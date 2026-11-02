# Implementation Plan — Dynamic Folder Notification Badges, Live System Wallpaper & Borderless Glass Dock

## Goal
Update **PixelCategory Launcher** with three core enhancements:
1. **Dynamic Notification Badges on ML Folders**: Track active notifications per package using a `NotificationListenerService` (`LauncherNotificationListenerService`) and display a dynamic **unread count number badge** on the top-right corner of each ML-categorized folder on the Home Screen (so users can see unread counts without opening the folder), as well as on individual apps inside the opened folder.
2. **Real System Wallpaper with Frosted Glass Blur**: Configure `Theme.MyApplication` and `MainActivity` to show the user's **actual Android system wallpaper** (`android:windowShowWallpaper="true"`, transparent window background, and `FLAG_SHOW_WALLPAPER`) with real window-level compositor blur (`setBackgroundBlurRadius` / `blurBehindRadius` on Android 12+) and translucent frosted glass overlays instead of hiding the device wallpaper behind an opaque Canvas.
3. **Seamless Borderless Frosted Glass Dock**: Remove the outline border and ensure the 4-app dock above the bottom search bar renders as a full-height, soft, borderless frosted glass dock surface (`heightIn(min = 72.dp)`, `showBorder = false`) so it looks like true frosted glass rather than a line.

---

## Architecture & Planned Changes

### 1. Dynamic Notification Badges (`LauncherNotificationListenerService`)
- **`/app/src/main/java/com/example/engine/LauncherNotificationListenerService.kt` (New)**:
  - Extend Android's `NotificationListenerService` to listen for `onListenerConnected()`, `onNotificationPosted()`, and `onNotificationRemoved()`.
  - Aggregate active non-ongoing notifications per package into a reactive `StateFlow<Map<String, Int>>` (`activePackageCounts`).
  - Provide a helper to check if notification listener access is enabled (`Settings.Secure.getString(contentResolver, "enabled_notification_listeners")`) and an intent helper (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`) to grant access from Launcher Settings or folder prompt.
  - Also fallback to `NotificationManager` / intelligent live notification state so badges update immediately in real time when notifications arrive or are dismissed.
- **`/app/src/main/AndroidManifest.xml`**:
  - Register `com.example.engine.LauncherNotificationListenerService` with permission `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and intent filter `android.service.notification.NotificationListenerService`.
- **`/app/src/main/java/com/example/ui/components/FrostedGlassComponents.kt`**:
  - Update `LauncherFolderTile` to accept `unreadNotificationCount: Int = 0`.
  - When `unreadNotificationCount > 0`, render a crisp, high-contrast pill/circle badge on the **top-right corner of the folder** displaying the total unread count (e.g., `3`, `12`, `99+`) without cluttering the mini app icons inside the folder preview.
  - Update `LauncherAppTile` to accept `notificationCount: Int = 0` so apps inside an opened ML folder or on the Home Screen also show their individual unread count badge on the top-right corner.

### 2. Real Android System Wallpaper & Frosted Glass Blur
- **`/app/src/main/res/values/themes.xml`**:
  - Set `<item name="android:windowShowWallpaper">true</item>`, `<item name="android:windowBackground">@android:color/transparent</item>`, and `<item name="android:windowIsTranslucent">false</item>` on `Theme.MyApplication` so the Android WindowManager composites the user's real device wallpaper directly behind the launcher.
- **`/app/src/main/java/com/example/MainActivity.kt`**:
  - Add `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER` and `ColorDrawable(android.graphics.Color.TRANSPARENT)` in `onCreate`.
  - On Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), dynamically apply `window.setBackgroundBlurRadius(...)` and `window.attributes.blurBehindRadius` when any overlay sheet (`APP_DRAWER`, `UNIFIED_SEARCH`, `AI_ORGANIZER_PREVIEW`, `SETTINGS_CUSTOMIZATION`, `WIDGET_PICKER`) or folder modal is open so the real system wallpaper blurs smoothly behind the frosted glass UI.
  - Make `PixelWallpaperBackground` transparent when `wallpaperPresetId == 0` (the default `"System Live/Static Wallpaper"` mode) so the user's actual device wallpaper is 100% visible, while keeping a subtle frosted glass scrim when overlays open.

### 3. Seamless Borderless Frosted Glass Dock
- **`/app/src/main/java/com/example/ui/components/FrostedGlassComponents.kt`**:
  - Add `showBorder: Boolean = true` parameter to `FrostedGlassBox` so components like the Home Dock can disable the `1.dp` border line.
- **`/app/src/main/java/com/example/ui/screens/HomeScreenContent.kt`**:
  - Update the 4-app Dock (`home_frosted_dock`) above the bottom search bar:
    - Set `showBorder = false` on the dock's `FrostedGlassBox` to eliminate any hard outline/line.
    - Enforce a proper glass dock height (`Modifier.fillMaxWidth().heightIn(min = 74.dp)`) with soft translucent glass fill and inner specular glow so it always renders as a plush frosted glass dock pill containing the 4 dock apps, never collapsing into a thin line even while apps are loading.

---

## Verification Plan
- Run `compile_applet` to ensure zero compilation or manifest errors.
- Run unit tests (`gradle :app:testDebugUnitTest --tests com.example.ExampleUnitTest`) to verify folder notification count aggregation and ML folder layout logic.
