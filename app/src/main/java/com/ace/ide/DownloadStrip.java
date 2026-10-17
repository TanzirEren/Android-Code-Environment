package com.ace.ide;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/** Slim animated bar under the IDE top bar: bouncing arrow, shimmering progress, percent and file name. */
final class DownloadStrip extends View {

    private static final int IDLE = 0, WORK = 1, DONE = 2, ERROR = 3;

    private final Theme t = new Theme();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int state = IDLE;
    private float target, shown;
    private String title = "", detail = "";
    private float phase;
    private float pop;
    private ValueAnimator loop, popAnim;
    private final int fullH;
    private Runnable onRetry;

    DownloadStrip(Context c) {
        super(c);
        fullH = Ui.dp(c, 46);
        setVisibility(GONE);
        loop = ValueAnimator.ofFloat(0f, 1f);
        loop.setDuration(1100);
        loop.setRepeatCount(ValueAnimator.INFINITE);
        loop.setInterpolator(new LinearInterpolator());
        loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                shown += (target - shown) * 0.18f;      // smooth progress
                invalidate();
            }
        });
        setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (state == ERROR && onRetry != null) onRetry.run(); }
        });
    }

    void setOnRetry(Runnable r) { onRetry = r; }

    private void expand() {
        setVisibility(VISIBLE);
        ValueAnimator a = ValueAnimator.ofInt(getLayoutParams() == null ? 0 : Math.max(0, getLayoutParams().height), fullH);
        a.setDuration(220);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                ViewGroup.LayoutParams lp = getLayoutParams();
                lp.height = (Integer) v.getAnimatedValue();
                setLayoutParams(lp);
            }
        });
        a.start();
    }

    private void collapse(long delay) {
        postDelayed(new Runnable() {
            @Override public void run() {
                ValueAnimator a = ValueAnimator.ofInt(fullH, 0);
                a.setDuration(240);
                a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator v) {
                        ViewGroup.LayoutParams lp = getLayoutParams();
                        lp.height = (Integer) v.getAnimatedValue();
                        setLayoutParams(lp);
                        if (lp.height == 0) { setVisibility(GONE); loop.cancel(); state = IDLE; }
                    }
                });
                a.start();
            }
        }, delay);
    }

    void start(String msg) {
        state = WORK;
        title = msg;
        detail = "";
        target = 0f;
        shown = 0f;
        if (!loop.isStarted()) loop.start();
        if (getVisibility() != VISIBLE || getLayoutParams().height < fullH) expand();
    }

    void progress(float f, String d) {
        if (state != WORK) return;
        target = f;
        detail = d;
    }

    void finish(String msg, boolean error) {
        title = msg;
        detail = "";
        target = 1f;
        state = error ? ERROR : DONE;
        if (getVisibility() != VISIBLE || getLayoutParams().height < fullH) expand();
        if (!loop.isStarted()) loop.start();
        popAnim = ValueAnimator.ofFloat(0f, 1f);
        popAnim.setDuration(380);
        popAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { pop = (Float) a.getAnimatedValue(); }
        });
        popAnim.start();
        collapse(error ? 5200 : 1700);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        loop.cancel();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (h < 4) return;
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(t.card);
        c.drawRect(0, 0, w, h, p);
        float dp = Ui.dp(getContext(), 1);

        // icon
        float cx = 24 * dp, cy = fullH / 2f - 3 * dp;
        p.setColor(t.accentSoft);
        c.drawCircle(cx, cy, 14 * dp, p);
        int col = state == ERROR ? t.danger : t.accent;
        p.setColor(col);
        c.save();
        c.translate(cx - 9 * dp, cy - 9 * dp);
        Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        ip.setColor(col);
        if (state == WORK) {
            float bounce = (float) Math.sin(phase * Math.PI * 2) * 2.2f * dp;
            c.translate(0, bounce);
            Icons.draw(c, Icons.DOWNLOAD, 18 * dp, ip);
        } else if (state == DONE) {
            float s = 0.6f + 0.4f * pop;
            c.scale(s, s, 9 * dp, 9 * dp);
            Icons.draw(c, Icons.CHECK, 18 * dp, ip);
        } else {
            Icons.draw(c, Icons.CLOSE, 18 * dp, ip);
        }
        c.restore();

        // texts
        float tx = 48 * dp;
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setTextSize(12.5f * Ui.dp(getContext(), 1));
        tp.setColor(state == ERROR ? t.danger : t.text);
        String tt = fit(title, tp, w - tx - 60 * dp);
        c.drawText(tt, tx, cy - 1 * dp, tp);
        tp.setTypeface(Typeface.MONOSPACE);
        tp.setTextSize(10.5f * dp);
        tp.setColor(t.textSub);
        String dd = state == WORK ? fit(detail, tp, w - tx - 60 * dp) : (state == ERROR ? "tap to retry" : "");
        c.drawText(dd, tx, cy + 13 * dp, tp);
        if (state == WORK) {
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(12.5f * dp);
            tp.setColor(t.accent);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Math.round(Math.min(1f, shown) * 100) + "%", w - 16 * dp, cy + 4 * dp, tp);
        }

        // progress track with shimmer
        float th = 3.5f * dp, ty = h - th - 2 * dp;
        r.set(12 * dp, ty, w - 12 * dp, ty + th);
        p.setColor(0x30E9E9E9);
        c.drawRoundRect(r, th, th, p);
        float fillW = (r.width()) * Math.max(0.02f, Math.min(1f, shown));
        RectF fr = new RectF(r.left, r.top, r.left + fillW, r.bottom);
        p.setColor(state == ERROR ? t.danger : t.accent);
        c.drawRoundRect(fr, th, th, p);
        if (state == WORK && fillW > 20 * dp) {
            float bandW = 70 * dp;
            float x = fr.left - bandW + (fillW + bandW) * phase;
            p.setShader(new LinearGradient(x, 0, x + bandW, 0, new int[]{0x00FFFFFF, 0xAAFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.save();
            c.clipRect(fr);
            c.drawRoundRect(fr, th, th, p);
            c.restore();
            p.setShader(null);
        }
        p.setColor(t.cardStroke);
        c.drawRect(0, h - 1, w, h, p);
    }

    private static String fit(String s, Paint p, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        int n = s.length();
        while (n > 1 && p.measureText(s, 0, n) + p.measureText("…") > max) n--;
        return s.substring(0, n) + "…";
    }
}
