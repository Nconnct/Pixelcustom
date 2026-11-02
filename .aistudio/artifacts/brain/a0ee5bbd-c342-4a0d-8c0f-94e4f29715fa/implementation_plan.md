# Home Screen Drag-and-Drop, Folder Resizing & Full-Screen Borderless Opened Folder Plan

## 1. Reliable Hold-and-Swipe Drag-and-Drop Across the Home Screen & Dock
- **Root Cause & Fix for Gesture Conflicts**:
  - Previously, child tiles (`LauncherAppTile`, `LauncherFolderTile`, and `FolderInteractiveAppIcon`) attached `.combinedClickable(onLongClick = ...)` modifiers that consumed long-press pointer events before the parent grid cell's `detectDragGesturesAfterLongPress` could begin tracking finger movement.
  - Additionally, the root vertical swipe detector (`PointerEventPass.Initial`) could intercept vertical drag movement if the drag state was not synchronized via `rememberUpdatedState`, and fallback items on Pages 2/3 had negative IDs that prevented saving their dragged coordinates.
- **Unified Pointer & Drag Architecture (`HomeScreenContent.kt` & `FrostedGlassComponents.kt`)**:
  - Replace competing child `onLongClick` handlers on Home Screen grid and Dock items with a unified gesture pipeline so holding and swiping any app icon or folder immediately lifts a floating preview (`1.08x` scale) under your finger.
  - Ensure all items across Pages 1, 2, and 3 are backed by persisted database records (or automatically materialized on drag) so any app or folder can be freely placed at **any `(row, column)` coordinate** on the 5×6 grid without snapping back.
  - Support dropping an app onto any empty cell, swapping with an existing item, dragging between the 5×6 grid and the 4-icon Dock, edge-scrolling across pages, or dropping an app onto another app/folder center to merge into a folder.

## 2. Folder Resizing via Floating Quick-Action Pill (`1×1`, `2×2 Bento`, `2×2 Dense 3×3`)
- **Stationary Long-Press Floating Pill**:
  - Holding a folder without dragging opens a sleek, borderless frosted glass **Floating Folder Action Pill** directly above/below the folder on the Home Screen (while starting to swipe immediately drags the folder instead).
  - The floating pill lets you switch the folder size and layout with one tap:
    - **1×1 Compact** (`spanX = 1, spanY = 1` regular circular/squircle folder icon)
    - **2×2 Bento** (`spanX = 2, spanY = 2` with 3 large direct-launch icons + bottom-right 2×2 mini-grid)
    - **2×2 Dense 3×3** (`spanX = 2, spanY = 2` with up to 8 medium direct-launch icons + 9th-slot 2×2 mini-grid)
  - Also includes quick actions to **Rename**, **Add Apps**, or **Remove Folder**.
- **Repository & Grid Span Persistence (`LauncherRepository.kt` & `LauncherViewModel.kt`)**:
  - Add `resizeAndStyleFolder(folderId: Long, spanX: Int, spanY: Int, displayStyle: FolderDisplayStyle)` to update both the folder's `HomeScreenItemEntity` (`spanX`, `spanY`) and `FolderEntity` (`colorHex` style suffix), automatically resolving any grid cell collisions when expanding from `1×1` to `2×2`.
  - Remove the `coerceAtLeast(2)` constraint in `HomeScreenContent.kt` so folders with `spanX = 1, spanY = 1` render at `1×1` size on the 5-column grid.

## 3. Full-Screen Borderless Opened Folder View (Matching Reference Screenshot)
- **Borderless Full-Screen Wallpaper Scrim (`FolderDetailModal` in `HomeScreenContent.kt`)**:
  - Replace the boxed dialog card and header action buttons with a full-screen borderless overlay over the dimmed/blurred Home wallpaper, matching your `"Tools"` folder screenshot.
  - Display the folder title (e.g., `"Tools"`) in large, clean white typography (`32sp`, left-aligned above the first column of icons).
  - Render member apps in a spacious **3-column floating grid** of large circular icons (`60.dp`) with generous spacing and **zero app text labels** or surrounding container borders.
  - Tapping anywhere on the empty wallpaper area outside the icons smoothly closes the folder.
- **Tap Title to Rename & Drag Icons Out to Remove**:
  - **Tap Folder Title**: Tapping the `"Tools"` title switches it inline into a borderless white text input to rename the folder (plus an optional subtle `+` chip next to the title while editing to add apps).
  - **Hold & Drag App Out of Folder**: Holding and dragging any app icon inside the opened folder beyond the folder grid area closes the folder view and drops that app directly onto the Home Screen as an independent shortcut (removing it from the folder).
