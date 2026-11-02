package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

data class DiscoveredPackageMetadata(
    val packageName: String,
    val activityName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isDisabled: Boolean,
    val androidCategoryHint: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val launchIntent: Intent
)

/**
 * Discovers all launchable applications installed on the device using Android's
 * LauncherApps and PackageManager APIs, manages an in-memory LRU icon cache,
 * and listens for incremental package install/update/remove events.
 */
class AppDiscoveryEngine(
    private val context: Context,
    private val iconPackManager: IconPackManager
) {
    private val packageManager: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    // Cache up to 220 app icons in memory for 120 Hz scrolling smoothness
    private val iconCache = object : LruCache<String, ImageBitmap>(220) {}

    private var launcherCallback: LauncherApps.Callback? = null

    fun discoverLaunchableApps(): List<DiscoveredPackageMetadata> {
        val discovered = LinkedHashMap<String, DiscoveredPackageMetadata>()
        val ownPackage = context.packageName

        // 1. Primary path: LauncherApps API (standard for HOME launchers)
        try {
            val profiles = launcherApps?.profiles ?: listOf(Process.myUserHandle())
            for (profile in profiles) {
                val activities = launcherApps?.getActivityList(null, profile).orEmpty()
                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == ownPackage) continue
                    val comp = info.componentName
                    val appInfo = info.applicationInfo
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val catHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appInfo.category
                    } else {
                        -1
                    }
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = comp
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    discovered[pkg] = DiscoveredPackageMetadata(
                        packageName = pkg,
                        activityName = comp.className,
                        appName = info.label?.toString()?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.'),
                        isSystemApp = isSys,
                        isDisabled = !appInfo.enabled,
                        androidCategoryHint = catHint,
                        firstInstallTime = info.firstInstallTime,
                        lastUpdateTime = info.firstInstallTime,
                        launchIntent = launchIntent
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to PackageManager queryIntentActivities
        }

        // 2. Fallback / Supplement path: PackageManager.queryIntentActivities
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(mainIntent, 0)
            }

            for (ri in resolveInfos) {
                val actInfo = ri.activityInfo ?: continue
                val pkg = actInfo.packageName ?: continue
                if (pkg == ownPackage || discovered.containsKey(pkg)) continue
                val appInfo = actInfo.applicationInfo
                val isSys = appInfo != null && (
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    )
                val catHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
                    appInfo.category
                } else {
                    -1
                }
                val label = ri.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pkg.substringAfterLast('.')
                val comp = ComponentName(pkg, actInfo.name)
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = comp
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                discovered[pkg] = DiscoveredPackageMetadata(
                    packageName = pkg,
                    activityName = actInfo.name,
                    appName = label,
                    isSystemApp = isSys,
                    isDisabled = appInfo != null && !appInfo.enabled,
                    androidCategoryHint = catHint,
                    firstInstallTime = System.currentTimeMillis(),
                    lastUpdateTime = System.currentTimeMillis(),
                    launchIntent = launchIntent
                )
            }
        } catch (_: Exception) {
            // Graceful handling if restricted
        }

        return discovered.values.sortedBy { it.appName.lowercase() }
    }

    fun inspectSinglePackage(packageName: String): DiscoveredPackageMetadata? {
        if (packageName == context.packageName) return null
        return try {
            val activities = launcherApps?.getActivityList(packageName, Process.myUserHandle()).orEmpty()
            val first = activities.firstOrNull()
            if (first != null) {
                val appInfo = first.applicationInfo
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val catHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else -1
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = first.componentName
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                DiscoveredPackageMetadata(
                    packageName = packageName,
                    activityName = first.componentName.className,
                    appName = first.label?.toString()?.takeIf { it.isNotBlank() } ?: packageName,
                    isSystemApp = isSys,
                    isDisabled = !appInfo.enabled,
                    androidCategoryHint = catHint,
                    firstInstallTime = first.firstInstallTime,
                    lastUpdateTime = System.currentTimeMillis(),
                    launchIntent = launchIntent
                )
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                } ?: return null
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val catHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else -1
                DiscoveredPackageMetadata(
                    packageName = packageName,
                    activityName = launchIntent.component?.className.orEmpty(),
                    appName = label,
                    isSystemApp = isSys,
                    isDisabled = !appInfo.enabled,
                    androidCategoryHint = catHint,
                    firstInstallTime = System.currentTimeMillis(),
                    lastUpdateTime = System.currentTimeMillis(),
                    launchIntent = launchIntent
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getOrLoadAppIcon(
        packageName: String,
        appName: String,
        activeIconPack: String = "",
        customIconKey: String? = null
    ): ImageBitmap {
        val cacheKey = "$packageName|$activeIconPack|${customIconKey.orEmpty()}"
        iconCache.get(cacheKey)?.let { return it }

        val bitmap = loadIconBitmapInternal(packageName, appName, activeIconPack, customIconKey)
        val imageBitmap = bitmap.asImageBitmap()
        iconCache.put(cacheKey, imageBitmap)
        return imageBitmap
    }

    fun clearIconCache() {
        iconCache.evictAll()
    }

    private fun loadIconBitmapInternal(
        packageName: String,
        appName: String,
        activeIconPack: String,
        customIconKey: String?
    ): Bitmap {
        // 1. Check custom icon override or active icon pack drawable first
        val packDrawable = iconPackManager.loadIconForPackage(
            packageName = packageName,
            activeIconPack = activeIconPack,
            customOverrideKey = customIconKey
        )
        if (packDrawable != null) {
            return drawableToBitmap(packDrawable, 144)
        }

        // 2. Load native application icon from PackageManager
        try {
            val rawDrawable = packageManager.getApplicationIcon(packageName)
            val baseBitmap = drawableToBitmap(rawDrawable, 144)
            return iconPackManager.applyBuiltInPackFilterIfSelected(baseBitmap, activeIconPack)
        } catch (_: Exception) {
            // 3. Fallback generated monogram icon if package icon is missing
            return generateFallbackMonogramBitmap(packageName, appName, 144)
        }
    }

    private fun drawableToBitmap(drawable: Drawable, targetSizePx: Int): Bitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: targetSizePx
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: targetSizePx
        val size = minOf(maxOf(width, height), targetSizePx)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun generateFallbackMonogramBitmap(packageName: String, appName: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = intArrayOf(
            0xFF2563EB.toInt(),
            0xFF059669.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDB2777.toInt(),
            0xFFD97706.toInt(),
            0xFF0891B2.toInt()
        )
        val bgColor = palette[abs(packageName.hashCode()) % palette.size]
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
            sizePx * 0.28f,
            sizePx * 0.28f,
            bgPaint
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = sizePx * 0.44f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initial = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        val yPos = (sizePx / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, sizePx / 2f, yPos, textPaint)
        return bitmap
    }

    fun launchApp(packageName: String, activityName: String = ""): Boolean {
        return try {
            if (activityName.isNotBlank() && launcherApps != null) {
                val comp = ComponentName(packageName, activityName)
                launcherApps.startMainActivity(comp, Process.myUserHandle(), null, null)
                true
            } else {
                val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                } ?: return false
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            try {
                val fallback = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } ?: return false
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun registerPackageChangeListener(
        onPackageAddedOrUpdated: (String) -> Unit,
        onPackageRemoved: (String) -> Unit
    ) {
        if (launcherCallback != null) return
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                iconCache.evictAll()
                onPackageRemoved(packageName)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                iconCache.evictAll()
                onPackageAddedOrUpdated(packageName)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                iconCache.evictAll()
                onPackageAddedOrUpdated(packageName)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                iconCache.evictAll()
                packageNames.forEach { onPackageAddedOrUpdated(it) }
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                if (!replacing) {
                    packageNames.forEach { onPackageRemoved(it) }
                }
            }
        }
        try {
            launcherApps?.registerCallback(callback)
            launcherCallback = callback
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    fun unregisterPackageChangeListener() {
        try {
            launcherCallback?.let { launcherApps?.unregisterCallback(it) }
            launcherCallback = null
        } catch (_: Exception) {
        }
    }
}
