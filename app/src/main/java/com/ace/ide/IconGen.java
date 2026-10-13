package com.ace.ide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Builds launcher icons (all densities) from one image, with an adjustable corner radius. */
public final class IconGen {

    private IconGen() {}

    private static final String[] DIRS = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
    private static final int[] SIZES = {48, 72, 96, 144, 192};

    /** default icon = the ACE logo */
    public static Bitmap defaultIcon(Context c) {
        try {
            return BitmapFactory.decodeStream(c.getAssets().open("default_icon.png"));
        } catch (IOException e) {
            Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            b.eraseColor(0xFFD9D0CB);
            return b;
        }
    }

    public static Bitmap render(Bitmap src, int size, int radiusPct) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = size * Math.max(0, Math.min(50, radiusPct)) / 100f;
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, size, size), r, r, Path.Direction.CW);
        cv.clipPath(clip);
        int sw = src.getWidth(), sh = src.getHeight();
        int side = Math.min(sw, sh);
        Rect from = new Rect((sw - side) / 2, (sh - side) / 2, (sw - side) / 2 + side, (sh - side) / 2 + side);
        cv.drawBitmap(src, from, new Rect(0, 0, size, size), p);
        return out;
    }

    public static void writeAll(Bitmap src, int radiusPct, File resDir) throws IOException {
        for (int i = 0; i < DIRS.length; i++) {
            File d = new File(resDir, DIRS[i]);
            d.mkdirs();
            save(render(src, SIZES[i], radiusPct), new File(d, "ic_launcher.png"));
            save(render(src, SIZES[i], 50), new File(d, "ic_launcher_round.png"));
        }
    }

    private static void save(Bitmap b, File f) throws IOException {
        FileOutputStream fo = new FileOutputStream(f);
        try { b.compress(Bitmap.CompressFormat.PNG, 100, fo); } finally { fo.close(); }
    }

    public static Bitmap load(File f) {
        try { return BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable t) { return null; }
    }
}
