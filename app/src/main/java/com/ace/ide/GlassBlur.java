package com.ace.ide;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Cheap frosted-glass backdrop: snapshot a view, shrink it, blur it. */
public final class GlassBlur {

    private GlassBlur() {}

    /** @return a blurred drawable of the left {@code cropWidth} px of {@code src}, or null */
    public static Drawable leftStrip(View src, int cropWidth) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return null;
            int scale = 8;
            Bitmap small = Bitmap.createBitmap(Math.max(1, w / scale), Math.max(1, h / scale), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            c.scale(1f / scale, 1f / scale);
            src.draw(c);
            blur(small, 3);
            blur(small, 3);
            int cw = Math.max(1, Math.min(small.getWidth(), cropWidth / scale));
            Bitmap strip = Bitmap.createBitmap(small, 0, 0, cw, small.getHeight());
            return new BitmapDrawable(src.getResources(), strip);
        } catch (Throwable t) {
            return null;
        }
    }

    /** simple separable box blur, in place */
    static void blur(Bitmap bmp, int radius) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        pass(px, tmp, w, h, radius, true);
        pass(tmp, px, w, h, radius, false);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int lines = horiz ? h : w;
        int len = horiz ? w : h;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, b = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = i + k;
                    if (j < 0 || j >= len) continue;
                    int p = horiz ? in[l * w + j] : in[j * w + l];
                    a += p >>> 24; rr += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
                }
                int v = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
                if (horiz) out[l * w + i] = v; else out[i * w + l] = v;
            }
        }
    }
}
