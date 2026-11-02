package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.example.domain.model.IconPackInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Extensible Icon Pack Engine supporting ADW, Nova, and Lawnchair/Apex
 * `appfilter.xml` icon pack protocols, built-in Pixel Material You & Monochrome
 * shader packs, and per-app manual icon overrides.
 */
class IconPackManager(private val context: Context) {

    companion object {
        const val BUILTIN_PIXEL_THEMED = "builtin.pixel.material_tinted"
        const val BUILTIN_FROSTED_MONO = "builtin.pixel.frosted_mono"

        private val ICON_PACK_INTENTS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
    }

    private val packageManager: PackageManager = context.packageManager

    // Maps iconPackPackage -> (targetPackageName -> drawableName)
    private val parsedAppFilters = ConcurrentHashMap<String, Map<String, String>>()

    fun discoverInstalledIconPacks(activeIconPack: String): List<IconPackInfo> {
        val packs = LinkedHashMap<String, IconPackInfo>()

        // Always offer the System Default + 2 verified built-in shader icon packs
        packs[""] = IconPackInfo(
            packageName = "",
            name = "System Adaptive Icons (Default)",
            iconCount = 0,
            isApplied = activeIconPack.isEmpty()
        )
        packs[BUILTIN_PIXEL_THEMED] = IconPackInfo(
            packageName = BUILTIN_PIXEL_THEMED,
            name = "Pixel Material Tinted Pack",
            iconCount = 500,
            isApplied = activeIconPack == BUILTIN_PIXEL_THEMED
        )
        packs[BUILTIN_FROSTED_MONO] = IconPackInfo(
            packageName = BUILTIN_FROSTED_MONO,
            name = "Frosted Monochrome Glass Pack",
            iconCount = 500,
            isApplied = activeIconPack == BUILTIN_FROSTED_MONO
        )

        // Scan device for external ADW / Nova / Apex compatible icon packs
        for (action in ICON_PACK_INTENTS) {
            try {
                val intent = Intent(action)
                @Suppress("DEPRECATION")
                val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (packs.containsKey(pkg)) continue
                    val label = ri.loadLabel(packageManager)?.toString() ?: pkg
                    val mapping = getOrParseIconPackMapping(pkg)
                    packs[pkg] = IconPackInfo(
                        packageName = pkg,
                        name = label,
                        iconCount = mapping.size,
                        isApplied = activeIconPack == pkg
                    )
                }
            } catch (_: Exception) {
            }
        }

        return packs.values.toList()
    }

    fun getOrParseIconPackMapping(iconPackPackage: String): Map<String, String> {
        if (iconPackPackage.isBlank() || iconPackPackage.startsWith("builtin.")) {
            return emptyMap()
        }
        parsedAppFilters[iconPackPackage]?.let { return it }

        val mapping = mutableMapOf<String, String>()
        try {
            val packResources = packageManager.getResourcesForApplication(iconPackPackage)
            val xmlId = packResources.getIdentifier("appfilter", "xml", iconPackPackage)
            if (xmlId != 0) {
                val parser = packResources.getXml(xmlId)
                parseAppFilterXml(parser, mapping)
            } else {
                packResources.assets.open("appfilter.xml").use { input ->
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(input, "UTF-8")
                    parseAppFilterXml(parser, mapping)
                }
            }
        } catch (_: Exception) {
            // Icon pack may not include standard appfilter.xml
        }
        parsedAppFilters[iconPackPackage] = mapping
        return mapping
    }

    /**
     * Parses `<item component="ComponentInfo{com.package/com.package.MainActivity}" drawable="icon_name" />`
     * entries in standard ADW / Nova `appfilter.xml`.
     */
    internal fun parseAppFilterXml(parser: XmlPullParser, outMap: MutableMap<String, String>) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val componentAttr = parser.getAttributeValue(null, "component")
                val drawableAttr = parser.getAttributeValue(null, "drawable")
                if (!componentAttr.isNullOrBlank() && !drawableAttr.isNullOrBlank()) {
                    val pkg = extractPackageFromComponentInfo(componentAttr)
                    if (pkg.isNotEmpty()) {
                        outMap.putIfAbsent(pkg, drawableAttr)
                    }
                }
            }
            eventType = parser.next()
        }
    }

    fun extractPackageFromComponentInfo(componentInfo: String): String {
        val start = componentInfo.indexOf('{')
        val slash = componentInfo.indexOf('/')
        return if (start != -1 && slash > start + 1) {
            componentInfo.substring(start + 1, slash).trim()
        } else {
            ""
        }
    }

    fun loadIconForPackage(
        packageName: String,
        activeIconPack: String,
        customOverrideKey: String?
    ): Drawable? {
        if (activeIconPack.isBlank() || activeIconPack.startsWith("builtin.")) return null
        return try {
            val packResources: Resources = packageManager.getResourcesForApplication(activeIconPack)
            val mapping = getOrParseIconPackMapping(activeIconPack)
            val drawableName = customOverrideKey?.takeIf { it.isNotBlank() } ?: mapping[packageName] ?: return null
            val resId = packResources.getIdentifier(drawableName, "drawable", activeIconPack)
            if (resId != 0) {
                ResourcesCompat.getDrawable(packResources, resId, null)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun applyBuiltInPackFilterIfSelected(source: Bitmap, activeIconPack: String): Bitmap {
        if (activeIconPack != BUILTIN_PIXEL_THEMED && activeIconPack != BUILTIN_FROSTED_MONO) {
            return source
        }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (activeIconPack == BUILTIN_FROSTED_MONO) {
            val matrix = ColorMatrix().apply { setSaturation(0.08f) }
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        } else if (activeIconPack == BUILTIN_PIXEL_THEMED) {
            // Subtle cool Pixel Material You blue-teal tint matrix
            val matrix = ColorMatrix(
                floatArrayOf(
                    0.82f, 0.08f, 0.10f, 0f, 12f,
                    0.06f, 0.88f, 0.12f, 0f, 18f,
                    0.10f, 0.14f, 0.95f, 0f, 28f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
