package com.voxy.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.voxy.files.data.StorageReport
import com.voxy.files.data.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Brand = Color(0xFF7257E8)
private val Sky = Color(0xFF39A8E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoxyFilesApp() }
    }
}

private enum class MainTab { Files, Tools, Settings }
private enum class ClipboardAction { Copy, Cut }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VoxyFilesApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("voxyfiles", 0) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    val colors = if (darkMode) {
        darkColorScheme(primary = Color(0xFFB7A8FF), secondary = Color(0xFF83CFFF),
            background = Color(0xFF101018), surface = Color(0xFF191923))
    } else {
        lightColorScheme(primary = Brand, secondary = Sky,
            background = Color(0xFFF7F7FC), surface = Color.White)
    }

    MaterialTheme(colorScheme = colors) {
        var rootUri by remember {
            mutableStateOf(prefs.getString("tree_uri", null)?.let(Uri::parse))
        }
        val folderStack = remember(rootUri) {
            mutableStateListOf<DocumentFile>().apply {
                rootUri?.let { DocumentFile.fromTreeUri(context, it)?.let(::add) }
            }
        }
        val currentFolder = folderStack.lastOrNull()
        var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
        var search by remember { mutableStateOf("") }
        var tab by remember { mutableStateOf(MainTab.Files) }
        val selected = remember { mutableStateListOf<String>() }
        var clipboard by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
        var clipboardAction by remember { mutableStateOf(ClipboardAction.Copy) }
        var dialog by remember { mutableStateOf("none") }
        var inputName by remember { mutableStateOf("") }
        var report by remember { mutableStateOf<StorageReport?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun refresh() {
            val folder = folderStack.lastOrNull() ?: return
            scope.launch {
                busy = true
                entries = withContext(Dispatchers.IO) { StorageRepository.list(folder) }
                busy = false
            }
        }

        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                prefs.edit().putString("tree_uri", uri.toString()).apply()
                rootUri = uri
                selected.clear()
                search = ""
            }
        }

        LaunchedEffect(currentFolder?.uri) { refresh() }
        BackHandler(enabled = tab == MainTab.Files && folderStack.size > 1) {
            folderStack.removeAt(folderStack.lastIndex)
            selected.clear()
            search = ""
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == MainTab.Files, { tab = MainTab.Files },
                        icon = { Icon(Icons.Outlined.Folder, null) }, label = { Text("Files") })
                    NavigationBarItem(tab == MainTab.Tools, { tab = MainTab.Tools },
                        icon = { Icon(Icons.Outlined.Analytics, null) }, label = { Text("Tools") })
                    NavigationBarItem(tab == MainTab.Settings, { tab = MainTab.Settings },
                        icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VOXYFILES", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            when (tab) {
                                MainTab.Files -> if (selected.isEmpty()) "Your files, your control" else "${selected.size} selected"
                                MainTab.Tools -> "Power tools"
                                MainTab.Settings -> "Preferences"
                            },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    if (tab == MainTab.Files) {
                        IconButton(onClick = { folderPicker.launch(rootUri) }) {
                            Icon(Icons.Outlined.Storage, contentDescription = "Choose storage folder")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (tab) {
                    MainTab.Files -> {
                        if (selected.isNotEmpty()) {
                            SelectionBar(
                                count = selected.size,
                                onCopy = {
                                    clipboard = entries.filter { it.uri.toString() in selected }
                                    clipboardAction = ClipboardAction.Copy
                                    selected.clear()
                                },
                                onCut = {
                                    clipboard = entries.filter { it.uri.toString() in selected }
                                    clipboardAction = ClipboardAction.Cut
                                    selected.clear()
                                },
                                onRename = {
                                    if (selected.size == 1) {
                                        inputName = entries.firstOrNull { it.uri.toString() == selected.first() }?.name.orEmpty()
                                        dialog = "rename"
                                    }
                                },
                                onDelete = { dialog = "delete" },
                                onClear = { selected.clear() }
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        if (currentFolder == null) {
                            EmptyStorageState { folderPicker.launch(null) }
                        } else {
                            StorageHeader(
                                folderName = currentFolder.name ?: "Selected folder",
                                itemCount = entries.size,
                                onCreateFolder = { inputName = ""; dialog = "folder" },
                                onCreateFile = { inputName = ""; dialog = "file" },
                                onRefresh = ::refresh
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Search this folder") },
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                shape = RoundedCornerShape(16.dp)
                            )
                            if (clipboard.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${clipboard.size} item(s) ready to ${clipboardAction.name.lowercase()}",
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary)
                                    TextButton(onClick = {
                                        val destination = currentFolder
                                        val pending = clipboard.toList()
                                        val action = clipboardAction
                                        scope.launch {
                                            busy = true
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    pending.forEach { source ->
                                                        StorageRepository.copy(context.contentResolver, source, destination)
                                                        if (action == ClipboardAction.Cut) source.delete()
                                                    }
                                                }
                                            }
                                            result.onFailure {
                                                Toast.makeText(context, it.message ?: "Paste failed", Toast.LENGTH_LONG).show()
                                            }
                                            if (result.isSuccess) clipboard = emptyList()
                                            refresh()
                                            busy = false
                                        }
                                    }) {
                                        Icon(Icons.Outlined.ContentPaste, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Paste")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (busy) Text("Working…", color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 12.dp))
                            val visible = entries.filter { it.name.orEmpty().contains(search, ignoreCase = true) }
                            if (visible.isEmpty()) {
                                EmptyFolderState(search.isNotBlank())
                            } else {
                                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(visible, key = { it.uri.toString() }) { file ->
                                        FileRow(
                                            file = file,
                                            isSelected = file.uri.toString() in selected,
                                            onClick = {
                                                if (selected.isNotEmpty()) {
                                                    val key = file.uri.toString()
                                                    if (key in selected) selected.remove(key) else selected.add(key)
                                                } else if (file.isDirectory) {
                                                    folderStack.add(file)
                                                    search = ""
                                                } else {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(file.uri, file.type ?: "*/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (_: ActivityNotFoundException) {
                                                        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                val key = file.uri.toString()
                                                if (key in selected) selected.remove(key) else selected.add(key)
                                            },
                                            onInfo = {
                                                inputName = file.name ?: "Unnamed"
                                                dialog = "details"
                                                selected.clear()
                                                selected.add(file.uri.toString())
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MainTab.Tools -> {
                        ToolCard(
                            icon = { Icon(Icons.Outlined.Analytics, null, tint = Brand) },
                            title = "Storage analyzer",
                            description = "Count folders and files, estimate known storage usage, and find the 10 largest files in your selected folder.",
                            action = "Analyze storage",
                            enabled = currentFolder != null && !busy,
                            onClick = {
                                val root = folderStack.firstOrNull() ?: return@ToolCard
                                scope.launch {
                                    busy = true
                                    report = withContext(Dispatchers.IO) { StorageRepository.analyze(root) }
                                    busy = false
                                    dialog = "analysis"
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        ToolCard(
                            icon = { Icon(Icons.Outlined.Description, null, tint = Sky) },
                            title = "File workspace",
                            description = "Text and HEX editors, archive tools, APK analysis, database tools and file comparison are planned as separate modules.",
                            action = "View roadmap",
                            enabled = true,
                            onClick = { dialog = "roadmap" }
                        )
                    }

                    MainTab.Settings -> {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Storage access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("VoxyFiles only browses folders you explicitly select in Android's system folder picker.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { folderPicker.launch(rootUri) }, modifier = Modifier.padding(top = 10.dp)) {
                                    Text("Choose accessible folder")
                                }
                                Divider(Modifier.padding(vertical = 14.dp))
                                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (darkMode) "Dark theme" else "Light theme", modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        darkMode = !darkMode
                                        prefs.edit().putBoolean("dark_mode", darkMode).apply()
                                    }) { Text("Switch theme") }
                                }
                                Divider(Modifier.padding(vertical = 14.dp))
                                Text("About VoxyFiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Version 0.1.0 • Native Android • Kotlin + Jetpack Compose",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (dialog != "none") {
            when (dialog) {
                "folder", "file", "rename" -> {
                    val title = when (dialog) {
                        "folder" -> "Create folder"
                        "file" -> "Create text file"
                        else -> "Rename item"
                    }
                    AlertDialog(
                        onDismissRequest = { dialog = "none" },
                        title = { Text(title) },
                        text = {
                            OutlinedTextField(value = inputName, onValueChange = { inputName = it },
                                singleLine = true, label = { Text("Name") })
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val folder = folderStack.lastOrNull()
                                if (folder == null) {
                                    dialog = "none"
                                } else {
                                    val operation = dialog
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                when (operation) {
                                                    "folder" -> StorageRepository.createFolder(folder, inputName)
                                                    "file" -> StorageRepository.createTextFile(folder, inputName, context.contentResolver)
                                                    else -> {
                                                        val target = entries.firstOrNull { it.uri.toString() in selected }
                                                            ?: throw IllegalStateException("Select an item to rename.")
                                                        if (!StorageRepository.rename(target, inputName)) {
                                                            throw IllegalStateException("Rename was not accepted by this storage provider.")
                                                        }
                                                        target
                                                    }
                                                }
                                            }
                                        }
                                        result.onFailure {
                                            Toast.makeText(context, it.message ?: "Operation failed", Toast.LENGTH_LONG).show()
                                        }
                                        selected.clear()
                                        dialog = "none"
                                        refresh()
                                    }
                                }
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { dialog = "none" }) { Text("Cancel") } }
                    )
                }

                "delete" -> AlertDialog(
                    onDismissRequest = { dialog = "none" },
                    title = { Text("Delete selected items?") },
                    text = { Text("This removes ${selected.size} selected item(s) from the chosen storage location. This action may not be reversible.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val targets = entries.filter { it.uri.toString() in selected }
                            scope.launch {
                                val count = withContext(Dispatchers.IO) { StorageRepository.delete(targets) }
                                Toast.makeText(context, "Deleted $count item(s).", Toast.LENGTH_SHORT).show()
                                selected.clear()
                                dialog = "none"
                                refresh()
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { dialog = "none" }) { Text("Cancel") } }
                )

                "details" -> {
                    val file = entries.firstOrNull { it.uri.toString() in selected }
                    AlertDialog(
                        onDismissRequest = { dialog = "none"; selected.clear() },
                        title = { Text("File properties") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(file?.name ?: inputName, fontWeight = FontWeight.SemiBold)
                                Text(if (file?.isDirectory == true) "Folder" else file?.type ?: "Unknown file type")
                                Text("Size: ${formatBytes(file?.length() ?: 0L)}")
                                Text("Modified: ${file?.lastModified()?.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "Unknown"}")
                                Text("Readable: ${file?.canRead() == true} • Writable: ${file?.canWrite() == true}")
                            }
                        },
                        confirmButton = { TextButton(onClick = { dialog = "none"; selected.clear() }) { Text("Done") } }
                    )
                }

                "analysis" -> {
                    val data = report
                    AlertDialog(
                        onDismissRequest = { dialog = "none" },
                        title = { Text("Storage report") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Folders: ${data?.folders ?: 0}")
                                Text("Files: ${data?.files ?: 0}")
                                Text("Known file size: ${formatBytes(data?.knownBytes ?: 0L)}")
                                Divider()
                                Text("Largest files", fontWeight = FontWeight.SemiBold)
                                data?.largestFiles?.forEach {
                                    Text("${it.name} • ${formatBytes(it.sizeBytes)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (data?.largestFiles.isNullOrEmpty()) Text("No files found.")
                            }
                        },
                        confirmButton = { TextButton(onClick = { dialog = "none" }) { Text("Close") } }
                    )
                }

                "roadmap" -> AlertDialog(
                    onDismissRequest = { dialog = "none" },
                    title = { Text("Development roadmap") },
                    text = { Text("Next modules: archive create/extract, APK inspector, code and HEX editors, file hashing and comparison, network storage, cloud providers, sync/backup and encrypted vault. They will be added in separate tested milestones.") },
                    confirmButton = { TextButton(onClick = { dialog = "none" }) { Text("OK") } }
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.ArrowBack, "Clear selection") }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copy") }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, "Cut") }
            IconButton(onClick = onRename, enabled = count == 1) { Icon(Icons.Outlined.Edit, "Rename") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        }
    }
}

@Composable
private fun StorageHeader(
    folderName: String,
    itemCount: Int,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brand.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Folder, null, tint = Brand)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folderName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$itemCount items", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.MoreVert, "Refresh") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCreateFolder) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Spacer(Modifier.width(5.dp))
                    Text("New folder")
                }
                TextButton(onClick = onCreateFile) {
                    Icon(Icons.Outlined.Description, null)
                    Spacer(Modifier.width(5.dp))
                    Text("New text")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: DocumentFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(if (file.isDirectory) Brand.copy(alpha = 0.12f) else Sky.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(if (file.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                    null, tint = if (file.isDirectory) Brand else Sky)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name ?: "Unnamed", fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (file.isDirectory) "Folder" else "${file.type ?: "File"} • ${formatBytes(file.length())}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isSelected) {
                Icon(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, "Properties") }
            }
        }
    }
}

@Composable
private fun EmptyStorageState(onChoose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Storage, null, tint = Brand, modifier = Modifier.size(58.dp))
            Spacer(Modifier.height(12.dp))
            Text("Choose a storage folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Grant access to a folder to browse and manage its files.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onChoose) { Text("Choose folder") }
        }
    }
}

@Composable
private fun EmptyFolderState(searching: Boolean) {
    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(if (searching) "No matching files in this folder." else "This folder is empty.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                Text(action)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[index])
}
