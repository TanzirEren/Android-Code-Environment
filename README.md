# ACE — Android Code Environment

Write, build and install Android apps **on your phone** using **Java + XML**.
ACE is the next version of Java2Dex: the Java → DEX converter is still here (first tab) and a
complete on-device **App Builder** (second tab) was added.

* App id `com.ace.ide` · min Android 8.0 (API 26) · built with GitHub Actions
* Projects are saved in `/storage/emulated/0/ACE/<Project_Name>/`
  (built APKs in `<Project_Name>/output/`). DEX conversions go to `ACE/Java2Dex/<name>/`.

## Build ACE on GitHub

1. Create a GitHub repository and upload **everything in this folder** (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab → *ACE CI* runs automatically (or press *Run workflow*).
3. When it is green, open the run and download the artifact **ACE-debug-apk**, install `app-debug.apk`.

The workflow copies `android.jar` of Android 34 into the app's assets (ACE needs the framework to compile
and to read the framework resource table) and runs `gradle :app:assembleDebug`.

## How it works (no Gradle, no aapt2 on the phone)

```
res/ + AndroidManifest.xml ──► ResCompiler (pure Java) ──► binary XML + resources.arsc + R.java
Java sources + R.java ───────► ECJ (Java 8) ───────────► .class
.class + library jars ───────► D8 ─────────────────────► classes.dex (multi-dex)
everything ──────────────────► aligned zip ────────────► APK
APK ─────────────────────────► apksig (v1+v2+v3) ──────► signed APK  → Download / Install / Share
```

The resource compiler (`app/src/main/java/com/ace/ide/build/`) was verified against Android's own `aapt`
(`aapt dump badging / xmltree / resources`), `zipalign -c` and `apksigner verify`.
The 4 project templates were compiled and packaged end-to-end the same way.

## Features (35+)

**Java → DEX** — Java → classes.dex (ECJ + D8) · import .java / folders / ZIP · bundle .jar libraries · multi-dex · Smali explorer · download / share.

**App Builder** — new-project wizard (name, app name, package id, icon + radius, API levels, template, permissions) ·
4 buildable templates (Empty, Counter demo, WebView, Notes list) · import any project folder (ACE / Android Studio / AIDE layouts) ·
clone a Git repo (JGit, token for private repos, GitHub zip fallback) · debug + release builds ·
keystore manager (generate, import `.jks` / `.p12` / `.bks`) · Install / Download / Share APK ·
project settings (version, SDK, signing key, icon, 12 permission switches) · library catalog (30 libraries),
Maven coordinates, your own `.jar` / `.aar` · clean build, last build log, export project as zip.

**Editor** — syntax highlighting (Java, XML, JSON, config) · live red error marks (ECJ + XML parser) while typing ·
two-finger pinch zoom (10–14 sp default) · line numbers · current-line highlight · undo / redo · find & replace (regex) ·
go to line · auto indent · auto-close brackets · symbol bar · word + Android API suggestions · 14 snippets ·
format code · comment toggle · duplicate line · tabs · auto-save · image preview.

**Files** — glass side explorer with tree · create / rename / duplicate / move / delete / export / share / properties ·
import files or folders from anywhere · in-app storage browser.

**Look** — ACE logo colors, light / dark / system theme, frosted-glass dialogs, floating pill navigation.

## Supported / not supported

Supported resources: `layout`, `drawable` (shape, selector, layer-list, vector …), `mipmap`, `menu`, `xml`, `anim`,
`animator`, `raw`, `font`, `color`; `values` (string, color, dimen, integer, bool, fraction, id, style, attr,
declare-styleable, string/integer arrays, plurals); qualifiers such as `night`, `land`, `sw600dp`, `hdpi`, `v21`, locales.

Not supported: Kotlin, annotation processors, AndroidX / AppCompat / Material **resources** (library resources are not
merged — use framework widgets/themes like the templates do), 9-patch image processing, product flavors, native (NDK) builds
(prebuilt `.so` in `jniLibs/` are packaged).

## Project layout

```
app/src/main/java/com/ace/ide/        UI, IDE, projects, settings
app/src/main/java/com/ace/ide/build/  ResCompiler, Axml, Arsc, ApkBuilder, Keys …
app/src/main/res/                     launcher icons made from the ACE logo
app/src/main/assets/                  developer.html, default_icon.png (android.jar is added by CI)
```
