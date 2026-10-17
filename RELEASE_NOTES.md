# Release

**Tag:** `v1.1.0-Evolution`

**Title:** `ACE v1.1.0 "Evolution" — new blue look, new logo & icon, automatic fast library downloads`

## Release notes (paste into the GitHub release description)

### ACE 1.1.0 — Evolution

The second release of ACE (Android Code Environment) gives the whole app a new identity and removes the manual
work of managing libraries.

#### 🎨 New look
- **New logo and app icon** — `Logo.png` is the in-app logo (splash, home, about) and `Icon.png` is the launcher icon
  (round and adaptive icons included). It is also the default icon of newly created projects.
- **New colors** — app background **`#385F8C`**, icons, text and controls **`#E9E9E9`**, applied to the home screen,
  glass navigation, dialogs, explorer, code editor, settings and all other screens. Syntax colors were re-tuned for the blue background.
- New projects from the templates use the same blue palette.

#### ⚡ Automatic fast library downloads
- **Auto-detect per project:** ACE reads the imports in your code and the `build.gradle` of imported/cloned projects,
  adds the matching libraries and fetches them — no manual setup.
- **Parallel downloads** (4 at a time) with a cache, so builds are faster and work offline afterwards.
- **Transitive dependencies** of Maven coordinates are resolved from POM files.
- **Download animation in the code IDE:** a slim bar with a bouncing arrow, shimmering progress, percentage and the
  current file; ✔ when done, red with *tap to retry* when offline.
- Add an `import` of a known library and press save — it is picked up and downloaded automatically.
- New setting: *Settings → Libraries → Auto-detect libraries*.

#### 🔧 Other changes
- Version 1.1.0 (versionCode 2).
- Theme switch removed from Settings (ACE now has a single blue theme).

#### ⚠️ Limitations (unchanged)
- Java + XML only (no Kotlin), no annotation processors.
- AndroidX / AppCompat / Material **resources** are not merged — libraries that need Android resources
  (for example Glide, RecyclerView) are downloaded but their resources are not used.
- 9-patch images are copied as-is; no NDK builds.

#### 📦 Install
Download `app-debug.apk` (artifact **ACE-debug-apk** from the Actions run), install it over 1.0 (same app id), allow
*All files access* if asked, and open the **App Builder** tab. Requires Android 8.0 (API 26) or newer.

**Full guide:** [`docs/README.md`](docs/README.md) · **License:** MIT
