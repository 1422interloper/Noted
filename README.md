# Noted

A native Android notes app: a rewrite of [NotesMaster](https://github.com/PranshulGG/NotesMaster) by PranshulGG, which was a WebView-wrapped JavaScript app. Noted rebuilds it in Kotlin with Jetpack Compose and Material 3.

Rich-text notes, labels and folders, pinning, a recycle bin, search, PIN and fingerprint locking for individual notes, Material You theming, and JSON backup with optional automatic backup to a folder. Everything stays on the device; the app has no network permission.

## Install

Noted is distributed through **GitHub Releases** and is designed to be installed and updated with [Obtainium](https://github.com/ImranR98/Obtainium):

1. In Obtainium, tap **Add App**.
2. Paste `https://github.com/1422interloper/Noted`.
3. Install. Obtainium will notify you when new releases are published.

You can also download the APK directly from the [Releases](https://github.com/1422interloper/Noted/releases) page.

### Versioning

Versions are `MAJOR.MINOR.PATCH` ([Semantic Versioning](https://semver.org)): MAJOR for large or breaking releases, MINOR for new features, PATCH for bug fixes. `noted.versionCode` is separate: a whole number that goes up by one with every release, which is what Android and Obtainium use to tell that a release is newer.

### Cutting a release

1. Bump `noted.versionCode` (+1) and `noted.versionName` in `gradle.properties`.
2. Commit, then tag and push:
   ```sh
   git tag v0.1.0 && git push origin main v0.1.0
   ```
3. The workflow checks that the tag matches `versionName`, builds and signs the APK, and publishes the release.

## How this was built

Noted was built with a lot of help from Claude Code and some other OpenRouter sourced models via OpenCode as the chosen harness. I don't know much about Native App development so this was a nice project to start with and seeing as Pranshul's application is now archived I thought it would be perfect to learn with an app I utilise daily. As with
all agentic codebases I suggest you perform your own reviews as deemed necessary and if something is amiss I welcome you to open an Issue so it can be sorted out. 

## Why not a fork

I believed cloning and starting a fresh repo was a better option than forking the archived [NotesMaster](https://github.com/PranshulGG/NotesMaster) repo as this is a native port and isn't in the original spirit of Pranshuls app which is a JavaScript WebView style app. 

## License

GPL-3.0, same as NotesMaster, from which this project is derived. See [LICENSE](LICENSE).
