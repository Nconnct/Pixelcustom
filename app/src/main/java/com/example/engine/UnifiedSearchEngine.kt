package com.example.engine

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.domain.model.AppInfo
import com.example.domain.model.ContactSearchResult
import com.example.domain.model.FolderModel
import com.example.domain.model.LocalFileSearchResult
import com.example.domain.model.WebSearchSuggestion
import java.net.URLEncoder

/**
 * Unified Multi-Source Launcher Search Engine.
 *
 * Ranks and retrieves:
 * A. Applications (exact, prefix, token, package name, and fuzzy subsequence matching)
 * B. Home Screen Folders
 * C. Device Contacts (via ContactsContract when authorized, with zero network upload)
 * D. Accessible Local Files & Documents (via MediaStore)
 * E. Web Search Suggestions & Direct Engine URLs (with live offline detection)
 */
class UnifiedSearchEngine(private val context: Context? = null) {

    fun searchApplications(query: String, apps: List<AppInfo>, limit: Int = 24): List<AppInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps.take(12)

        return apps
            .mapNotNull { app ->
                val score = computeAppMatchScore(q, app)
                if (score > 0) app to score else null
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenByDescending { it.first.isFavorite }
                    .thenByDescending { it.first.launchCount }
                    .thenBy { it.first.appName.lowercase() }
            )
            .take(limit)
            .map { it.first }
    }

    internal fun computeAppMatchScore(query: String, app: AppInfo): Int {
        val nameLower = app.appName.lowercase()
        val pkgLower = app.packageName.lowercase()
        val catLower = app.categoryName.lowercase()

        if (nameLower == query) return 1000
        if (nameLower.startsWith(query)) return 850
        if (nameLower.split(" ").any { it.startsWith(query) }) return 750
        if (nameLower.contains(query)) return 620
        if (catLower.startsWith(query)) return 520
        if (pkgLower.contains(query)) return 460
        if (isSubsequenceMatch(query, nameLower)) return 320
        if (levenshteinDistance(query, nameLower.take(query.length + 1)) <= 1 && query.length >= 3) {
            return 260
        }
        return 0
    }

    private fun isSubsequenceMatch(needle: String, haystack: String): Boolean {
        if (needle.length > haystack.length || needle.length < 2) return false
        var nIdx = 0
        for (ch in haystack) {
            if (ch == needle[nIdx]) {
                nIdx++
                if (nIdx == needle.length) return true
            }
        }
        return false
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }

    fun searchFolders(query: String, folders: List<FolderModel>, allApps: Map<String, AppInfo>): List<FolderModel> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return folders.filter { folder ->
            folder.folderName.lowercase().contains(q) ||
                folder.memberPackages.any { pkg ->
                    allApps[pkg]?.appName?.lowercase()?.contains(q) == true
                }
        }
    }

    fun hasContactsPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun searchContacts(query: String, limit: Int = 8): List<ContactSearchResult> {
        val ctx = context ?: return emptyList()
        val q = query.trim()
        if (q.isEmpty() || !hasContactsPermission()) return emptyList()

        val results = mutableListOf<ContactSearchResult>()
        val seenKeys = mutableSetOf<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val args = arrayOf("%$q%", "%$q%")

        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (cursor.moveToNext() && results.size < limit) {
                    val cId = if (idCol >= 0) cursor.getString(idCol).orEmpty() else ""
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                    val number = if (numCol >= 0) cursor.getString(numCol).orEmpty() else ""
                    val photo = if (photoCol >= 0) cursor.getString(photoCol) else null
                    val dedupKey = "$name|$number"
                    if (name.isNotBlank() && number.isNotBlank() && seenKeys.add(dedupKey)) {
                        results.add(
                            ContactSearchResult(
                                contactId = cId,
                                displayName = name,
                                phoneNumber = number,
                                photoUri = photo
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun searchLocalFiles(query: String, limit: Int = 8): List<LocalFileSearchResult> {
        val ctx = context ?: return emptyList()
        val q = query.trim()
        if (q.length < 2) return emptyList()

        val results = mutableListOf<LocalFileSearchResult>()
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$q%")

        try {
            ctx.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext() && results.size < limit) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                    if (name.isBlank()) continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "application/octet-stream"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "Internal Storage"
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()
                    results.add(
                        LocalFileSearchResult(
                            id = id,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            contentUri = contentUri,
                            relativePath = relPath
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun isNetworkOnline(): Boolean {
        val ctx = context ?: return false
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun buildWebSearchSuggestions(query: String, engineName: String): List<WebSearchSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val variants = listOf(
            q,
            "$q android app",
            "$q how to",
            "$q latest news"
        ).distinct()
        return variants.map { term ->
            WebSearchSuggestion(
                query = term,
                url = buildSearchEngineUrl(term, engineName),
                searchEngineName = engineName
            )
        }
    }

    fun buildSearchEngineUrl(query: String, engineName: String): String {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (_: Exception) {
            query.trim()
        }
        return when (engineName.lowercase()) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "brave" -> "https://search.brave.com/search?q=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "startpage" -> "https://www.startpage.com/do/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }
}
