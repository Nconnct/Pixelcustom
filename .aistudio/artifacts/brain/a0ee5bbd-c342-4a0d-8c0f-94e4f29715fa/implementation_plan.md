# App Overview & Objectives

This update refines the **Pixel AI Launcher** Home Screen experience across three core areas:

1. **Reliable Vertical Swipe Gestures**: Restore responsive vertical swipe-up (to open the App Drawer) and swipe-down (to open Unified Search) across the entire Home Screen without interference from nested scroll containers or horizontal paging.
2. **Uniform Circular Frosted Glass Folders**: Replace variable-width rectangular folder cards with clean, uniform circular frosted glass folder icons matching the exact dimensions and alignment of standard app icons.
3. **3-Page Horizontal Home Screen Paging**: Enable smooth left/right horizontal paging across 3 Home Screen pages—organizing ML smart category folders on Page 1 and extra/individual apps across Pages 2 and 3—complete with interactive frosted glass page indicator dots above the dock.

---

# Confirmed User Decisions

- **Home Screen Folder Shape & Layout**: Circular frosted glass folders matching the standard app icon size (`CircleShape` container with a 2x2 mini-icon preview grid, top-right unread notification count badge, and centered single-line label aligned to the Home Screen grid).
- **Horizontal Home Screen Paging Structure**: 3 horizontal pages where Page 1 hosts the primary ML-categorized smart folders and Pages 2 and 3 host extra and frequently used individual apps in a structured row-by-column grid.

---

# Technical Architecture & Gesture Routing

The Home Screen coordinates two orthogonal gesture axes alongside tile tap/long-press interactions:

```
+-------------------------------------------------------------------+
|                   Home Screen Touch Coordinator                   |
|                                                                   |
|   Vertical Drag (|dy| > |dx| & threshold > 42dp)                  |
|     ├── Swipe Up   (dy < -42dp) ──> Haptic + Open App Drawer      |
|     └── Swipe Down (dy > +42dp) ──> Haptic + Open Unified Search  |
|                                                                   |
|   Horizontal Drag (|dx| > |dy|)                                   |
|     └── Delegated to 3-Page Horizontal Pager (Page 1 <─> 2 <─> 3) |
+-------------------------------------------------------------------+
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
+---------------+        +---------------+        +---------------+
|    Page 1     |        |    Page 2     |        |    Page 3     |
| Smart Folders |        |  Extra Apps   |        |  Extra Apps   |
| (Circular 2x2 |        |  (Structured  |        |  (Structured  |
| Frosted Icons)|        |   App Grid)   |        |   App Grid)   |
+---------------+        +---------------+        +---------------+
        └────────────────────────┼────────────────────────┘
                                 ▼
+-------------------------------------------------------------------+
|         Interactive Frosted Glass Page Indicator (● ○ ○)          |
+-------------------------------------------------------------------+
|              Borderless Frosted Glass 4-App Dock                  |
+-------------------------------------------------------------------+
```

### 1. Vertical Swipe Gesture Fix (App Drawer & Unified Search)
- **Root Cause Resolution**: Remove the vertical scroll modifier from the Home Screen page container, which previously intercepted and consumed all vertical drag deltas before the Home Screen gesture detector could receive them.
- **Orthogonal Axis Locking**: Implement a clean vertical drag gesture pass on the Home Screen workspace that evaluates vertical vs. horizontal displacement (`abs(dy) > abs(dx)`). When the user swipes vertically past the activation threshold, the launcher triggers haptic feedback and immediately opens the App Drawer (swipe up) or Unified Search (swipe down), while horizontal drags flow uninterrupted to the horizontal pager.

### 2. Uniform Circular Frosted Glass Folders
- **Exact App Icon Proportions**: Update the folder tile component to use the exact same circular diameter (`iconSizeDp * iconScale`) and outer cell footprint (`76dp` width) as individual app tiles so folders and apps sit in crisp, uniform rows and columns.
- **2x2 Mini-Icon Matrix**: Render up to 4 app icons in a balanced 2x2 miniature grid inside the circular frosted glass surface with subtle glass refraction, specular rim highlight, top-right unread notification count pill, and a single-line centered title below the circle.
- **Expanded Folder Modal**: Tapping any circular folder smoothly opens the frosted glass folder sheet with full app launch and "+ Add Apps" management capabilities.

### 3. 3-Page Horizontal Home Screen & Smart Organization
- **3-Page Workspace**: Configure the Home Screen pager to maintain at least 3 horizontal pages (`Page 1`, `Page 2`, `Page 3`) with physics-based horizontal swipe snapping.
- **Structured Grid Layout**: Render each page using a consistent `gridRows × gridColumns` matrix rather than an unstructured wrapping flow, supporting both populated cells and empty long-press areas.
- **Smart Multi-Page Distribution**:
  - **Page 1 (`pageIndex = 0`)**: Displays the ML-categorized smart folders (e.g., Productivity, Social, Finance, Media, Utilities) in clean circular frosted glass icons.
  - **Pages 2 & 3 (`pageIndex = 1`, `pageIndex = 2`)**: Distributes extra and high-priority individual apps across Pages 2 and 3 so users can swipe left and right between pages just like a standard Android launcher.
- **Interactive Page Indicator**: Display a frosted glass pill indicator above the dock showing active page progress (`1..3`) with animated capsule expansion on the current page and tap-to-jump support.

---

# Verification Plan

1. **Gesture Verification**: Confirm swiping up anywhere on Page 1, Page 2, or Page 3 opens the App Drawer with haptic feedback, swiping down opens Unified Search, and swiping left/right transitions smoothly between pages.
2. **Visual & Grid Alignment**: Confirm all Home Screen folders render as uniform circular frosted glass icons matching app icon dimensions and align cleanly in the Home Screen grid.
3. **Build & Unit Test Suite**: Compile the application and run the unit test suite to ensure all layout organization, database persistence, and UI state transitions pass with zero errors.
