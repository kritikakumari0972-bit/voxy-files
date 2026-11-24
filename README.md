# VoxyFiles

**VoxyFiles** is a native Android file-management workstation inspired by MT Manager, ZArchiver, X-plore, MiXplorer, Solid Explorer, FX, Total Commander and FolderSync.

## Current milestone — v0.1.0 foundation

Implemented:
- Native Kotlin + Jetpack Compose Android app.
- Android Storage Access Framework (SAF) folder picker with persistable access.
- Browse a user-selected folder and its subfolders.
- Search within the current folder.
- Create folders and empty text files.
- Open files through compatible Android apps.
- Rename and delete selected items.
- Multi-select, copy, cut and paste files/folders within SAF-accessible storage.
- File properties and a recursive storage report.
- Light/dark appearance preference.
- Android 8.0 (API 26) minimum; target SDK 36.

## Build

Open the project in recent Android Studio with JDK 17 and Android SDK Platform 36 installed. Sync Gradle and run the `app` configuration. GitHub Actions builds the debug APK on pushes and pull requests.

## Storage and privacy

VoxyFiles uses Android's Storage Access Framework. Users explicitly choose a folder and grant access; the app persists that permission. Access to protected Android/data, Android/obb, other apps' private data, or root-only paths is not implied. Shizuku/root support will be separate opt-in modules with clear requirements.

The current foundation performs file operations locally through Android document-provider APIs. It does not upload file contents.

## Planned modules

1. Archive engine: create, inspect and extract supported formats.
2. APK workspace: package inspection, split APK handling and signing information.
3. Editors: text/code, HEX, AXML, ARSC and DEX tooling.
4. Storage intelligence: disk map, duplicate finder, large-file analysis and safe cleanup.
5. Network: SMB, FTP/FTPS, SFTP and WebDAV.
6. Cloud: pluggable providers and Android DocumentsProvider integration.
7. Sync and backup: scheduled and conflict-aware folder synchronization.
8. Security: encrypted vault and file encryption.
9. Power tools: checksums, file comparison, SQLite and Git explorers.
10. Remote access and plugin SDK.

Roadmap items are not claims that they are already implemented.

## License

A project license will be selected before public distribution. Third-party libraries and engines will retain their own licenses and notices.
