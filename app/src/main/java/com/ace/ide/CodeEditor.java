package com.ace.ide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Code editor: line numbers, red error lines, undo/redo, auto indent, pinch zoom. */
public class CodeEditor extends EditText {

    public interface Listener {
        void onChanged();
        void onZoom(float sp);
        void onCursorWord(String prefix);
    }

    /** edit history of one file */
    public static final class UndoStack {
        static final class Edit {
            int start; String oldText, newText; long time;
        }
        final List<Edit> undo = new ArrayList<Edit>();
        final List<Edit> redo = new ArrayList<Edit>();
    }

    private Theme t;
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Paint errPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gutter;
    private boolean lineNumbers = true;
    private boolean zoomEnabled = true;
    private boolean autoIndent = true, autoClose = true, highlightLine = true;
    private final Set<Integer> errorLines = new HashSet<Integer>();
    private Listener listener;
    private UndoStack undo = new UndoStack();
    private boolean applying;
    private boolean pendingBack;
    private String beforeText = "";
    private int beforeStart;
    private ScaleGestureDetector scale;
    private float sizeSp = 12f;
    private int digits = 2;

    public CodeEditor(Context c) {
        super(c);
        t = Theme.get(c);
        setTypeface(Typeface.MONOSPACE);
        setBackgroundColor(t.edBg);
        setGravity(Gravity.TOP | Gravity.START);
        setHorizontallyScrolling(true);
        setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setHighlightColor(t.edSel);
        setTextColor(t.edText);
        setCursorVisible(true);
        setIncludeFontPadding(false);
        setLineSpacing(0, 1.12f);
        numPaint.setTypeface(Typeface.MONOSPACE);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        errPaint.setStyle(Paint.Style.STROKE);
        errPaint.setStrokeWidth(Ui.dp(c, 1.4f));
        setTextSizeSp(12f);

        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                setTextSizeSp(sizeSp * d.getScaleFactor());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (listener != null) listener.onZoom(sizeSp);
            }
        });

        setFilters(new InputFilter[]{new InputFilter() {
            @Override public CharSequence filter(CharSequence src, int s, int e, Spanned dest, int ds, int de) {
                if (applying || e - s != 1) return null;
                char ch = src.charAt(s);
                if (autoIndent && ch == '\n') {
                    int ls = ds;
                    while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
                    StringBuilder ind = new StringBuilder("\n");
                    int k = ls;
                    while (k < ds && (dest.charAt(k) == ' ' || dest.charAt(k) == '\t')) ind.append(dest.charAt(k++));
                    int p = ds - 1;
                    while (p >= ls && dest.charAt(p) == ' ') p--;
                    if (p >= ls && (dest.charAt(p) == '{' || dest.charAt(p) == '(' || dest.charAt(p) == '[')) ind.append("    ");
                    return ind.toString();
                }
                if (autoClose && de == ds) {
                    char next = ds < dest.length() ? dest.charAt(ds) : 0;
                    if ((ch == ')' || ch == ']' || ch == '}' || ch == '"' || ch == '\'') && next == ch) {
                        post(new Runnable() { @Override public void run() { try { setSelection(getSelectionStart() + 1); } catch (Throwable ignored) { } } });
                        return "";
                    }
                    String close = ch == '(' ? ")" : ch == '[' ? "]" : ch == '{' ? "}" : null;
                    if (close == null && (ch == '"' || ch == '\'')) {
                        char prev = ds > 0 ? dest.charAt(ds - 1) : 0;
                        if (!Character.isLetterOrDigit(prev) && (next == 0 || !Character.isLetterOrDigit(next))) close = String.valueOf(ch);
                    }
                    if (close != null && (next == 0 || Character.isWhitespace(next) || next == ')' || next == ']' || next == '}' || next == ';' || next == ',')) {
                        pendingBack = true;
                        return String.valueOf(ch) + close;
                    }
                }
                return null;
            }
        }});

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (applying) return;
                beforeStart = start;
                beforeText = s.subSequence(start, start + count).toString();
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (applying) return;
                UndoStack.Edit ed = new UndoStack.Edit();
                ed.start = start;
                ed.oldText = beforeText;
                ed.newText = s.subSequence(start, start + count).toString();
                ed.time = System.currentTimeMillis();
                pushEdit(ed);
            }
            @Override public void afterTextChanged(Editable s) {
                if (pendingBack) {
                    pendingBack = false;
                    int sel = getSelectionStart();
                    if (sel > 0) setSelection(sel - 1);
                }
                updateDigits();
                if (listener != null && !applying) {
                    listener.onChanged();
                }
                reportWord();
            }
        });
    }

    private void reportWord() {
        if (listener == null) return;
        Editable e = getText();
        int sel = getSelectionStart();
        if (sel < 0 || sel > e.length()) { listener.onCursorWord(""); return; }
        int s = sel;
        while (s > 0) {
            char c = e.charAt(s - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.') s--; else break;
        }
        listener.onCursorWord(e.subSequence(s, sel).toString());
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (!applying) reportWord();
    }

    // ------------------------------------------------------------ undo / redo

    private void pushEdit(UndoStack.Edit ed) {
        UndoStack.Edit last = undo.undo.isEmpty() ? null : undo.undo.get(undo.undo.size() - 1);
        undo.redo.clear();
        if (last != null && ed.time - last.time < 900) {
            boolean typing = last.oldText.length() == 0 && ed.oldText.length() == 0 && ed.start == last.start + last.newText.length()
                    && ed.newText.length() == 1 && ed.newText.charAt(0) != '\n' && last.newText.indexOf('\n') < 0;
            boolean backspace = last.newText.length() == 0 && ed.newText.length() == 0 && ed.oldText.length() == 1
                    && ed.start + 1 == last.start;
            if (typing) { last.newText += ed.newText; last.time = ed.time; return; }
            if (backspace) { last.start = ed.start; last.oldText = ed.oldText + last.oldText; last.time = ed.time; return; }
        }
        undo.undo.add(ed);
        if (undo.undo.size() > 400) undo.undo.remove(0);
    }

    public boolean canUndo() { return !undo.undo.isEmpty(); }
    public boolean canRedo() { return !undo.redo.isEmpty(); }

    public void undo() {
        if (undo.undo.isEmpty()) return;
        UndoStack.Edit ed = undo.undo.remove(undo.undo.size() - 1);
        applying = true;
        try {
            getText().replace(ed.start, ed.start + ed.newText.length(), ed.oldText);
            setSelection(Math.min(getText().length(), ed.start + ed.oldText.length()));
        } finally { applying = false; }
        undo.redo.add(ed);
        if (listener != null) listener.onChanged();
    }

    public void redo() {
        if (undo.redo.isEmpty()) return;
        UndoStack.Edit ed = undo.redo.remove(undo.redo.size() - 1);
        applying = true;
        try {
            getText().replace(ed.start, ed.start + ed.oldText.length(), ed.newText);
            setSelection(Math.min(getText().length(), ed.start + ed.newText.length()));
        } finally { applying = false; }
        undo.undo.add(ed);
        if (listener != null) listener.onChanged();
    }

    /** load text without recording history */
    public void load(String text, UndoStack stack) {
        applying = true;
        try {
            undo = stack == null ? new UndoStack() : stack;
            setText(text);
        } finally { applying = false; }
        updateDigits();
    }

    public UndoStack undoStack() { return undo; }

    /** programmatic replace that should be undoable (find & replace, format …) */
    public void replaceRecorded(int start, int end, CharSequence with) {
        Editable e = getText();
        String old = e.subSequence(start, end).toString();
        applying = true;
        try { e.replace(start, end, with); } finally { applying = false; }
        UndoStack.Edit ed = new UndoStack.Edit();
        ed.start = start; ed.oldText = old; ed.newText = with.toString(); ed.time = 0;
        undo.redo.clear();
        undo.undo.add(ed);
        if (listener != null) listener.onChanged();
    }

    // ------------------------------------------------------------ settings

    public void setListener(Listener l) { listener = l; }
    public void setTheme(Theme th) {
        t = th;
        setBackgroundColor(t.edBg);
        setTextColor(t.edText);
        setHighlightColor(t.edSel);
        invalidate();
    }
    public void setOptions(boolean lineNumbers, boolean wrap, boolean autoIndent, boolean autoClose, boolean highlightLine, boolean zoom) {
        this.lineNumbers = lineNumbers;
        this.autoIndent = autoIndent;
        this.autoClose = autoClose;
        this.highlightLine = highlightLine;
        this.zoomEnabled = zoom;
        setHorizontallyScrolling(!wrap);
        updateDigits();
    }

    public void setTextSizeSp(float sp) {
        sizeSp = Math.max(8f, Math.min(32f, sp));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        numPaint.setTextSize(getTextSize() * 0.82f);
        updateDigits();
    }

    public float sizeSp() { return sizeSp; }

    public void setErrorLines(Set<Integer> lines) {
        errorLines.clear();
        if (lines != null) errorLines.addAll(lines);
        invalidate();
    }

    public void clearErrors() { errorLines.clear(); invalidate(); }

    public boolean hasErrors() { return !errorLines.isEmpty(); }

    private void updateDigits() {
        int lines = getLineCount();
        int d = Math.max(2, String.valueOf(Math.max(1, lines)).length());
        digits = d;
        int g = lineNumbers ? (int) (numPaint.measureText("0") * d) + Ui.dp(getContext(), 14) : 0;
        if (g != gutter) {
            gutter = g;
            setPadding(gutter + Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), Ui.dp(getContext(), 24), Ui.dp(getContext(), 80));
        }
    }

    public void goToLine(int line) {
        Layout l = getLayout();
        Editable e = getText();
        int target = Math.max(1, line);
        int idx = 0, cur = 1;
        while (cur < target && idx < e.length()) {
            int nl = e.toString().indexOf('\n', idx);
            if (nl < 0) { idx = e.length(); break; }
            idx = nl + 1;
            cur++;
        }
        setSelection(Math.min(idx, e.length()));
        requestFocus();
        if (l != null) {
            int y = l.getLineTop(l.getLineForOffset(Math.min(idx, e.length())));
            scrollTo(getScrollX(), Math.max(0, y - getHeight() / 3));
        }
    }

    public int currentLine() {
        Layout l = getLayout();
        int sel = getSelectionStart();
        if (sel < 0) return 1;
        int line = 1;
        CharSequence s = getText();
        for (int i = 0; i < sel && i < s.length(); i++) if (s.charAt(i) == '\n') line++;
        return line;
    }

    // ------------------------------------------------------------ touch: pinch zoom

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (zoomEnabled) {
            scale.onTouchEvent(ev);
            if (ev.getPointerCount() > 1 || scale.isInProgress()) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        Layout l = getLayout();
        int sx = getScrollX(), sy = getScrollY();
        if (l != null) {
            int top = l.getLineForVertical(sy);
            int bottom = l.getLineForVertical(sy + getHeight());
            int padTop = getPaddingTop();
            // current line + error lines (behind the text)
            if (highlightLine && getSelectionStart() >= 0 && getSelectionStart() == getSelectionEnd()) {
                int cl = l.getLineForOffset(getSelectionStart());
                bgPaint.setColor(t.edLine);
                c.drawRect(sx, l.getLineTop(cl) + padTop, sx + getWidth(), l.getLineBottom(cl) + padTop, bgPaint);
            }
            if (!errorLines.isEmpty()) {
                CharSequence txt = getText();
                int logical = logicalAt(l, top);
                for (int i = top; i <= bottom; i++) {
                    int start = l.getLineStart(i);
                    boolean first = start == 0 || txt.charAt(start - 1) == '\n';
                    if (first && i > top) logical++;
                    if (first && errorLines.contains(logical)) {
                        bgPaint.setColor((t.edErr & 0x00FFFFFF) | 0x22000000);
                        c.drawRect(sx, l.getLineTop(i) + padTop, sx + getWidth(), l.getLineBottom(i) + padTop, bgPaint);
                        errPaint.setColor(t.edErr);
                        float y = l.getLineBottom(i) + padTop - 2;
                        float x0 = gutter + Ui.dp(getContext(), 6) + l.getLineLeft(i);
                        float x1 = gutter + Ui.dp(getContext(), 6) + l.getLineRight(i);
                        float step = Ui.dp(getContext(), 3);
                        android.graphics.Path p = new android.graphics.Path();
                        p.moveTo(x0, y);
                        boolean up = true;
                        for (float x = x0; x < Math.max(x1, x0 + 12); x += step) {
                            p.lineTo(x + step, up ? y - step * 0.7f : y);
                            up = !up;
                        }
                        c.drawPath(p, errPaint);
                    }
                }
            }
        }
        super.onDraw(c);
        if (lineNumbers && l != null) {
            c.save();
            c.translate(sx, sy);
            bgPaint.setColor(t.edGutter);
            c.drawRect(0, 0, gutter, getHeight(), bgPaint);
            bgPaint.setColor(t.cardStroke);
            c.drawRect(gutter - 1, 0, gutter, getHeight(), bgPaint);
            c.restore();
            int top = l.getLineForVertical(sy);
            int bottom = l.getLineForVertical(sy + getHeight());
            CharSequence txt = getText();
            int logical = logicalAt(l, top);
            boolean topIsStart = l.getLineStart(top) == 0 || txt.charAt(l.getLineStart(top) - 1) == '\n';
            boolean pendingInc = !topIsStart;   // top is a wrapped continuation of `logical`
            Paint.FontMetrics fm = numPaint.getFontMetrics();
            int curLine = highlightLine ? l.getLineForOffset(Math.max(0, getSelectionStart())) : -1;
            for (int i = top; i <= bottom; i++) {
                int start = l.getLineStart(i);
                boolean first = start == 0 || txt.charAt(start - 1) == '\n';
                if (first) {
                    if (pendingInc) { logical++; pendingInc = false; }
                    boolean err = errorLines.contains(logical);
                    numPaint.setColor(err ? t.edErr : (i == curLine ? t.text : t.edGutterText));
                    numPaint.setFakeBoldText(err || i == curLine);
                    float base = l.getLineBaseline(i) + getPaddingTop();
                    c.drawText(String.valueOf(logical), sx + gutter - Ui.dp(getContext(), 7), base, numPaint);
                    if (err) {
                        errPaint.setStyle(Paint.Style.FILL);
                        errPaint.setColor(t.edErr);
                        c.drawCircle(sx + Ui.dp(getContext(), 5), base - numPaint.getTextSize() * 0.3f, Ui.dp(getContext(), 2), errPaint);
                        errPaint.setStyle(Paint.Style.STROKE);
                    }
                    logical++;
                }
            }
        }
    }

    /** 1-based logical line number of a visual (wrapped) line */
    private int logicalAt(Layout l, int visual) {
        CharSequence txt = getText();
        int end = Math.min(l.getLineStart(visual), txt.length());
        int n = 1;
        for (int i = 0; i < end; i++) if (txt.charAt(i) == '\n') n++;
        return n;
    }

    @Override protected void onScrollChanged(int hl, int vt, int ohl, int ovt) {
        super.onScrollChanged(hl, vt, ohl, ovt);
        invalidate();
    }

    static Rect unused() { return new Rect(); }
}
