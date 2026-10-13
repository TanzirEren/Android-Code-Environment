package com.ace.ide;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;

/** Creates a ready-to-build Java + XML Android project. */
public final class Templates {

    public static final String[] NAMES = {"Empty Activity", "Hello Counter (demo UI)", "WebView App", "Notes List"};
    public static final String[] DESCS = {
            "One activity with a centered label",
            "Header, counter, buttons, toast, saved state",
            "Address bar + progress + WebView",
            "ListView, add / delete, saved in SharedPreferences"};

    public static final class Spec {
        public String name, appName, pkg;
        public int minSdk = 21, targetSdk = 34, template = 1;
        public Bitmap icon;
        public int radiusPct = 22;
        public boolean internet = false;
    }

    private Templates() {}

    public static File create(Context ctx, Spec s) throws Exception {
        File root = new File(Project.aceRoot(ctx), FileUtil.safeName(s.name));
        File main = new File(root, "app/src/main");
        File res = new File(main, "res");
        File java = new File(main, "java/" + s.pkg.replace('.', '/'));
        java.mkdirs();
        new File(root, "libs").mkdirs();
        for (String d : new String[]{"layout", "values", "drawable"}) new File(res, d).mkdirs();

        boolean internet = s.internet || s.template == 2;
        FileUtil.writeText(new File(main, "AndroidManifest.xml"), manifest(s, internet));
        FileUtil.writeText(new File(res, "values/strings.xml"), strings(s));
        FileUtil.writeText(new File(res, "values/colors.xml"), colors());
        FileUtil.writeText(new File(res, "values/styles.xml"), styles());
        FileUtil.writeText(new File(res, "drawable/btn_primary.xml"), BTN);
        FileUtil.writeText(new File(res, "drawable/card_bg.xml"), CARD);
        FileUtil.writeText(new File(res, "layout/activity_main.xml"), layout(s.template));
        FileUtil.writeText(new File(java, "MainActivity.java"), code(s));
        FileUtil.writeText(new File(root, ".gitignore"), "build/\noutput/\n*.keystore\n");
        FileUtil.writeText(new File(root, "README.md"), "# " + s.appName + "\n\nCreated with ACE (Android Code Environment).\n"
                + "Package: `" + s.pkg + "`\n\nRun the project with the ▶ button — the APK is written to `output/`.\n");
        Bitmap icon = s.icon != null ? s.icon : IconGen.defaultIcon(ctx);
        IconGen.writeAll(icon, s.radiusPct, res);
        return root;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("\"", "\\\"");
    }

    private static String manifest(Spec s, boolean internet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        sb.append("    package=\"").append(s.pkg).append("\">\n\n");
        if (internet) sb.append("    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n");
        sb.append("    <application\n");
        sb.append("        android:allowBackup=\"true\"\n");
        sb.append("        android:icon=\"@mipmap/ic_launcher\"\n");
        sb.append("        android:roundIcon=\"@mipmap/ic_launcher_round\"\n");
        sb.append("        android:label=\"@string/app_name\"\n");
        sb.append("        android:theme=\"@style/AppTheme\">\n\n");
        sb.append("        <activity\n            android:name=\".MainActivity\"\n            android:exported=\"true\">\n");
        sb.append("            <intent-filter>\n");
        sb.append("                <action android:name=\"android.intent.action.MAIN\" />\n");
        sb.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n");
        sb.append("            </intent-filter>\n        </activity>\n\n    </application>\n</manifest>\n");
        return sb.toString();
    }

    private static String strings(Spec s) {
        return "<resources>\n    <string name=\"app_name\">" + esc(s.appName) + "</string>\n"
                + "    <string name=\"hello\">Hello from " + esc(s.appName) + "!</string>\n"
                + "    <string name=\"count_label\">Taps</string>\n"
                + "    <string name=\"add\">Add</string>\n    <string name=\"go\">Go</string>\n"
                + "    <string name=\"reset\">Reset</string>\n</resources>\n";
    }

    private static String colors() {
        return "<resources>\n    <color name=\"colorPrimary\">#6B6753</color>\n"
                + "    <color name=\"colorPrimaryDark\">#55523F</color>\n"
                + "    <color name=\"colorAccent\">#928C6F</color>\n"
                + "    <color name=\"background\">#F3EEEA</color>\n"
                + "    <color name=\"surface\">#FFFFFF</color>\n"
                + "    <color name=\"textPrimary\">#2B2A22</color>\n"
                + "    <color name=\"textSecondary\">#6F6A5A</color>\n</resources>\n";
    }

    private static String styles() {
        return "<resources>\n    <style name=\"AppTheme\" parent=\"@android:style/Theme.Material.Light.NoActionBar\">\n"
                + "        <item name=\"android:colorPrimary\">@color/colorPrimary</item>\n"
                + "        <item name=\"android:colorPrimaryDark\">@color/colorPrimaryDark</item>\n"
                + "        <item name=\"android:colorAccent\">@color/colorAccent</item>\n"
                + "        <item name=\"android:statusBarColor\">@color/colorPrimaryDark</item>\n"
                + "        <item name=\"android:windowBackground\">@color/background</item>\n"
                + "    </style>\n</resources>\n";
    }

    private static final String BTN = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n"
            + "    <solid android:color=\"@color/colorPrimary\" />\n    <corners android:radius=\"14dp\" />\n</shape>\n";

    private static final String CARD = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">\n"
            + "    <solid android:color=\"@color/surface\" />\n    <corners android:radius=\"18dp\" />\n"
            + "    <stroke android:width=\"1dp\" android:color=\"#E0D7D0\" />\n</shape>\n";

    private static String layout(int t) {
        String head = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
        String ns = "xmlns:android=\"http://schemas.android.com/apk/res/android\"";
        if (t == 0) {
            return head + "<FrameLayout " + ns + "\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n"
                    + "    <TextView\n        android:id=\"@+id/label\"\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"wrap_content\"\n"
                    + "        android:layout_gravity=\"center\"\n        android:text=\"@string/hello\"\n        android:textColor=\"@color/textPrimary\"\n        android:textSize=\"20sp\" />\n\n</FrameLayout>\n";
        }
        if (t == 1) {
            return head + "<LinearLayout " + ns + "\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n"
                    + "    <TextView\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:background=\"@color/colorPrimary\"\n"
                    + "        android:padding=\"20dp\"\n        android:text=\"@string/app_name\"\n        android:textColor=\"#FFFFFF\"\n        android:textSize=\"20sp\"\n        android:textStyle=\"bold\" />\n\n"
                    + "    <LinearLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:layout_margin=\"16dp\"\n        android:background=\"@drawable/card_bg\"\n"
                    + "        android:gravity=\"center_horizontal\"\n        android:orientation=\"vertical\"\n        android:padding=\"20dp\">\n\n"
                    + "        <TextView\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"wrap_content\"\n            android:text=\"@string/count_label\"\n            android:textColor=\"@color/textSecondary\" />\n\n"
                    + "        <TextView\n            android:id=\"@+id/counter\"\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"wrap_content\"\n            android:text=\"0\"\n            android:textColor=\"@color/textPrimary\"\n            android:textSize=\"56sp\"\n            android:textStyle=\"bold\" />\n\n"
                    + "        <Button\n            android:id=\"@+id/btnTap\"\n            android:layout_width=\"match_parent\"\n            android:layout_height=\"wrap_content\"\n            android:layout_marginTop=\"12dp\"\n            android:background=\"@drawable/btn_primary\"\n            android:text=\"+1\"\n            android:textColor=\"#FFFFFF\" />\n\n"
                    + "        <Button\n            android:id=\"@+id/btnReset\"\n            android:layout_width=\"match_parent\"\n            android:layout_height=\"wrap_content\"\n            android:layout_marginTop=\"8dp\"\n            android:text=\"@string/reset\" />\n    </LinearLayout>\n\n</LinearLayout>\n";
        }
        if (t == 2) {
            return head + "<LinearLayout " + ns + "\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n"
                    + "    <LinearLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:background=\"@color/colorPrimary\"\n        android:gravity=\"center_vertical\"\n        android:orientation=\"horizontal\"\n        android:padding=\"8dp\">\n\n"
                    + "        <EditText\n            android:id=\"@+id/url\"\n            android:layout_width=\"0dp\"\n            android:layout_height=\"wrap_content\"\n            android:layout_weight=\"1\"\n            android:background=\"@drawable/card_bg\"\n            android:hint=\"https://\"\n            android:imeOptions=\"actionGo\"\n            android:inputType=\"textUri\"\n            android:padding=\"10dp\"\n            android:singleLine=\"true\"\n            android:text=\"https://example.com\"\n            android:textSize=\"14sp\" />\n\n"
                    + "        <Button\n            android:id=\"@+id/go\"\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"wrap_content\"\n            android:layout_marginLeft=\"8dp\"\n            android:text=\"@string/go\" />\n    </LinearLayout>\n\n"
                    + "    <ProgressBar\n        android:id=\"@+id/progress\"\n        style=\"?android:attr/progressBarStyleHorizontal\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"3dp\"\n        android:max=\"100\" />\n\n"
                    + "    <WebView\n        android:id=\"@+id/web\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"0dp\"\n        android:layout_weight=\"1\" />\n\n</LinearLayout>\n";
        }
        return head + "<LinearLayout " + ns + "\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n\n"
                + "    <TextView\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:background=\"@color/colorPrimary\"\n        android:padding=\"20dp\"\n        android:text=\"@string/app_name\"\n        android:textColor=\"#FFFFFF\"\n        android:textSize=\"20sp\"\n        android:textStyle=\"bold\" />\n\n"
                + "    <LinearLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"wrap_content\"\n        android:orientation=\"horizontal\"\n        android:padding=\"10dp\">\n\n"
                + "        <EditText\n            android:id=\"@+id/input\"\n            android:layout_width=\"0dp\"\n            android:layout_height=\"wrap_content\"\n            android:layout_weight=\"1\"\n            android:hint=\"New note\"\n            android:singleLine=\"true\" />\n\n"
                + "        <Button\n            android:id=\"@+id/add\"\n            android:layout_width=\"wrap_content\"\n            android:layout_height=\"wrap_content\"\n            android:text=\"@string/add\" />\n    </LinearLayout>\n\n"
                + "    <ListView\n        android:id=\"@+id/list\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"0dp\"\n        android:layout_weight=\"1\" />\n\n</LinearLayout>\n";
    }

    private static String code(Spec s) {
        String pk = "package " + s.pkg + ";\n\n";
        if (s.template == 0) {
            return pk + "import android.app.Activity;\nimport android.os.Bundle;\n\npublic class MainActivity extends Activity {\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n    }\n}\n";
        }
        if (s.template == 1) {
            return pk + "import android.app.Activity;\nimport android.content.SharedPreferences;\nimport android.os.Bundle;\nimport android.view.View;\nimport android.widget.TextView;\nimport android.widget.Toast;\n\n"
                    + "public class MainActivity extends Activity {\n\n    private int count;\n    private TextView counter;\n    private SharedPreferences prefs;\n\n"
                    + "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n"
                    + "        prefs = getSharedPreferences(\"app\", MODE_PRIVATE);\n        count = prefs.getInt(\"count\", 0);\n        counter = (TextView) findViewById(R.id.counter);\n        show();\n\n"
                    + "        findViewById(R.id.btnTap).setOnClickListener(new View.OnClickListener() {\n            @Override\n            public void onClick(View v) {\n                count++;\n                show();\n                if (count % 10 == 0) {\n                    Toast.makeText(MainActivity.this, \"Nice! \" + count + \" taps\", Toast.LENGTH_SHORT).show();\n                }\n            }\n        });\n\n"
                    + "        findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {\n            @Override\n            public void onClick(View v) {\n                count = 0;\n                show();\n            }\n        });\n    }\n\n"
                    + "    private void show() {\n        counter.setText(String.valueOf(count));\n        prefs.edit().putInt(\"count\", count).apply();\n    }\n}\n";
        }
        if (s.template == 2) {
            return pk + "import android.app.Activity;\nimport android.graphics.Bitmap;\nimport android.os.Bundle;\nimport android.view.KeyEvent;\nimport android.view.View;\nimport android.view.inputmethod.EditorInfo;\nimport android.webkit.WebChromeClient;\nimport android.webkit.WebView;\nimport android.webkit.WebViewClient;\nimport android.widget.EditText;\nimport android.widget.ProgressBar;\nimport android.widget.TextView;\n\n"
                    + "public class MainActivity extends Activity {\n\n    private WebView web;\n    private EditText url;\n    private ProgressBar progress;\n\n"
                    + "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n        web = (WebView) findViewById(R.id.web);\n        url = (EditText) findViewById(R.id.url);\n        progress = (ProgressBar) findViewById(R.id.progress);\n\n"
                    + "        web.getSettings().setJavaScriptEnabled(true);\n        web.setWebViewClient(new WebViewClient() {\n            @Override\n            public void onPageStarted(WebView view, String u, Bitmap favicon) {\n                url.setText(u);\n            }\n        });\n        web.setWebChromeClient(new WebChromeClient() {\n            @Override\n            public void onProgressChanged(WebView view, int p) {\n                progress.setProgress(p);\n                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);\n            }\n        });\n\n"
                    + "        findViewById(R.id.go).setOnClickListener(new View.OnClickListener() {\n            @Override\n            public void onClick(View v) {\n                load();\n            }\n        });\n        url.setOnEditorActionListener(new TextView.OnEditorActionListener() {\n            @Override\n            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {\n                if (actionId == EditorInfo.IME_ACTION_GO) {\n                    load();\n                    return true;\n                }\n                return false;\n            }\n        });\n        load();\n    }\n\n"
                    + "    private void load() {\n        String u = url.getText().toString().trim();\n        if (!u.startsWith(\"http\")) {\n            u = \"https://\" + u;\n        }\n        web.loadUrl(u);\n    }\n\n"
                    + "    @Override\n    public void onBackPressed() {\n        if (web.canGoBack()) {\n            web.goBack();\n        } else {\n            super.onBackPressed();\n        }\n    }\n}\n";
        }
        return pk + "import android.app.Activity;\nimport android.content.SharedPreferences;\nimport android.os.Bundle;\nimport android.view.View;\nimport android.widget.AdapterView;\nimport android.widget.ArrayAdapter;\nimport android.widget.EditText;\nimport android.widget.ListView;\n\nimport org.json.JSONArray;\n\nimport java.util.ArrayList;\nimport java.util.List;\n\n"
                + "public class MainActivity extends Activity {\n\n    private final List<String> notes = new ArrayList<String>();\n    private ArrayAdapter<String> adapter;\n    private SharedPreferences prefs;\n    private EditText input;\n\n"
                + "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n        prefs = getSharedPreferences(\"notes\", MODE_PRIVATE);\n        load();\n\n"
                + "        input = (EditText) findViewById(R.id.input);\n        ListView list = (ListView) findViewById(R.id.list);\n        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, notes);\n        list.setAdapter(adapter);\n\n"
                + "        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {\n            @Override\n            public void onClick(View v) {\n                String t = input.getText().toString().trim();\n                if (t.length() == 0) {\n                    return;\n                }\n                notes.add(0, t);\n                input.setText(\"\");\n                changed();\n            }\n        });\n\n"
                + "        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {\n            @Override\n            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {\n                notes.remove(position);\n                changed();\n                return true;\n            }\n        });\n    }\n\n"
                + "    private void changed() {\n        adapter.notifyDataSetChanged();\n        prefs.edit().putString(\"items\", new JSONArray(notes).toString()).apply();\n    }\n\n"
                + "    private void load() {\n        try {\n            JSONArray a = new JSONArray(prefs.getString(\"items\", \"[]\"));\n            for (int i = 0; i < a.length(); i++) {\n                notes.add(a.getString(i));\n            }\n        } catch (Exception ignored) {\n        }\n    }\n}\n";
    }
}
