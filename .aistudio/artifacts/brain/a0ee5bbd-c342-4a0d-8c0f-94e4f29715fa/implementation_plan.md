# Expanded 2×2 Bento/3×3 Folders & Real-Time Hold-and-Swipe Drag-and-Drop Plan

## 1. Visual & Functional Goals (Matching Your Reference Screenshot)

1. **5-Column Label-Free Home Screen Layout (`5 × 7` Grid)**
   - Update the default Home Screen grid to **5 columns** and **7 rows** with **app/folder text labels hidden (`showHomeIconLabels = false`)** so the Home Screen replicates the clean, label-free layout in your screenshot:
     - **Left column (`col = 0`)**: Single 1×1 circular app shortcuts stacked vertically.
     - **Middle & Right columns (`cols 1..2` and `cols 3..4`)**: Pairs of expanded **2×2 frosted glass folders** (`spanX = 2, spanY = 2`) side-by-side, plus additional 1×1 app icons below them.
     - **Compact Frosted Search Pill**: Centered `Search` pill above the borderless bottom dock.

2. **Expanded 2×2 Home Screen Folders (`2×2 Bento` & `3×3 Dense` Modes)**
   - Upgrade `LauncherFolderTile` in `FrostedGlassComponents.kt` to render as a borderless frosted glass card (`RoundedCornerShape(26.dp)`) spanning **2×2 grid cells** (`spanX = 2, spanY = 2`) with zero text labels:
     - **2×2 Bento Style ("3 Large + 2×2 Mini Grid")**:
       - Used for folders with up to 7 apps (or when toggled to `2x2 Bento` in the folder popup):
       - **Top-Left, Top-Right, Bottom-Left quadrants**: 3 full-size circular app icons that launch the respective app **directly on tap**.
       - **Bottom-Right quadrant**: A **2×2 mini-grid** showing up to 4 mini app icons and subtle translucent rounded-square placeholders (`Color.White.copy(alpha = 0.22f)`) for remaining empty slots, exactly as shown in your screenshot. Tapping the bottom-right mini-grid opens the full `FolderDetailModal`.
     - **3×3 Dense Style ("8 Medium + 2×2 Mini Grid")**:
       - Used automatically for larger folders (8+ apps) or when toggled to `3x3 Dense` in the folder popup:
       - **First 8 slots (`0..7`) of the 3×3 grid**: Medium circular app icons that launch the respective app **directly on tap**.
       - **9th slot (bottom-right)**: A **2×2 mini-cluster** of 4 tiny app icons that opens the full `FolderDetailModal` when tapped.
     - **Folder Style Switcher**: Inside `FolderDetailModal`, add a quick pill toggle (`Auto` / `2×2 Bento` / `3×3 Dense`) so you can also switch any individual folder's display mode at any time.

3. **Real-Time Hold-and-Swipe Drag-and-Drop (`pointerInput` Gesture)**
   - Replace the static two-step move dialog with interactive **touch-hold-and-swipe dragging** directly on the Home Screen:
     - **Hold & Lift**: Long-pressing any app icon or 2×2 folder triggers haptic feedback and lifts a floating drag preview that tracks your finger smoothly across the screen.
     - **Quick Action Pill While Holding**: If you long-press and release without dragging, show the context menu / folder modal; as soon as your finger moves past touch slop, enter live drag mode with a highlighted drop target cell (or 2×2 footprint for folders) on the grid or dock.
     - **Edge Page Auto-Scroll**: Holding a dragged item near the left or right screen edge automatically scrolls `HorizontalPager` to the previous/next Home Screen page so you can drag items across pages.
     - **Drop Behaviors**:
       - **Empty Grid Cell(s) or Dock Slot**: Places the app or 2×2 folder directly at the exact `(pageIndex, row, column)` coordinate you dropped it on.
       - **Drop App onto Another App**: Merges both apps into a new expanded 2×2 folder (or swaps positions if dropped near the cell edge).
       - **Drop App onto an Existing Folder**: Adds the dragged app directly into that folder.
       - **Drop Folder onto Occupied Cells**: Swaps/shifts the underlying items to free the 2×2 block so the folder lands cleanly where you placed it.

---

## 2. Files to Modify

- **`/app/src/main/java/com/example/data/local/LauncherEntities.kt` & `/app/src/main/java/com/example/data/repository/LauncherRepository.kt`**
  - Update default `LauncherSettingsEntity` to `homeGridColumns = 5`, `homeGridRows = 7`, `showHomeIconLabels = false`, and migrate existing settings on startup if not customized so the 5-column label-free layout takes effect immediately.
  - Add repository operations for:
    - `dropOrSwapHomeItem(...)`: Free placement of 1×1 apps and 2×2 folders at any `(pageIndex, row, column)` or dock slot, automatically relocating overlapping items.
    - `mergeAppsIntoFolderOnDrop(draggedItemId, targetItemId)` and `addAppItemToFolderOnDrop(draggedItemId, targetFolderId)`.
- **`/app/src/main/java/com/example/ai/AiHomeOrganizer.kt`**
  - Update `AI_ORGANIZED` layout generation to arrange Page 1 (`pageIndex = 0`) matching the screenshot: single 1×1 favorite/essential apps down column `0`, and 2×2 expanded folders (`spanX = 2, spanY = 2`) in columns `1..2` and `3..4`, plus remaining 1×1 apps on the bottom row and across Pages 2 & 3.
- **`/app/src/main/java/com/example/ui/components/FrostedGlassComponents.kt`**
  - Implement `ExpandedLauncherFolderTile` inside `LauncherFolderTile` supporting both **2×2 Bento** (3 large direct-launch icons + bottom-right 2×2 mini-grid with translucent empty slot placeholders) and **3×3 Dense** (8 direct-launch icons + bottom-right 2×2 mini-cluster).
  - Update `PixelBottomSearchBar` to also support the compact centered `Search` pill look from the screenshot above the borderless dock.
- **`/app/src/main/java/com/example/ui/screens/HomeScreenContent.kt` & `/app/src/main/java/com/example/ui/viewmodel/LauncherViewModel.kt`**
  - Render the 2D multi-span grid (`BoxWithConstraints` coordinate system) so 1×1 apps and 2×2 folders sit at their exact `(row, column, spanX, spanY)` positions without overlapping.
  - Implement live hold-and-swipe drag-and-drop (`detectDragGesturesAfterLongPress`), floating drag shadow overlay, live target cell/merge highlight, and cross-page edge scrolling.

---

## 3. Verification Plan
- Run `compile_applet` to verify zero Kotlin or Compose compilation errors.
- Run unit tests (`gradle :app:testDebugUnitTest`) to verify 5-column multi-span folder layout generation, drag-and-drop placement, swap, and drop-to-merge folder logic.
