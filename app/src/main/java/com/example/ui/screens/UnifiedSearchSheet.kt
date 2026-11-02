package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.AppInfo
import com.example.domain.model.IconShapeOption
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.LauncherAppTile
import com.example.ui.viewmodel.LauncherUiState
import com.example.ui.viewmodel.SearchUiState

@Composable
fun UnifiedSearchSheet(
    launcherState: LauncherUiState,
    searchState: SearchUiState,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onCloseWebView: () -> Unit,
    onClearHistory: () -> Unit,
    onRecordHistory: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = launcherState.settings
    val iconShape = remember(settings.iconShape) {
        IconShapeOption.fromId(settings.iconShape)
    }

    BackHandler {
        if (searchState.activeWebViewUrl != null) {
            onCloseWebView()
        } else {
            onCloseSearch()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onQueryChange(searchState.query)
    }

    val safDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            } catch (_: Exception) {
            }
        }
    }

    FrostedGlassBox(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        alphaOverride = 0.88f,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("unified_search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp)
        ) {
            // Top Unified Search Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (searchState.activeWebViewUrl != null) {
                            onCloseWebView()
                        } else {
                            onCloseSearch()
                        }
                    },
                    modifier = Modifier.testTag("unified_search_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchState.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchState.query.isNotBlank()) {
                                onRecordHistory(searchState.query, "UNIFIED")
                                val topApp = searchState.appResults.firstOrNull()
                                if (topApp != null) {
                                    onLaunchApp(topApp)
                                } else if (searchState.isOnline) {
                                    onOpenWebUrl(searchState.query)
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("unified_search_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Embedded Secure WebView Mode (when user taps a Web result)
            val activeUrl = searchState.activeWebViewUrl
            if (activeUrl != null) {
                EmbeddedSecureSearchWebView(
                    url = activeUrl,
                    isOnline = searchState.isOnline,
                    onClose = onCloseWebView,
                    onOpenExternal = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("unified_search_results_list")
                ) {
                    // Section F: Recent Search History (when query is blank or matching)
                    if (launcherState.searchHistory.isNotEmpty() && searchState.query.isBlank()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches (Offline History)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear")
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(launcherState.searchHistory, key = { it.id }) { entry ->
                                    FrostedGlassBox(
                                        shape = RoundedCornerShape(16.dp),
                                        alphaOverride = 0.6f,
                                        modifier = Modifier.clickable { onQueryChange(entry.query) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = entry.query,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section A: Applications
                    item {
                        Text(
                            text = if (searchState.query.isBlank()) {
                                "Suggested Applications"
                            } else {
                                "Applications (${searchState.appResults.size})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (searchState.appResults.isEmpty()) {
                            Text(
                                text = "No matching installed applications",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(searchState.appResults, key = { it.packageName }) { app ->
                                    LauncherAppTile(
                                        app = app,
                                        iconSizeDp = 52.dp,
                                        iconScale = settings.iconScale,
                                        iconShape = iconShape,
                                        showLabel = true,
                                        showCategoryBadge = true,
                                        onClick = {
                                            if (searchState.query.isNotBlank()) {
                                                onRecordHistory(searchState.query, "APP")
                                            }
                                            onLaunchApp(app)
                                        },
                                        onLongClick = { onLongPressApp(app) }
                                    )
                                }
                            }
                        }
                    }

                    // Section B: Folders
                    if (searchState.folderResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Folders (${searchState.folderResults.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(searchState.folderResults, key = { "folder_${it.id}" }) { folder ->
                            FrostedGlassBox(
                                shape = RoundedCornerShape(16.dp),
                                alphaOverride = 0.6f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCloseSearch()
                                        onOpenFolder(folder.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${folder.memberPackages.size} apps inside",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Contacts
                    if (settings.enableContactsSearch) {
                        item {
                            Text(
                                text = "Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!searchState.hasContactsPermission) {
                            item {
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(16.dp),
                                    alphaOverride = 0.62f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Local Contacts Search",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = stringResource(R.string.contacts_permission_rationale),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                            },
                                            modifier = Modifier.testTag("grant_contacts_permission_button")
                                        ) {
                                            Text("Allow")
                                        }
                                    }
                                }
                            }
                        } else if (searchState.contactResults.isNotEmpty()) {
                            items(
                                searchState.contactResults,
                                key = { "contact_${it.contactId}_${it.phoneNumber}" }
                            ) { contact ->
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(16.dp),
                                    alphaOverride = 0.62f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Column {
                                                Text(
                                                    text = contact.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = contact.phoneNumber,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val dialIntent = Intent(
                                                            Intent.ACTION_DIAL,
                                                            Uri.parse("tel:${contact.phoneNumber}")
                                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(dialIntent)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Call ${contact.displayName}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val smsIntent = Intent(
                                                            Intent.ACTION_SENDTO,
                                                            Uri.parse("smsto:${contact.phoneNumber}")
                                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(smsIntent)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                                    contentDescription = "Message ${contact.displayName}",
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section D: Files (MediaStore + SAF Document Picker)
                    if (settings.enableFilesSearch) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Local Files & Documents",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = { safDocumentPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("saf_browse_files_button")
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse Storage")
                                }
                            }
                        }
                        items(searchState.fileResults, key = { "file_${it.id}" }) { file ->
                            FrostedGlassBox(
                                shape = RoundedCornerShape(14.dp),
                                alphaOverride = 0.6f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(file.contentUri), file.mimeType)
                                                addFlags(
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            }
                                            context.startActivity(openIntent)
                                        } catch (_: Exception) {
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text(
                                            text = file.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${file.relativePath} • ${file.sizeBytes / 1024} KB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section E: Web Search & Offline State Awareness
                    if (searchState.query.isNotBlank()) {
                        item {
                            Text(
                                text = "Web Search (${settings.defaultSearchEngine})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!searchState.isOnline) {
                            item {
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(16.dp),
                                    alphaOverride = 0.68f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                        Column {
                                            Text(
                                                text = "You are currently offline",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Internet connectivity is unavailable for Web Search. App, Folder, Contact, File search, and AI categorization remain 100% functional offline.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(searchState.webSuggestions, key = { it.url }) { suggestion ->
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(14.dp),
                                    alphaOverride = 0.6f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenWebUrl(suggestion.url) }
                                        .testTag("web_suggestion_${suggestion.query}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = suggestion.query,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Search on ${suggestion.searchEngineName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = "Open web search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedSecureSearchWebView(
    url: String,
    isOnline: Boolean,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url.take(46),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row {
                TextButton(onClick = onOpenExternal) {
                    Text("Open in Browser")
                }
                TextButton(onClick = onClose) {
                    Text("Close Web")
                }
            }
        }

        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No Internet Connection",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Web results require an active internet connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClose) {
                        Text("Return to Local Search")
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Strict security hardening: no file access, no content access, no JS bridges
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return true
                                val scheme = targetUri.scheme?.lowercase()
                                return scheme != "https" && scheme != "http"
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.url != url) {
                        webView.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
