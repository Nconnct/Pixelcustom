# PixelCategory Launcher

PixelCategory Launcher is a privacy-first, daily-driver Android home screen replacement that blends the clean aesthetic of the Google Pixel Launcher with an on-device AI categorization engine, intelligent layout auto-arrangement, unified device search, and frosted-glass customization—all functioning 100% offline with zero telemetry or cloud dependencies.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices have been confirmed from your answers and aligned with Android platform best practices:

- **Confirmed Decision 1 — Hybrid On-Device AI Classification Pipeline**: We will implement a multi-stage offline categorization engine combining a curated local package/metadata knowledge base, feature extraction over app labels/package tokens/system flags, an embedded TensorFlow Lite (LiteRT) flatbuffer classifier with a calibrated TF-IDF / Naive Bayes statistical ensemble fallback, confidence scoring, and persistent user override memory in Room.
- **Confirmed Decision 2 — Pixel-Inspired Default Home Screen Layout**: Out of the box, the launcher presents a signature Pixel At-a-Glance header widget (date, weather/calendar status, quick contextual info), a multi-page customizable drag-and-drop workspace grid with folders and live `AppWidgetHost` support, a pinned frosted dock, and a bottom Google-style unified search bar pill.
- **Confirmed Decision 3 — Unified Multi-Source Search**: The launcher search interface queries installed applications (fuzzy + token matching), folders, device contacts (via `ContactsContract` with runtime permission gating and direct call/SMS shortcuts), local documents/media (via `MediaStore` and Storage Access Framework), and configurable web search with embedded `WebView` preview and offline state awareness.
- **Open Choice / Recommended Default — Package Visibility & Role Management**: To discover all launchable apps and act as a system home replacement on Android 11+, the launcher declares `CATEGORY_HOME`, `CATEGORY_DEFAULT`, and `<queries>` for `ACTION_MAIN` + `CATEGORY_LAUNCHER` alongside `QUERY_ALL_PACKAGES` for full launcher discovery, plus `RoleManager.ROLE_HOME` prompting so users can set PixelCategory Launcher as their default home screen with one tap.

---

### 1. Overview & Core Concept

- **What It Does**: Replaces the stock Android home screen with a responsive, 120 Hz-capable launcher featuring multi-page workspace grids, folders, system widget hosting (`AppWidgetHost`), automatic and manual 18-category app organization, one-tap AI home screen auto-arrangement with interactive preview and undo/restore, unified local + web search, ADW/Nova icon pack parsing, and granular visual customization.
- **Target Audience / Persona**: Android power users and privacy-conscious daily drivers who love the clean ergonomics of the Pixel Launcher but want intelligent, zero-cloud app organization, frosted glass aesthetics, and deep grid/icon customization.
- **Key Value**: Eliminates home screen and app drawer clutter automatically using local on-device intelligence while guaranteeing that no installed app lists, contacts, files, or usage patterns ever leave the device.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home Screen & Daily Navigation**: Users land on the main workspace featuring the Pixel At-a-Glance widget, customizable app/folder grid, pinned dock, and bottom search bar. Swiping horizontally navigates workspace pages; swiping up smoothly reveals the frosted-glass categorized App Drawer; swiping down opens notifications or unified search based on gesture settings.
  2. **App Drawer & Category Management**: The App Drawer displays sticky frosted category tabs (*All Apps, Browsers, Internet, Games, Video & Streaming, Social, Messaging, Banking, UPI & Payments, Shopping, Productivity, Education, Photography, Music & Audio, Technology & Development, Utilities, System Apps, Uncategorized*, plus custom user categories). Each app badge can display its AI confidence indicator or manual override tag. Long-pressing an app opens a contextual sheet to reassign categories, pin to home/dock, customize its icon, or inspect app info.
  3. **AI Auto-Arrangement & Layout Preview**: From the Home long-press menu or Launcher Settings, users can switch between *Pixel Default*, *AI Organized*, and *Custom Layout*. Selecting *AI Organized* computes an optimal arrangement (grouping high-affinity categories into smart folders, prioritizing favorites in the dock, preserving existing widgets and pinned shortcuts) and opens an interactive side-by-side **Layout Preview Modal**. Users can approve, tweak, or hit **Undo / Restore Previous Layout** at any time.
  4. **Unified Search & Embedded Web View**: Tapping the bottom search pill or swiping opens the Unified Search overlay with instant categorized sections for Apps, Folders, Contacts (with one-tap Call/SMS buttons), Local Files, Search History, and Web Search (rendering in a secure sandboxed in-launcher `WebView` when online, or displaying a clear offline banner when disconnected).
  5. **Customization, Icon Packs & Privacy Center**: A comprehensive Settings & Customization sheet lets users adjust home/drawer grid rows and columns, icon scaling and shapes (Circle, Squircle, Rounded Square, Teardrop), frosted blur strength and surface opacity, theme mode (Light, Dark, System, Wallpaper Dynamic Color), gesture bindings, installed Icon Packs, local JSON/DB backup & restore, and a transparent Privacy Dashboard explaining all optional permissions.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Refined Pixel Material You minimalism elevated with translucent frosted-glass depth (`RenderEffect.createBlurEffect` on Android 12+ with high-contrast tinted translucent fallback on Android 8–11).
  - *Color Palette & Mood*:
    - **Dynamic Wallpaper Adaptation**: Uses `dynamicDarkColorScheme` / `dynamicLightColorScheme` on Android 12+ (`WallpaperColors` integration) with customizable accent overrides.
    - **Fallback Pixel Palette**: Deep Obsidian Slate (`#0F1419`) and Frosted Charcoal (`#1C222B` at 72% alpha) in Dark Mode; Crisp Porcelain (`#F8FAFC`) and Translucent Mist (`#FFFFFF` at 78% alpha) in Light Mode; Google Blue (`#4285F4` / `#A8C7FA`), Sage Mint (`#34A853` / `#81C995`), and Warm Amber (`#FBBC04`) accents for AI confidence badges and category pills.
  - *Typography & Hierarchy*: Locally bundled Google Fonts (`Plus Jakarta Sans` for crisp geometric headers/At-a-Glance titles and `Inter` for high-legibility app labels and search results, paired with `JetBrains Mono` for AI confidence metrics and package metadata), scaled strictly in `sp` with configurable user font scale.
  - *Component Styling & Layout*: Edge-to-edge full-bleed wallpaper rendering (`enableEdgeToEdge()`), `WindowInsets.safeDrawing` / `navigationBars` respect, 24dp–28dp pill radii for search bars and category chips, 20dp radii for frosted folder popovers, and 48dp minimum touch targets across all icons and actions.
- **Interactive Feedback & Motion**: Physics-based `spring()` animations for app drawer sheet expansion, folder zoom-in/out transitions, drag-and-drop grid reordering with cell highlight previews, and haptic feedback on long-press context menus.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hybrid On-Device AI Classification (LiteRT / TFLite + Local Knowledge Graph + TF-IDF Ensemble)**
  - *Chosen Approach*: A 4-tier offline classification pipeline: (1) User manual override lookup in Room, (2) Curated high-precision package/signature dictionary (~500+ common global & Indian apps including UPI, banking, browsers, social, streaming, utilities), (3) Embedded TFLite character/token n-gram neural classifier & calibrated TF-IDF Naive Bayes statistical model running on extracted package tokens, app label tokens, and `ApplicationInfo.category` metadata, and (4) Confidence threshold gating (apps below 55% confidence route to *Uncategorized* with a one-tap suggested category chip).
  - *Why*: Delivers sub-5ms per-app inference completely offline without requiring multi-hundred-megabyte LLM downloads or internet access, while providing real ML probability distributions, confidence scores, and instant user-correction learning.
  - *Alternatives Considered*: Cloud LLM APIs (rejected due to strict offline and privacy requirements) or pure regex rules (rejected because unknown regional or indie packages need statistical token generalization).

- **Decision 2: Non-Destructive AI Auto-Arrangement with Snapshot Undo Stack**
  - *Chosen Approach*: Before applying any AI-generated home screen layout, the engine serializes the current `HomeScreenPageEntity`, `HomeScreenItemEntity`, and `FolderEntity` state into a local layout snapshot in Room and presents an interactive visual preview.
  - *Why*: Ensures a user's painstakingly customized home screen is never accidentally overwritten and can always be restored with a single tap.
  - *Alternatives Considered*: Live in-place mutation without preview (rejected due to risk of disrupting user muscle memory).

- **Decision 3: Multi-Protocol Icon Pack Engine & Adaptive Icon Shaper**
  - *Chosen Approach*: Queries installed packages matching `org.adw.launcher.THEMES`, `com.novalauncher.THEME`, and `com.teslacoilsw.launcher.THEME`, parses `res/xml/appfilter.xml` and `assets/appfilter.xml` for `ComponentInfo` -> drawable mappings, caches rendered bitmaps in an LRU memory + disk cache, and applies uniform Path-based adaptive shape masks (Circle, Squircle, Rounded Square, Teardrop) to unmatched icons.
  - *Why*: Guarantees visual consistency across all installed apps whether or not an icon pack covers 100% of packages.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         JETPACK COMPOSE UI & LAUNCHER SHELL                       │
│                                                                                   │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌──────────────────────────┐  │
│  │  Pixel Home Screen  │  │ Frosted App Drawer   │  │ Unified Search Overlay   │  │
│  │ • At-a-Glance Header│  │ • 18+ Category Tabs  │  │ • Apps, Folders, History │  │
│  │ • Multi-Page Grid   │  │ • AI Confidence Badge│  │ • Contacts (Call / SMS)  │  │
│  │ • Folders & Dock    │  │ • Sort & Filter Bar  │  │ • MediaStore & SAF Files │  │
│  │ • AppWidgetHostView │  │ • Category Editor    │  │ • Embedded WebView       │  │
│  └──────────┬──────────┘  └──────────┬───────────┘  └────────────┬─────────────┘  │
│             │                        │                           │                │
│  ┌──────────┴────────────────────────┴───────────────────────────┴─────────────┐  │
│  │       AI Auto-Organizer Preview Modal  •  Settings, Themes & Privacy UI     │  │
│  └──────────────────────────────────────┬──────────────────────────────────────┘  │
└─────────────────────────────────────────┼─────────────────────────────────────────┘
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                      VIEWMODEL & DOMAIN USE-CASE LAYER (MVVM)                     │
│                                                                                   │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌──────────────────────────┐  │
│  │  LauncherViewModel  │  │ Category & AI Engine │  │  Search & Customization  │  │
│  │ • Workspace State   │  │ • Hybrid Classifier  │  │ • Multi-Source Search    │  │
│  │ • Drag/Drop & Undo  │  │ • Auto-Layout Solver │  │ • IconPack XML Parser    │  │
│  │ • Widget Allocation │  │ • Override Learner   │  │ • Dynamic Theme & Blur   │  │
│  └──────────┬──────────┘  └──────────┬───────────┘  └────────────┬─────────────┘  │
└─────────────┼────────────────────────┼───────────────────────────┼────────────────┘
              ▼                        ▼                           ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                     DATA, PERSISTENCE & ANDROID SYSTEM LAYER                      │
│                                                                                   │
│  ┌────────────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │   Room Database (SQLite + Mig.)    │  │   Android Platform Integrations     │  │
│  │ • AppEntity & CategoryEntity       │  │ • LauncherApps & PackageManager     │  │
│  │ • AppCategoryCrossRef              │  │ • BroadcastReceiver (Pkg Changes)   │  │
│  │ • HomeScreenPage / Item / Folder   │  │ • AppWidgetManager & AppWidgetHost  │  │
│  │ • AIClassification & UserCorrection│  │ • ContactsContract & MediaStore     │  │
│  │ • LauncherSettings & SearchHistory │  │ • TFLite Asset Interpreter + TF-IDF │  │
│  │ • IconPack & IconOverride          │  │ • Local JSON Backup / Restore       │  │
│  └────────────────────────────────────┘  └─────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State (Room Entities & Relationships)**:
  - `AppEntity`: Stores `packageName` (PK), `activityName`, `label`, `isSystemApp`, `isInstalled`, `isDisabled`, `isFavorite`, `firstInstallTime`, `lastUpdateTime`, `primaryCategoryId`, `aiConfidence`, `isUserOverridden`, and `launchCount`.
  - `CategoryEntity` & `AppCategoryCrossRef`: Stores the 18 default categories plus user-created categories (`id`, `name`, `iconName`, `colorHex`, `sortOrder`, `isVisibleInDrawer`, `isSystemDefault`) and many-to-many app-to-category assignments so an app can belong to multiple categories if desired.
  - `AIClassificationEntity` & `AIUserCorrectionEntity`: Persists model predictions, feature vectors/tokens, confidence scores, and explicit user category corrections so manual overrides always take precedence and train the local token weights.
  - `HomeScreenPageEntity`, `HomeScreenItemEntity`, & `FolderEntity`: Models workspace pages, grid coordinates (`pageIndex`, `row`, `column`, `spanX`, `spanY`), item types (`APP_SHORTCUT`, `FOLDER`, `WIDGET`), `appWidgetId`, dock positions, and layout history snapshots for instant undo/restore.
  - `LauncherSettingsEntity`, `IconPackEntity`, `IconOverrideEntity`, & `SearchHistoryEntity`: Persists all grid dimensions, icon sizes/shapes, frosted glass blur/alpha values, theme mode, gesture mappings, active icon pack component mappings, per-app icon overrides, and local search history.

- **Interactive Component & State Mapping**:
  - **Package Discovery & Live Updates**: On startup and via `LauncherApps.Callback` / package broadcast receiver (`ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_CHANGED`), incremental diffs update `AppEntity` rows without full rescans, run the offline AI classifier only on newly discovered packages, and update the UI `StateFlow` reactively.
  - **Drag-and-Drop, Long-Press & Widget Hosting**: Long-pressing any app on the home screen or drawer opens a context menu to assign categories, add to home/dock, create a folder, or open system App Info. Long-pressing empty workspace space opens the Home Customization sheet, Widget Picker (binding real `AppWidgetProviderInfo` via `AppWidgetHost`), and the **AI Auto-Arrangement Preview**.
  - **Unified Search Execution**: Typing in the search bar debounces input over `StateFlow`, concurrently querying Room/memory for apps and folders, `ContactsContract` (when permission is granted, or showing an inline permission card with privacy rationale), `MediaStore` for local files, and rendering web search suggestions + an optional embedded `WebView` with offline detection.
  - **Backup, Restore & Migrations**: Non-destructive Room schema migrations (`Migration(1, 2)`) preserve user data across updates, complemented by one-tap export/import of complete launcher layouts, categories, and settings to a local JSON file via Storage Access Framework.
