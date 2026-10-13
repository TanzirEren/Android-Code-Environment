package com.ace.ide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Clean line icons drawn in code (24 unit grid, round caps). No image assets needed. */
public final class Icons {

    public static final String MENU = "menu", PLAY = "play", UNDO = "undo", REDO = "redo", SAVE = "save",
            MORE = "more", FOLDER = "folder", FOLDER_OPEN = "folder_open", FILE = "file", JAVA = "java",
            XML = "xml", PLUS = "plus", IMPORT = "import", SEARCH = "search", SETTINGS = "settings",
            CONVERT = "convert", BUILD = "build", CHEV_R = "chev_r", CHEV_D = "chev_d", CLOSE = "close",
            CHECK = "check", TRASH = "trash", EDIT = "edit", COPY = "copy", SHARE = "share",
            DOWNLOAD = "download", KEY = "key", GIT = "git", APK = "apk", INFO = "info", BUG = "bug",
            LIB = "lib", REFRESH = "refresh", CODE = "code", LINK = "link", USER = "user", MOVE = "move",
            ARROW_L = "arrow_l", IMAGE = "image", PACKAGE = "package", TERMINAL = "terminal", INSTALL = "install",
            LOG = "log", SMALI = "smali", EXPORT = "export", NEWFILE = "newfile", NEWFOLDER = "newfolder";

    private Icons() {}

    public static void draw(Canvas c, String name, float size, Paint p) {
        float s = size / 24f;
        c.save();
        c.scale(s, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path q = new Path();
        RectF r = new RectF();
        if (name.equals(MENU)) {
            c.drawLine(4, 7, 20, 7, p); c.drawLine(4, 12, 15, 12, p); c.drawLine(4, 17, 20, 17, p);
        } else if (name.equals(PLAY)) {
            q.moveTo(7, 4.5f); q.lineTo(19, 12); q.lineTo(7, 19.5f); q.close();
            p.setStyle(Paint.Style.FILL_AND_STROKE); p.setStrokeWidth(1.6f); c.drawPath(q, p);
        } else if (name.equals(UNDO) || name.equals(REDO)) {
            boolean u = name.equals(UNDO);
            float m = u ? 1 : -1;
            float cx = u ? 0 : 24;
            q.moveTo(cx + m * 9, 7); q.lineTo(cx + m * 4, 12); q.lineTo(cx + m * 9, 17);
            c.drawPath(q, p);
            Path a = new Path();
            a.moveTo(cx + m * 4, 12); a.lineTo(cx + m * 14, 12);
            a.cubicTo(cx + m * 19, 12, cx + m * 21, 15, cx + m * 21, 18);
            c.drawPath(a, p);
        } else if (name.equals(SAVE)) {
            r.set(4, 4, 20, 20); c.drawRoundRect(r, 2.5f, 2.5f, p);
            r.set(8, 4, 16, 9); c.drawRect(r, p);
            r.set(7.5f, 13, 16.5f, 20); c.drawRect(r, p);
        } else if (name.equals(MORE)) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(12, 5, 1.8f, p); c.drawCircle(12, 12, 1.8f, p); c.drawCircle(12, 19, 1.8f, p);
        } else if (name.equals(FOLDER)) {
            q.moveTo(3, 7); q.lineTo(3, 18); q.quadTo(3, 19.5f, 4.5f, 19.5f); q.lineTo(19.5f, 19.5f);
            q.quadTo(21, 19.5f, 21, 18); q.lineTo(21, 9.5f); q.quadTo(21, 8, 19.5f, 8); q.lineTo(11, 8);
            q.lineTo(9, 5.5f); q.lineTo(4.5f, 5.5f); q.quadTo(3, 5.5f, 3, 7); c.drawPath(q, p);
        } else if (name.equals(FOLDER_OPEN)) {
            q.moveTo(3, 18); q.lineTo(3, 6.5f); q.quadTo(3, 5.5f, 4, 5.5f); q.lineTo(9, 5.5f); q.lineTo(11, 8);
            q.lineTo(18.5f, 8); q.quadTo(19.5f, 8, 19.5f, 9); q.lineTo(19.5f, 11);
            c.drawPath(q, p);
            Path a = new Path();
            a.moveTo(3, 18.5f); a.lineTo(6, 11); a.lineTo(21.5f, 11); a.lineTo(18.5f, 18.5f); a.close();
            c.drawPath(a, p);
        } else if (name.equals(FILE) || name.equals(JAVA) || name.equals(XML) || name.equals(NEWFILE)) {
            q.moveTo(6, 3.5f); q.lineTo(14, 3.5f); q.lineTo(19, 8.5f); q.lineTo(19, 19.5f);
            q.quadTo(19, 20.5f, 18, 20.5f); q.lineTo(6, 20.5f); q.quadTo(5, 20.5f, 5, 19.5f); q.lineTo(5, 4.5f);
            q.quadTo(5, 3.5f, 6, 3.5f); c.drawPath(q, p);
            c.drawLine(14, 3.5f, 14, 8.5f, p); c.drawLine(14, 8.5f, 19, 8.5f, p);
            if (name.equals(JAVA)) { c.drawLine(9, 13, 9, 16.5f, p); c.drawLine(9, 16.5f, 8, 17.5f, p); c.drawLine(12, 13, 15, 13, p); }
            else if (name.equals(XML)) { c.drawLine(10, 13, 8, 15, p); c.drawLine(8, 15, 10, 17, p); c.drawLine(14, 13, 16, 15, p); c.drawLine(16, 15, 14, 17, p); }
            else if (name.equals(NEWFILE)) { c.drawLine(12, 12, 12, 17, p); c.drawLine(9.5f, 14.5f, 14.5f, 14.5f, p); }
            else { c.drawLine(8.5f, 13, 15.5f, 13, p); c.drawLine(8.5f, 16.5f, 13, 16.5f, p); }
        } else if (name.equals(PLUS)) {
            c.drawLine(12, 5, 12, 19, p); c.drawLine(5, 12, 19, 12, p);
        } else if (name.equals(IMPORT) || name.equals(DOWNLOAD) || name.equals(INSTALL)) {
            c.drawLine(12, 4, 12, 15, p); c.drawLine(7.5f, 10.5f, 12, 15, p); c.drawLine(16.5f, 10.5f, 12, 15, p);
            c.drawLine(5, 19.5f, 19, 19.5f, p);
        } else if (name.equals(EXPORT) || name.equals(SHARE)) {
            c.drawLine(12, 15, 12, 4, p); c.drawLine(7.5f, 8.5f, 12, 4, p); c.drawLine(16.5f, 8.5f, 12, 4, p);
            c.drawLine(5, 19.5f, 19, 19.5f, p);
        } else if (name.equals(SEARCH)) {
            c.drawCircle(10.5f, 10.5f, 6, p); c.drawLine(15, 15, 20, 20, p);
        } else if (name.equals(SETTINGS)) {
            c.drawCircle(12, 12, 3, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float x1 = (float) (12 + Math.cos(a) * 6.6), y1 = (float) (12 + Math.sin(a) * 6.6);
                float x2 = (float) (12 + Math.cos(a) * 9), y2 = (float) (12 + Math.sin(a) * 9);
                c.drawLine(x1, y1, x2, y2, p);
            }
            c.drawCircle(12, 12, 6.6f, p);
        } else if (name.equals(CONVERT)) {
            c.drawLine(4, 8, 17, 8, p); c.drawLine(13.5f, 4.5f, 17, 8, p); c.drawLine(13.5f, 11.5f, 17, 8, p);
            c.drawLine(20, 16, 7, 16, p); c.drawLine(10.5f, 12.5f, 7, 16, p); c.drawLine(10.5f, 19.5f, 7, 16, p);
        } else if (name.equals(BUILD) || name.equals(PACKAGE) || name.equals(APK)) {
            q.moveTo(12, 3); q.lineTo(20, 7.5f); q.lineTo(20, 16.5f); q.lineTo(12, 21); q.lineTo(4, 16.5f);
            q.lineTo(4, 7.5f); q.close(); c.drawPath(q, p);
            c.drawLine(4, 7.5f, 12, 12, p); c.drawLine(20, 7.5f, 12, 12, p); c.drawLine(12, 12, 12, 21, p);
        } else if (name.equals(CHEV_R)) {
            c.drawLine(9, 5, 16, 12, p); c.drawLine(16, 12, 9, 19, p);
        } else if (name.equals(CHEV_D)) {
            c.drawLine(5, 9, 12, 16, p); c.drawLine(12, 16, 19, 9, p);
        } else if (name.equals(ARROW_L)) {
            c.drawLine(19, 12, 5, 12, p); c.drawLine(11, 6, 5, 12, p); c.drawLine(11, 18, 5, 12, p);
        } else if (name.equals(CLOSE)) {
            c.drawLine(6, 6, 18, 18, p); c.drawLine(18, 6, 6, 18, p);
        } else if (name.equals(CHECK)) {
            c.drawLine(5, 12.5f, 10, 17.5f, p); c.drawLine(10, 17.5f, 19, 7, p);
        } else if (name.equals(TRASH)) {
            c.drawLine(4, 7, 20, 7, p); c.drawLine(9, 7, 9, 4.5f, p); c.drawLine(9, 4.5f, 15, 4.5f, p); c.drawLine(15, 4.5f, 15, 7, p);
            q.moveTo(6, 7); q.lineTo(7, 19.5f); q.lineTo(17, 19.5f); q.lineTo(18, 7); c.drawPath(q, p);
            c.drawLine(10, 11, 10, 16, p); c.drawLine(14, 11, 14, 16, p);
        } else if (name.equals(EDIT)) {
            q.moveTo(4, 20); q.lineTo(4, 16); q.lineTo(15.5f, 4.5f); q.lineTo(19.5f, 8.5f); q.lineTo(8, 20); q.close();
            c.drawPath(q, p); c.drawLine(13, 7, 17, 11, p);
        } else if (name.equals(COPY)) {
            r.set(8.5f, 8.5f, 19.5f, 19.5f); c.drawRoundRect(r, 2, 2, p);
            q.moveTo(15.5f, 8.5f); q.lineTo(15.5f, 6); q.quadTo(15.5f, 4.5f, 14, 4.5f); q.lineTo(6, 4.5f);
            q.quadTo(4.5f, 4.5f, 4.5f, 6); q.lineTo(4.5f, 14); q.quadTo(4.5f, 15.5f, 6, 15.5f); q.lineTo(8.5f, 15.5f);
            c.drawPath(q, p);
        } else if (name.equals(KEY)) {
            c.drawCircle(8, 14, 4, p); c.drawLine(11, 11, 20, 4, p); c.drawLine(17, 6.5f, 19.5f, 9, p); c.drawLine(14.5f, 8.5f, 16.5f, 10.5f, p);
        } else if (name.equals(GIT)) {
            c.drawCircle(7, 6, 2.2f, p); c.drawCircle(7, 18, 2.2f, p); c.drawCircle(17, 9, 2.2f, p);
            c.drawLine(7, 8.2f, 7, 15.8f, p);
            q.moveTo(17, 11.2f); q.cubicTo(17, 14.5f, 7, 12.5f, 7, 15.8f); c.drawPath(q, p);
        } else if (name.equals(INFO)) {
            c.drawCircle(12, 12, 8.5f, p); c.drawLine(12, 11, 12, 16.5f, p);
            p.setStyle(Paint.Style.FILL); c.drawCircle(12, 7.8f, 1.2f, p);
        } else if (name.equals(BUG)) {
            r.set(8, 8, 16, 19); c.drawRoundRect(r, 4, 4, p);
            c.drawLine(8, 12.5f, 4.5f, 11, p); c.drawLine(16, 12.5f, 19.5f, 11, p);
            c.drawLine(8, 16, 4.5f, 17.5f, p); c.drawLine(16, 16, 19.5f, 17.5f, p);
            c.drawLine(10, 6, 9, 4, p); c.drawLine(14, 6, 15, 4, p); c.drawLine(12, 10, 12, 19, p);
        } else if (name.equals(LIB)) {
            c.drawLine(5, 4, 5, 20, p); c.drawLine(10, 4, 10, 20, p);
            q.moveTo(14, 5); q.lineTo(18, 4); q.lineTo(21, 19); q.lineTo(17, 20); q.close(); c.drawPath(q, p);
        } else if (name.equals(REFRESH)) {
            r.set(5, 5, 19, 19); c.drawArc(r, -60, 280, false, p);
            c.drawLine(17, 3.5f, 17.3f, 7.8f, p); c.drawLine(17.3f, 7.8f, 13, 8, p);
        } else if (name.equals(CODE) || name.equals(SMALI)) {
            c.drawLine(8.5f, 7, 3.5f, 12, p); c.drawLine(3.5f, 12, 8.5f, 17, p);
            c.drawLine(15.5f, 7, 20.5f, 12, p); c.drawLine(20.5f, 12, 15.5f, 17, p); c.drawLine(13.5f, 5, 10.5f, 19, p);
        } else if (name.equals(LINK)) {
            r.set(3, 9, 12, 15); c.drawRoundRect(r, 3, 3, p); r.set(12, 9, 21, 15); c.drawRoundRect(r, 3, 3, p);
            c.drawLine(9, 12, 15, 12, p);
        } else if (name.equals(USER)) {
            c.drawCircle(12, 8, 3.8f, p); r.set(5, 14, 19, 22); c.drawArc(r, 180, 180, false, p);
        } else if (name.equals(MOVE)) {
            c.drawLine(12, 3, 12, 21, p); c.drawLine(3, 12, 21, 12, p);
            c.drawLine(9.5f, 5.5f, 12, 3, p); c.drawLine(14.5f, 5.5f, 12, 3, p);
            c.drawLine(9.5f, 18.5f, 12, 21, p); c.drawLine(14.5f, 18.5f, 12, 21, p);
            c.drawLine(5.5f, 9.5f, 3, 12, p); c.drawLine(5.5f, 14.5f, 3, 12, p);
            c.drawLine(18.5f, 9.5f, 21, 12, p); c.drawLine(18.5f, 14.5f, 21, 12, p);
        } else if (name.equals(IMAGE)) {
            r.set(4, 5, 20, 19); c.drawRoundRect(r, 2.5f, 2.5f, p);
            c.drawCircle(9, 10, 1.6f, p); c.drawLine(4.5f, 17, 10, 12.5f, p); c.drawLine(10, 12.5f, 14, 16, p); c.drawLine(14, 16, 16, 14.5f, p); c.drawLine(16, 14.5f, 19.5f, 17.5f, p);
        } else if (name.equals(TERMINAL) || name.equals(LOG)) {
            r.set(3.5f, 5, 20.5f, 19); c.drawRoundRect(r, 2.5f, 2.5f, p);
            c.drawLine(7, 10, 10, 12.5f, p); c.drawLine(10, 12.5f, 7, 15, p); c.drawLine(12.5f, 15, 17, 15, p);
        } else if (name.equals(NEWFOLDER)) {
            q.moveTo(3, 7); q.lineTo(3, 18); q.quadTo(3, 19.5f, 4.5f, 19.5f); q.lineTo(19.5f, 19.5f);
            q.quadTo(21, 19.5f, 21, 18); q.lineTo(21, 9.5f); q.quadTo(21, 8, 19.5f, 8); q.lineTo(11, 8);
            q.lineTo(9, 5.5f); q.lineTo(4.5f, 5.5f); q.quadTo(3, 5.5f, 3, 7); c.drawPath(q, p);
            c.drawLine(12, 11.5f, 12, 16.5f, p); c.drawLine(9.5f, 14, 14.5f, 14, p);
        } else {
            c.drawCircle(12, 12, 8, p);
        }
        c.restore();
    }

    /** a small view that draws one icon */
    public static class IconView extends View {
        private String name;
        private int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IconView(Context c, String name, int color) {
            super(c);
            this.name = name;
            this.color = color;
        }

        public void set(String name, int color) { this.name = name; this.color = color; invalidate(); }
        public void setColor(int color) { this.color = color; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            if (size <= 0) return;
            paint.setColor(color);
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            Icons.draw(canvas, name, size, paint);
            canvas.restore();
        }
    }

    public static IconView view(Context c, String name, int color) { return new IconView(c, name, color); }

    /** icon name for a file in the explorer */
    public static String forFile(String fileName) {
        String n = fileName.toLowerCase();
        if (n.endsWith(".java")) return JAVA;
        if (n.endsWith(".xml") || n.endsWith(".html")) return XML;
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif")) return IMAGE;
        if (n.endsWith(".jar") || n.endsWith(".aar") || n.endsWith(".zip") || n.endsWith(".apk") || n.endsWith(".dex")) return PACKAGE;
        return FILE;
    }
}
