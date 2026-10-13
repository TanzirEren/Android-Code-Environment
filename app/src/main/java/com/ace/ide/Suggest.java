package com.ace.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Word / API completion shown above the symbol bar. */
public final class Suggest {

    private static final String[] JAVA = ("abstract boolean break byte case catch char class continue default do double else enum extends final finally float for if "
            + "implements import instanceof int interface long new package private protected public return short static super switch synchronized this throw "
            + "throws try void volatile while true false null String StringBuilder Integer Long Double Boolean Math Object List ArrayList Map HashMap Set HashSet "
            + "Collections Arrays Iterator Override Runnable Thread Exception IOException File InputStream OutputStream "
            + "Activity Bundle Context Intent Handler Looper Log Toast View ViewGroup TextView Button EditText ImageView ListView WebView ProgressBar CheckBox "
            + "Switch Spinner SeekBar ScrollView LinearLayout RelativeLayout FrameLayout GridView LayoutInflater AlertDialog Dialog Menu MenuItem SharedPreferences "
            + "Environment Uri Color Bitmap BitmapFactory Canvas Paint Typeface Drawable Resources Configuration PackageManager PendingIntent Notification "
            + "NotificationManager BroadcastReceiver Service AsyncTask Fragment JSONObject JSONArray JSONException "
            + "onCreate onStart onResume onPause onStop onDestroy onClick onBackPressed onActivityResult onCreateOptionsMenu onOptionsItemSelected onSaveInstanceState "
            + "setContentView findViewById setText getText setOnClickListener setVisibility setBackgroundColor setTextColor setTextSize startActivity "
            + "startActivityForResult finish getSystemService getSharedPreferences getApplicationContext getResources getString runOnUiThread postDelayed "
            + "makeText LENGTH_SHORT LENGTH_LONG VISIBLE GONE INVISIBLE MATCH_PARENT WRAP_CONTENT getIntent putExtra getStringExtra getIntExtra "
            + "setLayoutParams addView removeView setPadding setOnLongClickListener setEnabled setAdapter notifyDataSetChanged "
            + "R.layout R.id R.string R.drawable R.color R.mipmap R.style R.dimen").split(" ");

    private static final String[] XML_TAGS = ("LinearLayout RelativeLayout FrameLayout ConstraintLayout ScrollView HorizontalScrollView TextView Button EditText ImageView "
            + "ImageButton CheckBox RadioButton RadioGroup Switch Spinner ListView GridView WebView ProgressBar SeekBar View include merge "
            + "uses-permission application activity service receiver provider intent-filter action category meta-data "
            + "resources string color dimen style item shape solid corners stroke gradient selector layer-list vector path").split(" ");

    private static final String[] XML_ATTRS = ("android:id android:layout_width android:layout_height android:layout_margin android:layout_marginTop android:layout_marginBottom "
            + "android:layout_marginLeft android:layout_marginRight android:padding android:paddingTop android:paddingBottom android:text android:textSize android:textColor "
            + "android:textStyle android:background android:orientation android:gravity android:layout_gravity android:layout_weight android:visibility android:src "
            + "android:hint android:inputType android:onClick android:name android:label android:icon android:theme android:exported android:scaleType android:singleLine "
            + "android:maxLines android:ellipsize android:elevation android:clickable android:enabled android:alpha android:color android:radius android:width "
            + "xmlns:android package match_parent wrap_content vertical horizontal center center_horizontal center_vertical bold italic").split(" ");

    private Suggest() {}

    public static List<String> get(int lang, String prefix, String fullText) {
        List<String> out = new ArrayList<String>();
        if (prefix == null || prefix.length() < 2) return out;
        String p = prefix.toLowerCase();
        Set<String> seen = new LinkedHashSet<String>();
        String[] dict = lang == Highlighter.XML ? merge(XML_TAGS, XML_ATTRS) : lang == Highlighter.JAVA ? JAVA : new String[0];
        add(seen, dict, p, prefix, true);
        // identifiers already used in the file
        if (fullText != null && fullText.length() < 200000) {
            Matcher m = Pattern.compile("[A-Za-z_][A-Za-z0-9_:.]{2,}").matcher(fullText);
            int guard = 0;
            while (m.find() && seen.size() < 24 && guard++ < 6000) {
                String w = m.group();
                if (w.length() > prefix.length() && w.toLowerCase().startsWith(p)) seen.add(w);
            }
        }
        for (String s : seen) {
            if (!s.equals(prefix)) out.add(s);
            if (out.size() >= 9) break;
        }
        return out;
    }

    private static void add(Set<String> into, String[] dict, String p, String raw, boolean prefixOnly) {
        for (String w : dict) if (w.length() > raw.length() && w.toLowerCase().startsWith(p)) into.add(w);
    }

    private static String[] merge(String[] a, String[] b) {
        String[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
