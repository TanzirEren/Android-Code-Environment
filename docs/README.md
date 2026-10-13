# ACE — User Guide

ACE (**A**ndroid **C**ode **E**nvironment) lets you write, build, sign and install Android apps directly on your
phone using **Java and XML**. This guide explains every part of the app.

**Contents**
1. [Install and first start](#1-install-and-first-start)
2. [The two tabs](#2-the-two-tabs)
3. [App Builder: your first app in 2 minutes](#3-app-builder-your-first-app-in-2-minutes)
4. [Creating, importing and cloning projects](#4-creating-importing-and-cloning-projects)
5. [Project folder structure](#5-project-folder-structure)
6. [The code IDE](#6-the-code-ide)
7. [File explorer](#7-file-explorer)
8. [Running a build](#8-running-a-build)
9. [Project settings](#9-project-settings)
10. [Libraries](#10-libraries)
11. [Signing keys and release builds](#11-signing-keys-and-release-builds)
12. [Java → DEX tab](#12-java--dex-tab)
13. [App settings](#13-app-settings)
14. [What resources and Java features are supported](#14-what-is-supported)
15. [Troubleshooting](#15-troubleshooting)
16. [Tips](#16-tips)

---

## 1. Install and first start

1. Build ACE with the GitHub Action (see the main `README.md`) and install `app-debug.apk`.
2. On first start ACE asks for **All files access**. Allow it — projects and APKs are saved in
   `/storage/emulated/0/ACE/` so you can see them in any file manager.
3. The first build can take a little longer: ACE reads the Android framework resource table once and caches it.

Requirements: Android 8.0+ (API 26). Internet is only needed to download libraries and to clone Git repositories.

## 2. The two tabs

A floating glass bar at the bottom switches between:

| Tab | Purpose |
|---|---|
| **Java → DEX** | Convert Java source files into `classes.dex` (and view Smali). |
| **App Builder** | Create / import / clone Android projects and build installable APKs. |

The **+** button (bottom right) creates something new on the current tab. In **App Builder** the **gear icon**
(top right) opens Settings; in **Java → DEX** the **info icon** opens the feature list.

## 3. App Builder: your first app in 2 minutes

1. Open **App Builder** → tap **+** → **New project**.
2. Enter a project name (for example `Hello`). The app name and package id fill in automatically.
3. Keep the default template (**Hello Counter**) and tap **Create project**. The code IDE opens.
4. Tap the **▶** icon (top bar). A dialog shows each build step live.
5. When it says **Build successful**, tap **Install** (or **Download** to save the APK in `Downloads/ACE`).

The APK is also kept in `/storage/emulated/0/ACE/Hello/output/`.

## 4. Creating, importing and cloning projects

Tap **+** on the App Builder tab. A dialog offers three choices.

### New project
| Field | Meaning |
|---|---|
| Project name | Folder name inside `ACE/`. Must not exist yet. |
| App name | Name shown under the launcher icon. |
| Package id | Like `com.example.hello` — lowercase, at least two parts. Cannot be changed later. |
| App icon | Default is the ACE logo. Tap *Choose image* to use your own; drag **Icon radius** (0 % square … 50 % circle). |
| API level | Min SDK / Target SDK are set automatically (defaults come from Settings). Tap to change. |
| Template | Empty Activity · Hello Counter · WebView App · Notes List |
| Internet permission | Adds `INTERNET` to the manifest (always on for the WebView template). |

### Import project
Opens a storage browser. Select a project folder. ACE finds `AndroidManifest.xml` automatically in these layouts:
- ACE / Android Studio: `app/src/main/AndroidManifest.xml`
- Single module: `src/main/AndroidManifest.xml`
- AIDE / Eclipse style: `AndroidManifest.xml` in the folder root

If the folder is outside `ACE/`, it is **copied** into `ACE/` first. Package name, SDK levels and version are read
from the manifest and `build.gradle` when available.

### Clone repository
1. Paste the repository URL, e.g. `https://github.com/user/repo.git`.
2. The folder name is filled from the URL — you can rename it.
3. Optional: branch, and username + access token for private repositories
   (they can also be saved in Settings → Git).
4. Tap **Clone**. If git fails for a public GitHub repository, ACE falls back to downloading the zip.

## 5. Project folder structure

```
/storage/emulated/0/ACE/<Project_Name>/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/<your/package>/MainActivity.java
│   └── res/
│       ├── layout/  values/  drawable/  mipmap-*/
├── libs/                 ← put .jar / .aar files here
├── output/               ← built APKs and last-build.log
├── build/                ← temporary build files (deleted at the start of every build)
├── ace.json              ← project settings (name, version, SDK, libraries)
└── .gitignore
```

Java → DEX conversions are written to `/storage/emulated/0/ACE/Java2Dex/<name>/`.

## 6. The code IDE

### Top bar (left → right)
| Icon | Action |
|---|---|
| ☰ | Open the file explorer |
| Title | Project name and current file |
| ▶ | Save everything and build (App Builder) / convert (Java → DEX) |
| ↶ ↷ | Undo / redo |
| 💾 | Save all files |
| ⋮ | Menu: settings, project settings, libraries, find & replace, go to line, snippets, format, comment, duplicate line, errors, build log, clean, export zip, close tabs |

Under the top bar is a row of **file tabs**. Tap to switch, ✕ to close; a ● means unsaved changes.

### Writing code
- **Syntax highlighting** for Java, XML, JSON and config files.
- **Red errors:** while you type, ACE checks Java with the real compiler and XML with a parser. Lines with problems
  get a red background, a wavy underline and a red line number. Tap the red *● N errors* at the bottom to see the list
  and jump to a line. After a failed build the build errors are marked the same way.
- **Pinch zoom:** use two fingers in the editor to change the text size (8–32 sp; 12 sp is the default).
- **Auto indent**, **auto-close** of `( [ { " '`, **current line highlight**, **line numbers**.
- **Suggestions bar** above the symbol bar: Java keywords, common Android classes/methods, XML tags/attributes and
  words already used in the file. Tap a word to insert it.
- **Symbol bar** at the bottom: Tab, `{ } ( ) [ ] ; " ' < > / = . , :` and more.

### ⋮ menu tools
- **Find & replace** — case-sensitive and regex options, *Next*, *Replace*, *All*.
- **Go to line**
- **Snippets** — onCreate, Toast, Log, click listener, Handler, Thread + UI, SharedPreferences, AlertDialog,
  adapter, HTTP GET, and XML Button / TextView / EditText.
- **Format code** — re-indents Java, JSON and XML.
- **Comment / uncomment** the selected lines (`//` or `<!-- -->`).
- **Duplicate line**, **Show errors**, **Last build log**, **Clean build**, **Export project (zip)**.

Files are saved automatically when you leave the editor or press ▶ (Settings → Editor → Auto-save).

## 7. File explorer

Open it with ☰. A glass panel slides in from the left and shows the project as a tree.

Header icons: **+** create · **import** · **refresh**.

- Tap a folder to expand/collapse it, tap a file to open it. Images open in a preview.
- **Long-press a file:** Open, Rename, Duplicate, Change path (move), Export, Share, Copy path, Properties, Delete.
- **Long-press a folder:** New file here, New folder here, Import files here, Import folder here, Rename, Duplicate,
  Change path, Export folder (zip), Copy path, Properties, Expand all, Collapse all, Delete.
- **Import** lets you pick one or many files, or a whole folder, from anywhere on your storage.
- **Export** saves to `Downloads/ACE` or to any folder you choose.

New `.java` files get a package line and class skeleton automatically; new XML files in `layout`, `values`,
`drawable` and `menu` folders get a starter template.

## 8. Running a build

Press **▶**. ACE runs these steps and shows the log live:

1. **Preparing libraries** — downloads missing libraries (first time only), reads `libs/`.
2. **Compiling resources** — XML → binary, builds `resources.arsc` and `R.java`.
3. **Compiling Java** — Java 8 source level.
4. **Converting to DEX**
5. **Packaging APK**
6. **Signing APK** — v1 + v2 + v3.

**On success** a dialog offers:
- **Download** — copies the APK to `Downloads/ACE/`
- **Install** — installs through the system installer (Android asks you to allow "install unknown apps" for ACE the first time)
- **Share**

**On failure** a dialog shows all errors. Tap **Copy all** to copy them in one go, **Close** to dismiss. The errors are
also marked in the editor (red lines); the first error file opens automatically.

## 9. Project settings

⋮ → **Project settings** (or long-press a project on the home screen).

| Section | Options |
|---|---|
| App | App name (also updates `strings.xml`), version name, version code, package (read-only) |
| SDK | Minimum SDK (21–30), Target SDK (28–34) |
| Build & signing | **Release build** switch, release key |
| App icon | Pick an image, set corner radius, regenerate all icon sizes |
| Permissions | 12 switches (Internet, Camera, Microphone, Location, Storage, Notifications …) — edits the manifest |
| Project | Libraries, location, remove from list, delete with files |

## 10. Libraries

⋮ → **Libraries** (or Project settings → Libraries).

- **Catalog (30 libraries):** Gson, OkHttp, OkHttp Logging, Retrofit, Retrofit-Gson, Jsoup, Commons Lang / IO / Codec /
  Collections / CSV / Text / Math, Guava, RxJava 2 & 3, EventBus, Joda-Time, ZXing, Zip4j, Java-WebSocket, Paho MQTT,
  CommonMark, BouncyCastle, ORMLite, Picasso, jBCrypt, HttpRequest, SLF4J, JetBrains Annotations.
  Switch one on — it downloads from Maven Central and is cached.
- **Maven coordinate:** `group:artifact:version`, for example `com.squareup.okhttp3:okhttp:3.14.9`.
  Add `@aar` to fetch an aar. Dependencies of a library must be added too.
- **Import .jar / .aar:** copies files into `libs/`.

Notes:
- Libraries must be built for **Java 8 or older** bytecode (the on-device compiler is Java 8).
- `.aar` files work when they contain classes only. Android **resources inside libraries** (AppCompat, Material,
  RecyclerView layouts/styles…) are **not** merged.

## 11. Signing keys and release builds

Settings → **Signing keys**.

- **Debug builds** use ACE's built-in debug key. Nothing to set up.
- **Generate key:** name, alias, password (6+ characters), your name, optional organization/city/country, validity
  (years). Creates a 2048-bit RSA PKCS12 key.
- **Import key:** pick a `.jks`, `.keystore`, `.p12`, `.pfx` or `.bks` file, enter the keystore password, optional
  alias and key password. Android Studio `.jks` keystores work.
- Tap a key to see its SHA-1 / SHA-256, set it as default, export a backup, or delete it.

To make a release build: Project settings → turn on **Release build** → choose the **Release key** → press ▶.
The APK is named `<Project>-release.apk`.

> **Back up your key and passwords.** An app can only be updated by an APK signed with the same key.

## 12. Java → DEX tab

For turning Java source into `classes.dex`:

1. **+** → add `.java` files, folders or a `.zip`, optionally library `.jar` files; or write code in the IDE.
2. Open the project and press **▶** in the IDE (or *Convert* on the detail page).
3. On success: **Download** (to `Downloads/ACE`), **Smali** (browse the generated Smali) or **Share**.

Settings → *Java → DEX*: minimum API level, and **Bundle libraries into the DEX**. Output files are in
`/storage/emulated/0/ACE/Java2Dex/<name>/`.

## 13. App settings

Gear icon on the App Builder tab (or ⋮ → Settings in the IDE).

| Section | What you can change |
|---|---|
| Appearance | Theme (system / light / dark), show hidden files |
| Editor | Font size, pinch zoom, word wrap, line numbers, current-line highlight, auto indent, auto-close, symbol bar, suggestions, live error check, auto-save, keep screen on |
| New app defaults | Package prefix, min/target SDK, default template, default icon radius |
| Java → DEX | Min API level, bundle libraries |
| Signing | Opens the key manager |
| Git | Default username and access token |
| Storage | ACE folder location, clear library cache, rebuild framework cache, clear DEX outputs |
| About | Features, developer page, reset all settings |

## 14. What is supported

**Language:** Java up to source level 1.8 (lambdas, generics, inner classes …). Libraries are desugared by D8.

**Resource folders:** `layout`, `drawable` (shape, selector, layer-list, vector, …), `mipmap`, `menu`, `xml`, `anim`,
`animator`, `interpolator`, `transition`, `raw`, `font`, `color`, `navigation`, `values`.

**Values:** `string`, `color`, `dimen`, `integer`, `bool`, `fraction`, `id`, `style`, `attr`, `declare-styleable`,
`string-array`, `integer-array`, `array`, `plurals`.

**Qualifiers:** `night`/`notnight`, `land`/`port`, `sw600dp`, `w`/`h`, `small…xlarge`, density (`mdpi … xxxhdpi`, `nodpi`,
`anydpi`), `v21`-style versions, two-letter locales (`values-bn`, `values-es-rUS`).

**Not supported:** Kotlin · annotation processors · AndroidX/AppCompat/Material resources · 9-patch processing ·
product flavors · NDK builds (prebuilt `.so` in `app/src/main/jniLibs/<abi>/` are packaged) · `tools:` attributes are removed.

Use framework themes such as `@android:style/Theme.Material.Light.NoActionBar` — the templates already do.

## 15. Troubleshooting

| Problem | What to do |
|---|---|
| *Resource not found: @string/xyz* | The name does not exist in `res/values`. Check spelling; the message suggests close matches. |
| *attribute 'xyz' not found* | Custom attributes need `values/attrs.xml`. AppCompat/Material attributes (`?attr/colorPrimary`) are not available — use `android:` attributes. |
| *'14' is not valid for attribute textSize* | Add a unit: `14sp`. |
| *cannot be resolved to a type* | Add the library (⋮ → Libraries) or an import statement. |
| *R cannot be resolved* in the editor | Build once (▶) so `R.java` exists, or fix resource errors first. |
| *Could not download …* | Check internet and the coordinate. The library is retried on the next build. |
| *class file has wrong version* | The library needs a newer Java than the on-device compiler supports; use an older version. |
| Install fails with *App not installed* | An app with the same package but another signature is installed — uninstall it first. |
| Install button asks for permission | Allow *Install unknown apps* for ACE in Android settings. |
| No files visible in `ACE/` | Grant *All files access* (Android Settings → Apps → ACE → Permissions). |
| First build is slow | ACE builds a framework cache once; later builds are faster. Settings → Storage → *Rebuild framework cache* re-creates it. |
| Wrong signing password | Settings → Signing keys → delete and import again. |

The full log of the last build is in ⋮ → **Last build log** and in `output/last-build.log`.

## 16. Tips

- Use *Hello Counter* as a starting point and edit `res/layout/activity_main.xml`.
- Keep `android:exported="true"` on activities with an intent-filter (ACE adds it for you if missing, with a warning).
- Zoom the editor with two fingers; set a permanent size in Settings → Editor.
- Clone a repository, press ▶ — many simple Java/XML projects build without changes.
- Export a project as a zip (⋮ → *Export project*) for backup.
- Commit `ace.json` to Git — it stores version, SDK and library choices.
