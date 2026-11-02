# PixelCategory Launcher

A production-grade, privacy-first, Google Pixel-inspired Android HOME replacement launcher built with **Kotlin**, **Jetpack Compose**, **Room**, and an **On-Device Hybrid Neural + TF-IDF AI Categorization Engine**.

---

## 1. Architecture Overview

PixelCategory Launcher follows **MVVM + Clean Architecture** with strict separation of concerns across six core layers:

- **UI & Design System (`com.example.ui.*`)**:
  - `Theme.kt`, `Color.kt`, `Type.kt`: Material You dynamic wallpaper color adaptation (`dynamicDarkColorScheme` / `dynamicLightColorScheme`), locally bundled Google Fonts (`Plus Jakarta Sans`, `Inter`, `JetBrains Mono`), and `LauncherDesignTokens` for frosted glass surfaces, blur radii, icon shapes, and grid geometry.
  - `HomeScreenContent.kt`: Multi-page workspace `HorizontalPager`, Pixel At-a-Glance header, drag-and-move grid placement, frosted glass folders, real Android widget hosting (`AppWidgetHostView`), pinned dock, and long-press context menus.
  - `AppDrawerSheet.kt`: Categorized app drawer with 18 default categories + user custom categories, in-category search, 5 sort modes, AI confidence badges, uncertain prediction approval banner, and Category Manager modal.
  - `UnifiedSearchSheet.kt`: Multi-source launcher search across Installed Apps, Folders, Device Contacts (`ContactsContract` with direct Call/SMS shortcuts), Local Files (`MediaStore` + Storage Access Framework), Offline Search History, and Web Search with a sandboxed embedded `WebView`.
  - `AiPreviewAndSettingsSheets.kt`: Interactive AI Home Screen Auto-Arrangement Preview & Undo/Restore modal, System Widget Picker, and 5-tab Customization & Privacy Center.
- **Offline AI & Auto-Arrangement (`com.example.ai.*`)**:
  - `OfflineAiClassifier.kt`: 8-step hybrid classification pipeline running 100% offline. Loads `assets/app_category_model.tflite` (binary float32 tensor bundle) and `assets/app_category_weights.json`, combining user manual overrides, a 500+ package signature knowledge base, Android `ApplicationInfo.category` metadata, and a 2-Layer Neural Softmax + TF-IDF inference pass with confidence threshold gating (`0.56f`).
  - `AiHomeOrganizer.kt`: Generates non-destructive `PIXEL_DEFAULT` and `AI_ORGANIZED` home screen layouts, grouping related categories into smart folders, prioritizing favorites in the dock, preserving widgets and pinned shortcuts, and storing layout snapshots in Room for 1-tap Undo & Restore.
- **Platform Engines (`com.example.engine.*`)**:
  - `AppDiscoveryEngine.kt`: Discovers all launchable activities via `LauncherApps` and `PackageManager`, caches icons in a 220-entry `LruCache<String, ImageBitmap>`, and listens for live package additions, updates, and removals.
  - `IconPackManager.kt`: Discovers ADW (`org.adw.launcher.THEMES`), Nova (`com.novalauncher.THEME`), and Apex icon packs, parses `appfilter.xml` mappings, and provides built-in Material Tinted & Monochrome Glass shader packs.
  - `UnifiedSearchEngine.kt`, `WidgetHostController.kt`, `LocalBackupManager.kt`.
- **Database (`com.example.data.local.*`)**:
  - Room SQLite database (`LauncherDatabase.kt`) with 13 entities (`AppEntity`, `CategoryEntity`, `AppCategoryCrossRef`, `HomeScreenPageEntity`, `HomeScreenItemEntity`, `FolderEntity`, `LauncherSettingsEntity`, `IconPackEntity`, `IconOverrideEntity`, `SearchHistoryEntity`, `AIClassificationEntity`, `AIUserCorrectionEntity`, `LayoutSnapshotEntity`) and non-destructive migrations (`MIGRATION_1_2`).

---

## 2. Offline AI Model & Training Pipeline

The embedded categorization model requires **zero internet access, zero API keys, and zero cloud calls**:

- **Training Script**: `scripts/train_category_model.py`
- **Architecture**: 640-feature Word & Character 3-gram TF-IDF vectorizer -> Dense Layer (48 units, ReLU) -> Dense Output Layer (18 classes, Softmax) trained via SGD with L2 regularization.
- **Artifacts**:
  - `app/src/main/assets/app_category_model.tflite`: Binary `TFL3` float32 tensor bundle (`idf`, `W1`, `b1`, `W2`, `b2`).
  - `app/src/main/assets/app_category_weights.json`: Vocabulary index, known package dictionary, and fallback weights.
- **Retraining**: Run `python3 scripts/train_category_model.py` from the project root to regenerate both model artifacts.

---

## 3. Permissions & Privacy Documentation

PixelCategory Launcher contains **zero advertisements and zero analytics SDKs**:

- `QUERY_ALL_PACKAGES`: Required for a HOME replacement launcher on Android 11+ to discover all installed launchable applications.
- `READ_CONTACTS` *(Optional, Runtime)*: Requested only when the user enables local Contacts search inside the Unified Search sheet. Contacts are queried strictly locally via `ContactsContract` and never uploaded.
- `INTERNET` & `ACCESS_NETWORK_STATE`: Used exclusively for optional Web Search suggestions and the embedded `WebView` when online.
- `VIBRATE`, `EXPAND_STATUS_BAR`, `SET_WALLPAPER`: Standard launcher hardware/system integration.

---

## 4. Build, Test & Installation Instructions

### Build Debug & Release APKs
```bash
gradle assembleDebug
gradle assembleRelease
```
- **Debug APK Output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK Output**: `app/build/outputs/apk/release/app-release.apk`

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Install & Set as Default Home Screen
1. Install `app-debug.apk` on your Android device (Android 8.0 / API 26+).
2. Press the device **Home** button or tap **"Set PixelCategory as your default Home Launcher"** in the Pixel At-a-Glance header / Settings sheet to grant `RoleManager.ROLE_HOME`.
