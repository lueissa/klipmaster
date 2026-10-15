package com.clipmaster.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.activity.result.ActivityResult;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Нативный движок КлипМастера на базе Media3 Transformer. */
@CapacitorPlugin(name = "VideoEngine")
public class VideoEnginePlugin extends Plugin {
    private Transformer transformer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int counter = 0;

    @PluginMethod
    public void pickVideos(PluginCall call) { pick(call, "video/*", "onVideosPicked"); }

    @PluginMethod
    public void pickAudio(PluginCall call) { pick(call, "audio/*", "onAudioPicked"); }

    private void pick(PluginCall call, String mime, String cb) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mime.startsWith("video"));
        startActivityForResult(call, i, cb);
    }

    @ActivityCallback
    private void onVideosPicked(PluginCall call, ActivityResult r) { handlePicked(call, r, true); }

    @ActivityCallback
    private void onAudioPicked(PluginCall call, ActivityResult r) { handlePicked(call, r, false); }

    private void handlePicked(PluginCall call, ActivityResult r, boolean video) {
        if (call == null) return;
        if (r.getResultCode() != Activity.RESULT_OK || r.getData() == null) {
            call.resolve(new JSObject().put("items", new JSArray()));
            return;
        }
        Intent d = r.getData();
        List<Uri> uris = new ArrayList<>();
        if (d.getClipData() != null) {
            for (int k = 0; k < d.getClipData().getItemCount(); k++) uris.add(d.getClipData().getItemAt(k).getUri());
        } else if (d.getData() != null) uris.add(d.getData());
        new Thread(() -> {
            JSArray arr = new JSArray();
            try {
                File dir = new File(getContext().getFilesDir(), "import");
                dir.mkdirs();
                for (Uri u : uris) {
                    String name = displayName(u);
                    File f = new File(dir, System.currentTimeMillis() + "_" + (counter++) + (video ? ".mp4" : ".m4a"));
                    try (InputStream in = getContext().getContentResolver().openInputStream(u);
                         OutputStream out = Files.newOutputStream(f.toPath())) {
                        byte[] buf = new byte[1 << 16]; int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    JSObject o = new JSObject();
                    o.put("path", f.getAbsolutePath());
                    o.put("name", name);
                    MediaMetadataRetriever mr = new MediaMetadataRetriever();
                    try {
                        mr.setDataSource(f.getAbsolutePath());
                        o.put("durationMs", parse(mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
                        if (video) {
                            long w = parse(mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                            long h = parse(mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                            long rot = parse(mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                            if (rot == 90 || rot == 270) { long t = w; w = h; h = t; }
                            o.put("width", w); o.put("height", h);
                            o.put("hasAudio", "yes".equals(mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)));
                        }
                    } finally { mr.release(); }
                    arr.put(o);
                }
                call.resolve(new JSObject().put("items", arr));
            } catch (Exception e) {
                call.reject("Не удалось импортировать файл: " + e.getMessage());
            }
        }).start();
    }

    private long parse(String s) { try { return s == null ? 0 : Long.parseLong(s); } catch (Exception e) { return 0; } }

    private String displayName(Uri u) {
        try (Cursor c = getContext().getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "clip";
    }

    @PluginMethod
    public void cancelExport(PluginCall call) {
        main.post(() -> { if (transformer != null) transformer.cancel(); transformer = null; call.resolve(); });
    }

    @PluginMethod
    public void export(PluginCall call) {
        try {
            JSONArray clips = call.getArray("clips");
            if (clips == null || clips.length() == 0) { call.reject("Нет клипов"); return; }
            final int n = clips.length();
            boolean portrait = call.getBoolean("portrait", false);
            final int outW = portrait ? 720 : 1280, outH = portrait ? 1280 : 720;
            String text = call.getString("text", "");
            String textPos = call.getString("textPosition", "bottom");
            int textSize = call.getInt("textSize", 56);
            String textStyle = call.getString("textStyle", "box");
            String textColor = call.getString("textColor", "#FFFFFF");
            String musicPath = call.getString("musicPath", null);
            boolean muteOriginal = call.getBoolean("muteOriginal", false);
            double musicVol = call.getDouble("musicVolume", 1.0);
            double origVol = call.getDouble("originalVolume", 1.0);
            long musicStartMs = call.getLong("musicStartMs", 0L);

            boolean anyNoAudio = false;
            for (int i = 0; i < n; i++) if (!clips.getJSONObject(i).optBoolean("hasAudio", true)) anyNoAudio = true;
            // Media3 требует одинаковый набор дорожек у клипов в последовательности.
            final boolean removeAudio = muteOriginal || anyNoAudio || origVol <= 0.001;

            // --- раскладка по времени ---
            final long[] eff = new long[n], start = new long[n], ov = new long[n];
            final String[] tin = new String[n];
            boolean overlapping = false;
            for (int i = 0; i < n; i++) {
                JSONObject c = clips.getJSONObject(i);
                double sp = Math.max(0.25, Math.min(4.0, c.optDouble("speed", 1.0)));
                eff[i] = (long) (Math.max(1, c.optLong("endMs", 0) - c.optLong("startMs", 0)) * 1000L / sp);
                tin[i] = i > 0 ? c.optString("transitionIn", "none") : "none";
            }
            for (int i = 0; i < n; i++) {
                if (i > 0 && isOverlap(tin[i])) {
                    ov[i] = Math.min(800_000L, Math.min(eff[i - 1], eff[i]) / 2);
                    overlapping = true;
                }
                start[i] = i == 0 ? 0 : start[i - 1] + eff[i - 1] - ov[i];
            }

            List<EditedMediaItemSequence.Builder> seqs = new ArrayList<>();
            seqs.add(new EditedMediaItemSequence.Builder());
            if (overlapping) seqs.add(new EditedMediaItemSequence.Builder());
            long[] seqEnd = new long[2];
            for (int i = 0; i < n; i++) {
                JSONObject c = clips.getJSONObject(i);
                double sp = Math.max(0.25, Math.min(4.0, c.optDouble("speed", 1.0)));
                MediaItem mi = new MediaItem.Builder()
                        .setUri(Uri.fromFile(new File(c.getString("path"))))
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(c.optLong("startMs", 0))
                                .setEndPositionMs(c.optLong("endMs", C_END))
                                .build())
                        .build();
                List<Effect> video = new ArrayList<>();
                List<androidx.media3.common.audio.AudioProcessor> audio = new ArrayList<>();
                if (Math.abs(sp - 1.0) > 0.01) {
                    if (removeAudio) video.add(new androidx.media3.effect.SpeedChangeEffect((float) sp));
                    else {
                        android.util.Pair<androidx.media3.common.audio.AudioProcessor, Effect> pr =
                                Effects.createExperimentalSpeedChangingEffect(constSpeed((float) sp));
                        audio.add(pr.first); video.add(pr.second);
                    }
                }
                if (!removeAudio && Math.abs(origVol - 1.0) > 0.01) audio.add(gain((float) origVol));
                long durUs = eff[i];
                video.addAll(Fx.filter(c.optString("filter", "none")));
                String inT = tin[i];
                String outT = i < n - 1 ? tin[i + 1] : "none";
                long half = Math.min(400_000L, durUs / 3);
                if (inT.equals("slide") || inT.equals("slideL")) video.add(Fx.slideIn(half * 2, inT.equals("slide") ? 1f : -1f));
                if (inT.equals("zoom")) video.add(Fx.zoomIn(half * 2));
                video.add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT));
                long fin = (inT.equals("black") || inT.equals("white")) ? half : 0;
                long fout = (outT.equals("black") || outT.equals("white")) ? half : 0;
                float wIn = inT.equals("white") ? 1f : 0f, wOut = outT.equals("white") ? 1f : 0f;
                if (fin > 0 || fout > 0) {
                    if (fin > 0 && fout > 0 && wIn != wOut) {
                        video.add(Fx.fade(durUs, fin, 0, wIn));
                        video.add(Fx.fade(durUs, 0, fout, wOut));
                    } else video.add(Fx.fade(durUs, fin, fout, fin > 0 ? wIn : wOut));
                }
                if (text != null && !text.trim().isEmpty() && !overlapping) video.add(textEffect(text.trim(), textPos, textSize, textStyle, textColor));
                EditedMediaItem item = new EditedMediaItem.Builder(mi)
                        .setRemoveAudio(removeAudio)
                        .setEffects(new Effects(ImmutableList.copyOf(audio), ImmutableList.copyOf(video)))
                        .build();
                int si = overlapping ? i % 2 : 0;
                if (overlapping && start[i] > seqEnd[si]) seqs.get(si).addGap(start[i] - seqEnd[si]);
                seqs.get(si).addItem(item);
                seqEnd[si] = start[i] + eff[i];
            }
            final long total = start[n - 1] + eff[n - 1];
            if (overlapping && seqEnd[0] < total) seqs.get(0).addGap(total - seqEnd[0]);

            List<EditedMediaItemSequence> all = new ArrayList<>();
            for (int k = 0; k < seqs.size(); k++) {
                EditedMediaItemSequence.Builder b = seqs.get(k);
                if (overlapping) {
                    b.experimentalSetForceVideoTrack(true);
                    if (!removeAudio) b.experimentalSetForceAudioTrack(true);
                }
                all.add(b.build());
            }
            if (musicPath != null && !musicPath.isEmpty()) {
                MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.fromFile(new File(musicPath)));
                if (musicStartMs > 0) mb.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(musicStartMs).build());
                List<androidx.media3.common.audio.AudioProcessor> ap = new ArrayList<>();
                if (Math.abs(musicVol - 1.0) > 0.01) ap.add(gain((float) musicVol));
                EditedMediaItem music = new EditedMediaItem.Builder(mb.build())
                        .setEffects(new Effects(ImmutableList.copyOf(ap), ImmutableList.of())).build();
                all.add(new EditedMediaItemSequence.Builder(ImmutableList.of(music)).setIsLooping(true).build());
            }
            Composition.Builder cb = new Composition.Builder(all);
            if (overlapping) {
                List<Effect> post = new ArrayList<>();
                post.add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT));
                if (text != null && !text.trim().isEmpty()) post.add(textEffect(text.trim(), textPos, textSize, textStyle, textColor));
                cb.setEffects(new Effects(ImmutableList.of(), ImmutableList.copyOf(post)));
                cb.setVideoCompositorSettings(new androidx.media3.common.VideoCompositorSettings() {
                    @Override public androidx.media3.common.util.Size getOutputSize(List<androidx.media3.common.util.Size> sizes) {
                        return new androidx.media3.common.util.Size(outW, outH);
                    }
                    @Override public androidx.media3.common.OverlaySettings getOverlaySettings(int inputId, long t) {
                        if (inputId != 0) return new StaticOverlaySettings.Builder().build();
                        return primaryOverlay(t, n, start, eff, ov, tin);
                    }
                });
            }
            Composition comp = cb.build();

            File outDir = new File(getContext().getCacheDir(), "export");
            outDir.mkdirs();
            File out = new File(outDir, "KlipMaster_" + System.currentTimeMillis() + ".mp4");

            main.post(() -> {
                transformer = new Transformer.Builder(getContext())
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(new Transformer.Listener() {
                            @Override public void onCompleted(Composition c, ExportResult r) {
                                transformer = null;
                                new Thread(() -> {
                                    try {
                                        JSObject res = saveToGallery(out);
                                        res.put("durationMs", r.durationMs);
                                        res.put("sizeBytes", out.length());
                                        call.resolve(res);
                                    } catch (Exception e) { call.reject("Видео собрано, но не сохранено: " + e.getMessage()); }
                                }).start();
                            }
                            @Override public void onError(Composition c, ExportResult r, ExportException e) {
                                transformer = null;
                                call.reject("Ошибка экспорта: " + e.getMessage() + " (" + e.getErrorCodeName() + ")");
                            }
                        })
                        .build();
                transformer.start(comp, out.getAbsolutePath());
                pollProgress();
            });
        } catch (Exception e) {
            call.reject("Ошибка подготовки экспорта: " + e.getMessage());
        }
    }

    private static boolean isOverlap(String t) { return "crossfade".equals(t) || "wipe".equals(t) || "wipeL".equals(t); }

    /** Верхний слой — клипы с чётным индексом; нижний — нечётные. */
    private static androidx.media3.common.OverlaySettings primaryOverlay(long t, int n, long[] start, long[] eff, long[] ov, String[] tin) {
        float alpha = 0f, x = 0f;
        for (int j = 0; j < n; j += 2) {
            if (t < start[j] || t >= start[j] + eff[j]) continue;
            alpha = 1f;
            String type = null; float p = 1f; boolean incoming = false;
            if (j > 0 && ov[j] > 0 && t < start[j] + ov[j]) { type = tin[j]; p = (t - start[j]) / (float) ov[j]; incoming = true; }
            else if (j < n - 1 && ov[j + 1] > 0 && t >= start[j + 1]) { type = tin[j + 1]; p = (t - start[j + 1]) / (float) ov[j + 1]; }
            p = Math.max(0f, Math.min(1f, p));
            if ("crossfade".equals(type)) alpha = incoming ? p : 1f - p;
            else if (type != null) {
                float e = 1f - (1f - p) * (1f - p);
                float dir = "wipeL".equals(type) ? -1f : 1f;
                x = incoming ? dir * 2f * (1f - e) : -dir * 2f * e;
            }
            break;
        }
        return new StaticOverlaySettings.Builder().setAlphaScale(alpha).setBackgroundFrameAnchor(x, 0f).build();
    }

    private static androidx.media3.common.audio.SpeedProvider constSpeed(float sp) {
        return new androidx.media3.common.audio.SpeedProvider() {
            @Override public float getSpeed(long timeUs) { return sp; }
            @Override public long getNextSpeedChangeTimeUs(long timeUs) { return androidx.media3.common.C.TIME_UNSET; }
        };
    }

    private static androidx.media3.common.audio.AudioProcessor gain(float g) {
        return new androidx.media3.common.audio.GainProcessor(
                new androidx.media3.common.audio.DefaultGainProvider.Builder(g).build());
    }

    private static final long C_END = Long.MIN_VALUE; // C.TIME_END_OF_SOURCE

    private void pollProgress() {
        if (transformer == null) return;
        ProgressHolder ph = new ProgressHolder();
        int state = transformer.getProgress(ph);
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
            notifyListeners("progress", new JSObject().put("percent", ph.progress));
        }
        main.postDelayed(this::pollProgress, 300);
    }

    private Effect textEffect(String text, String pos, int size, String style, String color) {
        boolean box = "box".equals(style);
        SpannableString s = new SpannableString(box ? " " + text + " " : text);
        int n = s.length();
        int col;
        try { col = Color.parseColor(color); } catch (Exception e) { col = Color.WHITE; }
        s.setSpan(new ForegroundColorSpan(col), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (box) s.setSpan(new BackgroundColorSpan(0x99000000), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        else if ("pill".equals(style)) s.setSpan(new BackgroundColorSpan(0xE66C5CFF), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new AbsoluteSizeSpan(Math.max(20, Math.min(140, size))), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!"light".equals(style)) s.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        float y = "top".equals(pos) ? 0.8f : ("center".equals(pos) ? 0f : -0.8f);
        StaticOverlaySettings st = new StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build();
        TextureOverlay ov = TextOverlay.createStaticTextOverlay(s, st);
        return new OverlayEffect(ImmutableList.of(ov));
    }

    private JSObject saveToGallery(File f) throws Exception {
        JSObject res = new JSObject();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, f.getName());
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KlipMaster");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = getContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            try (OutputStream os = getContext().getContentResolver().openOutputStream(uri);
                 InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[1 << 16]; int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            v.clear();
            v.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContext().getContentResolver().update(uri, v, null, null);
            res.put("location", "Галерея → Movies/KlipMaster/" + f.getName());
            res.put("uri", uri.toString());
        } else {
            File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            File dst = new File(dir, f.getName());
            Files.copy(f.toPath(), dst.toPath());
            res.put("location", dst.getAbsolutePath());
        }
        res.put("cachePath", f.getAbsolutePath());
        return res;
    }
}
