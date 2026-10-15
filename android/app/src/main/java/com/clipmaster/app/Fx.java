package com.clipmaster.app;

import android.graphics.Matrix;
import androidx.media3.common.Effect;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.MatrixTransformation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.RgbMatrix;
import java.util.ArrayList;
import java.util.List;

/** Фильтры и переходы КлипМастера на эффектах Media3. */
final class Fx {
    private Fx() {}

    static List<Effect> filter(String id) {
        List<Effect> l = new ArrayList<>();
        if (id == null) return l;
        switch (id) {
            case "vivid":
                l.add(new HslAdjustment.Builder().adjustSaturation(35f).build());
                l.add(new Contrast(0.12f));
                break;
            case "warm": l.add(rgb(1.10f, 1.02f, 0.88f, 0.02f)); break;
            case "cool": l.add(rgb(0.90f, 1.00f, 1.12f, 0f)); break;
            case "bw":
                l.add(RgbFilter.createGrayscaleFilter());
                l.add(new Contrast(0.15f));
                break;
            case "cinema":
                l.add(new HslAdjustment.Builder().adjustSaturation(-20f).build());
                l.add(new Contrast(0.22f));
                l.add(rgb(1.05f, 0.98f, 0.92f, 0f));
                break;
            case "vintage":
                l.add(new HslAdjustment.Builder().adjustSaturation(-35f).build());
                l.add(rgb(1.08f, 0.98f, 0.80f, 0.05f));
                break;
            case "faded":
                l.add(new HslAdjustment.Builder().adjustSaturation(-25f).build());
                l.add(new Contrast(-0.18f));
                l.add(rgb(0.95f, 0.95f, 0.95f, 0.06f));
                break;
            case "drama":
                l.add(new Contrast(0.35f));
                l.add(new HslAdjustment.Builder().adjustSaturation(-10f).build());
                break;
            case "sunset": l.add(rgb(1.15f, 0.95f, 0.80f, 0.03f)); l.add(new Contrast(0.1f)); break;
            case "sepia":
                l.add(RgbFilter.createGrayscaleFilter());
                l.add(rgb(1.07f, 0.95f, 0.78f, 0.03f));
                break;
            case "mint": l.add(rgb(0.92f, 1.06f, 1.0f, 0.01f)); l.add(new HslAdjustment.Builder().adjustSaturation(10f).build()); break;
            case "invert": l.add(RgbFilter.createInvertedFilter()); break;
            default: break;
        }
        return l;
    }

    private static RgbMatrix rgb(float r, float g, float b, float lift) {
        float[] m = {r, 0, 0, 0, 0, g, 0, 0, 0, 0, b, 0, lift, lift, lift, 1};
        return (t, hdr) -> m;
    }

    /** Время кадра относительно начала клипа (первый увиденный кадр = 0). */
    private static final class Clock {
        long first = Long.MIN_VALUE;
        long rel(long t) { if (first == Long.MIN_VALUE || t < first) first = t; return t - first; }
    }

    /** Затемнение в цвет (0 = чёрный, 1 = белый) на входе и/или выходе клипа. */
    static Effect fade(long durUs, long inUs, long outUs, float toWhite) {
        Clock c = new Clock();
        return (RgbMatrix) (t, hdr) -> {
            long r = c.rel(t);
            float a = 1f;
            if (inUs > 0 && r < inUs) a = Math.min(a, r / (float) inUs);
            if (outUs > 0 && r > durUs - outUs) a = Math.min(a, Math.max(0f, (durUs - r) / (float) outUs));
            a = Math.max(0f, Math.min(1f, a));
            float o = (1f - a) * toWhite;
            return new float[]{a, 0, 0, 0, 0, a, 0, 0, 0, 0, a, 0, o, o, o, 1};
        };
    }

    /** Въезд нового клипа сбоку (dir: 1 = справа, -1 = слева). */
    static Effect slideIn(long inUs, float dir) {
        Clock c = new Clock();
        return (MatrixTransformation) t -> {
            float p = Math.min(1f, c.rel(t) / (float) inUs);
            float e = 1f - (1f - p) * (1f - p) * (1f - p);
            Matrix m = new Matrix();
            m.postTranslate(dir * 2f * (1f - e), 0f);
            return m;
        };
    }

    /** Приближение на входе клипа. */
    static Effect zoomIn(long inUs) {
        Clock c = new Clock();
        return (MatrixTransformation) t -> {
            float p = Math.min(1f, c.rel(t) / (float) inUs);
            float s = 1.25f - 0.25f * (1f - (1f - p) * (1f - p));
            Matrix m = new Matrix();
            m.postScale(s, s);
            return m;
        };
    }
}
