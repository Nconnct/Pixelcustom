# Borderless Frosted Glass UI & Authentic iOS Folders

Deliver a seamless, borderless frosted glass aesthetic across all search bars, buttons, inputs, and sheets—matching the 4-app Home Screen dock—while redesigning folders to an authentic iOS squircle and popup layout where only the folder title text is displayed.

## User Review & Critical Decisions

> [!IMPORTANT]
> Both design questions were confirmed during interactive clarification and are incorporated into this plan:

- **Confirmed Decision 1 — Zero Internal Text in Folders (Only Folder Title Stays)**: Both the Home Screen folder preview and the opened iOS Folder popup will hide all internal app labels, category subtitles, and button text labels so only the folder name is shown.
- **Confirmed Decision 2 — Launcher-Wide Borderless Frosted Glass**: The borderless soft frosted glass treatment used on the 4-app Home Screen dock will be applied globally across the bottom search bar, buttons, chips, text inputs, modals, App Drawer, Unified Search, Glass Browser, and Settings sheets.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates all 1dp outlines, strokes, and borders from buttons, search bars, chips, and glass cards throughout the launcher, replacing them with soft, borderless multi-layer frosted glass surfaces. Transforms folders into authentic iOS frosted squircles (3×3 mini-icon grid) and iOS-style opened folder popups containing only the folder title and app icons.
- **Target Audience / Persona**: Users who want a clean, ultra-modern iOS + Pixel glassmorphic home screen free of visual clutter, hairline borders, or redundant text inside folders.
- **Key Value**: Creates visual harmony between the 4-app dock, bottom search bar, action buttons, and folders while making folders cleaner and faster to scan at a glance.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home Screen & Search Bar**: The bottom search pill, header AI/Settings buttons, page indicator pill, and folder tiles float over the live wallpaper using soft translucent glass without any white or dark border lines.
  2. **iOS Folder Tile on Home Screen**: Each folder on Page 1 renders as a borderless frosted glass iOS squircle (`RoundedCornerShape(percent = 26)`) housing up to 9 mini app icons in a crisp 3×3 grid, with only the folder name below the squircle and the dynamic unread count badge on the top-right corner.
  3. **Opening an iOS Folder**: Tapping a folder opens a centered, borderless frosted glass iOS folder card (`RoundedCornerShape(38.dp)`). Only the folder name is displayed at the top; inside the folder surface, app icons are arranged in a clean 3-column iOS grid with all app text labels, tier subtitles, and button text labels removed (using subtle icon-only controls for adding apps or deleting the folder).
  4. **All Launcher Sheets & Buttons**: Opening the App Drawer, Unified Search, In-Launcher Glass Browser, AI Arrange sheet, or Long-Press menus displays borderless frosted search bars, borderless tonal glass buttons, and borderless filter chips.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Pure Borderless Glassmorphism (iOS Vision/iOS 18 + Pixel Frosted Glass).
  - *Color Palette & Mood*: Translucent surface layers (`frostedSurfaceAlpha` with soft vertical luminance gradients and top specular sheen) over the system wallpaper, completely free of `1.dp` border strokes (`showBorder = false`).
  - *Typography & Hierarchy*: Inside folders, visual hierarchy is simplified to a single bold centered folder header (`titleLarge`), letting app iconography speak for itself without truncated sub-labels.
  - *Component Styling & Layout*:
    - Replace all `OutlinedButton` instances with borderless translucent glass buttons (`FilledTonalButton` / `FrostedGlassBox` with zero border).
    - Configure all `OutlinedTextField` search and form inputs with `Color.Transparent` focused and unfocused borders over a frosted pill background.
    - Configure all `FilterChip` components with `border = null`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Defaulting `FrostedGlassBox` to Borderless (`showBorder = false`)**
  - *Chosen Approach*: Set `showBorder = false` by default on `FrostedGlassBox` and remove explicit `.border(...)` modifiers on badges, search bars, buttons, and injected WebView glass CSS.
  - *Why*: Ensures every glass surface—from the bottom search bar and header buttons to sheets and dialogs—shares the exact same seamless edge treatment as the 4-app Home Screen dock.
  - *Alternatives Considered*: Keeping faint borders on buttons or inputs was rejected because the user explicitly requested removing all borders, lines, and outlines to match the dock.

- **Decision 2: Authentic iOS 3×3 Squircle Folder & Text-Free Opened Folder Popup**
  - *Chosen Approach*: Render Home Screen folders as an iOS continuous squircle with a 3×3 mini-icon grid (showing real app icon bitmaps inside rounded mini-squircles), and render the opened folder modal with the centered folder title at the top and icon-only app tiles (`showLabel = false`) inside.
  - *Why*: Matches iOS folder geometry and directly fulfills the requirement to remove all text inside folders so only the folder name stays.
  - *Alternatives Considered*: Keeping app labels inside the opened popup was passed over per the user's explicit confirmation.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                  Borderless Frosted Glass Design System                   │
│  FrostedGlassBox(showBorder = false) • Borderless Search & Tonal Buttons  │
└───────────────────┬───────────────────────────────────┬───────────────────┘
                    │                                   │
                    ▼                                   ▼
┌───────────────────────────────────────┐ ┌─────────────────────────────────┐
│          Home Screen Surface          │ │   Launcher Overlay Sheets       │
│  • PixelAtAGlanceHeader (No Border)   │ │  • AppDrawerSheet (No Outlines) │
│  • LauncherFolderTile (iOS 3x3 Grid)  │ │  • UnifiedSearch & Glass Web    │
│  • 4-App Frosted Dock (No Border)     │ │  • AI Arrange & Settings Sheets │
│  • PixelBottomSearchBar (No Border)   │ │  • Borderless Chips & Inputs    │
└───────────────────┬───────────────────┘ └─────────────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                   Opened iOS Folder Modal (FolderDetailModal)             │
│  • Centered Folder Title Only (No Tier Subtitle, No Button Text Labels)   │
│  • 3-Column iOS App Icon Grid (showLabel = false, Icon-Only App Tiles)    │
│  • Icon-Only "+" Toggle for Editing Folder Membership & Renaming          │
└───────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

- **Global Glass Surface (`FrostedGlassBox`)**: Updates default `showBorder` parameter to `false` and removes `1.5.dp` dark borders on notification count badges so badges float cleanly with soft elevation.
- **Bottom Search Bar & Search Sheets (`PixelBottomSearchBar`, `AppDrawerSheet`, `UnifiedSearchSheet`)**: Removes all container borders and `OutlinedTextField` border strokes (`focusedBorderColor = Color.Transparent`, `unfocusedBorderColor = Color.Transparent`), as well as CSS `border` rules in the embedded Glass WebView.
- **Buttons & Action Menus (`HomeScreenContent`, `AiPreviewAndSettingsSheets`, `UnifiedSearchSheet`)**: Replaces all `OutlinedButton` and bordered `FilterChip` calls with borderless tonal glass buttons and borderless chips (`border = null`).
- **iOS Folder Tile & Opened Modal (`LauncherFolderTile`, `FolderDetailModal`)**:
  - `LauncherFolderTile`: Uses `RoundedCornerShape(percent = 26)` iOS squircle, 3×3 mini-icon grid of real app icons, no internal text, and only `folder.folderName` underneath.
  - `FolderDetailModal`: Displays `folder.folderName` centered at the top with icon-only action buttons (`+` to edit apps, trash icon to delete) and renders all member apps with `showLabel = false` so zero text appears inside the folder body.
