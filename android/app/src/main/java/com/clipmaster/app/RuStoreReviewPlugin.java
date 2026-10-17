package com.clipmaster.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import ru.rustore.sdk.review.RuStoreReviewManager;
import ru.rustore.sdk.review.RuStoreReviewManagerFactory;
import ru.rustore.sdk.review.model.ReviewInfo;

/** Оценки и отзывы RuStore (ru.rustore.sdk:review) + запасной переход на страницу приложения. */
@CapacitorPlugin(name = "RuStoreReview")
public class RuStoreReviewPlugin extends Plugin {
    private RuStoreReviewManager manager;
    private ReviewInfo cachedInfo;
    private long cachedAt;

    private RuStoreReviewManager mgr() {
        if (manager == null) manager = RuStoreReviewManagerFactory.INSTANCE.create(getContext());
        return manager;
    }

    /** Подготовить ReviewInfo заранее (живёт ~5 минут). */
    @PluginMethod
    public void requestReviewFlow(PluginCall call) {
        try {
            mgr().requestReviewFlow()
                    .addOnSuccessListener(info -> { cachedInfo = info; cachedAt = System.currentTimeMillis(); call.resolve(new JSObject().put("ready", true)); })
                    .addOnFailureListener(t -> call.resolve(new JSObject().put("ready", false).put("error", String.valueOf(t.getMessage()))));
        } catch (Throwable t) {
            call.resolve(new JSObject().put("ready", false).put("error", String.valueOf(t.getMessage())));
        }
    }

    /** Показать экран оценки; при необходимости сначала запросить ReviewInfo. */
    @PluginMethod
    public void launchReviewFlow(PluginCall call) {
        try {
            if (cachedInfo != null && System.currentTimeMillis() - cachedAt < 4 * 60_000L) {
                launch(call, cachedInfo);
                return;
            }
            mgr().requestReviewFlow()
                    .addOnSuccessListener(info -> launch(call, info))
                    .addOnFailureListener(t -> call.resolve(new JSObject().put("shown", false).put("error", String.valueOf(t.getMessage()))));
        } catch (Throwable t) {
            call.resolve(new JSObject().put("shown", false).put("error", String.valueOf(t.getMessage())));
        }
    }

    private void launch(PluginCall call, ReviewInfo info) {
        cachedInfo = null; // ReviewInfo одноразовый
        mgr().launchReviewFlow(info)
                .addOnSuccessListener(u -> call.resolve(new JSObject().put("shown", true)))
                .addOnFailureListener(t -> call.resolve(new JSObject().put("shown", false).put("error", String.valueOf(t.getMessage()))));
    }

    /** Открыть страницу приложения в RuStore (приложение RuStore, иначе браузер). */
    @PluginMethod
    public void openStorePage(PluginCall call) {
        String pkg = getContext().getPackageName();
        if (open("rustore://apps.rustore.ru/app/" + pkg)) { call.resolve(new JSObject().put("via", "app")); return; }
        if (open("https://www.rustore.ru/catalog/app/" + pkg)) { call.resolve(new JSObject().put("via", "web")); return; }
        call.reject("Не удалось открыть RuStore");
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url", "");
        if (url.startsWith("https://") && open(url)) call.resolve(); else call.reject("Не удалось открыть ссылку");
    }

    private boolean open(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
