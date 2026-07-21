package com.uc0079.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.uc0079.launcher.AppFolder
import com.uc0079.launcher.AppInfo
import com.uc0079.launcher.IndexLetter
import com.uc0079.launcher.LauncherViewModel
import com.uc0079.launcher.UpdateChecker
import com.uc0079.launcher.WidgetHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen { HOME, ALL, FOLDER }

@Composable
fun LauncherApp(vm: LauncherViewModel, widgets: WidgetHostController) {
    GundamTheme {
        var screen by remember { mutableStateOf(Screen.HOME) }
        var openFolderId by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        // System Home button → back to home screen (from ALL / FOLDER).
        val homePulse = vm.homePulse
        LaunchedEffect(homePulse) {
            if (homePulse > 0) {
                openFolderId = null
                screen = Screen.HOME
            }
        }

        // Update dialog only when user taps the home banner (not auto-popup).
        var showUpdateDialog by remember { mutableStateOf(false) }
        val updateInfo = vm.updateInfo
        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                info = updateInfo,
                onDismiss = {
                    showUpdateDialog = false
                    // Keep banner visible; user can open again from the strip.
                },
                onDownload = { apkUrl ->
                    showUpdateDialog = false
                    vm.dismissUpdate()
                    UpdateChecker.download(context, apkUrl) { fileUri ->
                        UpdateChecker.installApk(context, fileUri)
                    }
                }
            )
        }

        ScrimBackground(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    vm = vm,
                    widgets = widgets,
                    onOpenAll = { screen = Screen.ALL },
                    onOpenFolder = { id ->
                        openFolderId = id
                        screen = Screen.FOLDER
                    },
                    onOpenUpdate = { showUpdateDialog = true },
                )
                Screen.ALL -> {
                    BackHandler { screen = Screen.HOME }
                    AllAppsScreen(
                        vm = vm,
                        onClose = { screen = Screen.HOME },
                        onOpenFolder = { id ->
                            openFolderId = id
                            screen = Screen.FOLDER
                        }
                    )
                }
                Screen.FOLDER -> {
                    val id = openFolderId
                    if (id == null || vm.folders.none { it.id == id }) {
                        LaunchedEffect(id) {
                            openFolderId = null
                            screen = Screen.HOME
                        }
                    } else {
                        BackHandler {
                            openFolderId = null
                            screen = Screen.HOME
                        }
                        FolderScreen(
                            vm = vm,
                            folderId = id,
                            onClose = {
                                openFolderId = null
                                screen = Screen.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* HOME                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun HomeScreen(
    vm: LauncherViewModel,
    widgets: WidgetHostController,
    onOpenAll: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val favorites = vm.favoriteApps
    val folders = vm.folders
    val scroll = rememberScrollState()
    var createFolderOpen by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<AppFolder?>(null) }
    var helpOpen by remember { mutableStateOf(false) }
    val updateInfo = vm.updateInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        HudHeader(
            unitCount = vm.apps.size,
            onSwipeUp = onOpenAll,
        )

        HomeCommandStrip(
            updateInfo = updateInfo,
            onOpenUpdate = onOpenUpdate,
            onOpenMenu = { helpOpen = true },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            Spacer(Modifier.height(12.dp))
            SectionLabel(
                text = "WIDGETS / ウィジェット",
                onAdd = { widgets.pickWidget() }
            )
            Spacer(Modifier.height(8.dp))
            if (widgets.widgetIds.isEmpty()) {
                Text(
                    text = "なし — 右の ＋ で追加",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            widgets.widgetIds.forEach { id ->
                WidgetFrame(widgets = widgets, id = id)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("FAVORITE UNITS / お気に入り")
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                Text(
                    text = "登録なし — [ALL UNITS] を開き、\nアプリ右の ⋮ から「お気に入りに追加」",
                    color = G.Dim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                favorites.forEachIndexed { index, app ->
                    AppRow(
                        app = app,
                        isFavorite = true,
                        folders = folders,
                        currentFolder = vm.folderOf(app.packageName),
                        displayName = vm.displayLabel(app),
                        originalName = app.label,
                        onLaunch = { vm.launchApp(app.packageName) },
                        onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                        onAddToFolder = { folderId -> vm.addToFolder(folderId, app.packageName) },
                        onCreateFolderWithApp = { name ->
                            vm.createFolder(name, app.packageName)
                        },
                        onRemoveFromFolder = { folderId ->
                            vm.removeFromFolder(folderId, app.packageName)
                        },
                        onRename = { name -> vm.setCustomLabel(app.packageName, name) },
                        onResetName = { vm.clearCustomLabel(app.packageName) },
                        onMoveUp = if (index > 0) {
                            { vm.moveFavorite(app.packageName, -1) }
                        } else null,
                        onMoveDown = if (index < favorites.lastIndex) {
                            { vm.moveFavorite(app.packageName, +1) }
                        } else null,
                        labelSize = 18.sp,
                        iconSize = 28.dp
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel(
                text = "FOLDERS / フォルダ",
                onAdd = { createFolderOpen = true }
            )
            Spacer(Modifier.height(6.dp))
            if (folders.isEmpty()) {
                Text(
                    text = "なし — 右の ＋ で作成",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    previewIcons = vm.appsInFolder(folder.id).take(4).map { it.icon },
                    onOpen = { onOpenFolder(folder.id) },
                    onRename = { renameFolder = folder },
                    onDelete = { vm.deleteFolder(folder.id) }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        AllUnitsButton(onOpenAll)
        Spacer(Modifier.height(10.dp))
    }

    if (createFolderOpen) {
        FolderNameDialog(
            title = "新しいフォルダ",
            initial = "フォルダ",
            onDismiss = { createFolderOpen = false },
            onConfirm = { name ->
                createFolderOpen = false
                vm.createFolder(name)
            }
        )
    }
    renameFolder?.let { folder ->
        FolderNameDialog(
            title = "フォルダ名を変更",
            initial = folder.name,
            onDismiss = { renameFolder = null },
            onConfirm = { name ->
                vm.renameFolder(folder.id, name)
                renameFolder = null
            }
        )
    }
    if (helpOpen) {
        val ctx = LocalContext.current
        val ver = remember {
            runCatching { "v${UpdateChecker.currentVersionCode(ctx)}" }.getOrDefault("?")
        }
        HelpSheet(
            onDismiss = { helpOpen = false },
            onOpenLauncherSettings = { vm.openHomeAppSettings() },
            onCheckUpdate = { vm.checkForUpdate(force = true) },
            versionLabel = ver,
        )
    }
}

@Composable
private fun WidgetFrame(widgets: WidgetHostController, id: Int) {
    val minH = remember(id) { widgets.minHeightPx(id) }
    val heightDp = with(LocalDensity.current) { minH.toDp() }.coerceIn(72.dp, 340.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .hudFrame(fill = G.PanelStrong)
            .padding(6.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                widgets.createHostView(ctx, id) ?: android.widget.FrameLayout(ctx)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .background(G.PanelStrong)
                .clickable { widgets.removeWidget(id) },
            contentAlignment = Alignment.Center
        ) {
            Text("\u2715", color = G.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val Green = Color(0xFF15C26B)

@Composable
private fun HomeCommandStrip(
    updateInfo: UpdateChecker.UpdateInfo?,
    onOpenUpdate: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (updateInfo != null && updateInfo.available) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .hudFrame(fill = G.PanelStrong, bracket = G.Red)
                    .clickable(onClick = onOpenUpdate)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPDATE",
                    color = G.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = updateInfo.message,
                    color = G.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "詳細 \u25B6",
                    color = G.Cyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "MENU",
            color = G.Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier
                .hudFrame(fill = G.Panel, bracket = G.Cyan)
                .clickable(onClick = onOpenMenu)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun HudHeader(unitCount: Int, onSwipeUp: () -> Unit) {
    val (time, date) = rememberClock()
    val battery = rememberBatteryPercent()

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -90f) onSwipeUp() },
                    onDragCancel = { total = 0f }
                )
            }
            .cockpitPanel()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Top rail — callsign + energy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "MSZ-006",
                    color = G.Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "A.E.U.G.",
                    color = G.Dim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                EnergyMeter(percent = battery)
            }

            Spacer(Modifier.height(8.dp))

            // Status lights
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(G.Yellow, "SYS")
                Spacer(Modifier.width(10.dp))
                StatusDot(Green, "COM")
                Spacer(Modifier.width(10.dp))
                StatusDot(G.Cyan, "NAV")
                Spacer(Modifier.width(10.dp))
                StatusDot(G.Blue, "THR")
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (battery in 0..15) "ENERGY LOW" else "ALL GREEN",
                    color = if (battery in 0..15) G.Red else Green,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Main chronometer + unit mark
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(G.Red)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "MISSION TIME",
                        color = G.Dim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = time,
                        color = G.White,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "\u30BC\u30FC\u30BF\u30AC\u30F3\u30C0\u30E0",
                        color = G.Dim,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "ZETA",
                        color = G.Cyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 5.sp
                    )
                    Text(
                        text = "ORBITAL",
                        color = G.Blue,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom data strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = G.Border,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = date,
                    color = G.Dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "U.C.0087",
                    color = G.Cyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "UNITS $unitCount",
                    color = G.Dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Segmented reactor / energy gauge for battery %. */
@Composable
private fun EnergyMeter(percent: Int) {
    val pct = percent.coerceIn(0, 100)
    val segments = 10
    val filled = if (percent < 0) 0 else ((pct / 100f) * segments).toInt().coerceIn(0, segments)
    val barColor = when {
        percent < 0 -> G.Dim
        pct <= 15 -> G.Red
        pct <= 35 -> G.Yellow
        else -> G.Cyan
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "ENRG",
            color = G.Dim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(segments) { i ->
                Box(
                    Modifier
                        .width(7.dp)
                        .height(12.dp)
                        .background(
                            if (i < filled) barColor else Color(0x33FFFFFF),
                            SkewTag
                        )
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (percent >= 0) "$pct%" else "--",
            color = barColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = G.Dim,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun AllUnitsButton(onOpenAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hudFrame(fill = G.PanelStrong, bracket = G.Yellow)
            .clickable(onClick = onOpenAll)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u25B2  ALL UNITS  /  全アプリ",
            color = G.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/* ALL APPS                                                            */
/* ------------------------------------------------------------------ */

private data class ListRow(
    val header: Char?,
    val app: AppInfo?,
    val folder: AppFolder? = null
)

/** Header marker for the favorites block pinned above A–Z. */
private const val FAV_HEADER = '\u2605' // ★
/** Header marker for folders block. */
private const val FOLDER_HEADER = '\u25A3' // ▣

@Composable
private fun AllAppsScreen(
    vm: LauncherViewModel,
    onClose: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var renameFolder by remember { mutableStateOf<AppFolder?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val favorites = vm.favoriteApps
    val folders = vm.folders
    val shelved = vm.shelvedPackages

    val filtered = remember(vm.apps, query, vm.customLabels) {
        if (query.isBlank()) vm.apps
        else {
            val q = query.trim()
            vm.apps.filter {
                vm.displayLabel(it).contains(q, ignoreCase = true) ||
                    it.label.contains(q, ignoreCase = true)
            }
        }
    }

    val rows = remember(filtered, query, favorites, folders, shelved, vm.customLabels) {
        if (query.isNotBlank()) {
            filtered.map { ListRow(header = null, app = it, folder = null) }
        } else {
            val favInList = favorites.filter { fav ->
                filtered.any { it.packageName == fav.packageName }
            }
            val visible = filtered.filter { it.packageName !in shelved }
            val collator = java.text.Collator.getInstance(Locale.JAPAN)
            val grouped = visible
                .groupBy { IndexLetter.of(vm.displayLabel(it)) }
                .toSortedMap(compareBy { ch -> if (ch == '#') Char.MAX_VALUE else ch })
                .mapValues { (_, list) ->
                    list.sortedWith(compareBy(collator) { vm.displayLabel(it) })
                }
            buildList {
                if (favInList.isNotEmpty()) {
                    add(ListRow(FAV_HEADER, null, null))
                    favInList.forEach { add(ListRow(null, it, null)) }
                }
                if (folders.isNotEmpty()) {
                    add(ListRow(FOLDER_HEADER, null, null))
                    folders.forEach { add(ListRow(null, null, it)) }
                }
                grouped.forEach { (letter, list) ->
                    add(ListRow(letter, null, null))
                    list.forEach { add(ListRow(null, it, null)) }
                }
            }
        }
    }

    val letterIndex = remember(rows) {
        val map = LinkedHashMap<Char, Int>()
        rows.forEachIndexed { i, r -> if (r.header != null) map[r.header] = i }
        map
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onClose = onClose
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(rows.size) { i ->
                        val row = rows[i]
                        when {
                            row.header != null -> {
                                when (row.header) {
                                    FAV_HEADER -> Text(
                                        text = "\u2605  FAVORITES / \u304A\u6C17\u306B\u5165\u308A",
                                        color = G.Yellow,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
                                    )
                                    FOLDER_HEADER -> Text(
                                        text = "\u25A3  FOLDERS / \u30D5\u30A9\u30EB\u30C0",
                                        color = G.Cyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                                    )
                                    else -> Text(
                                        text = row.header.toString(),
                                        color = G.Cyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            row.folder != null -> {
                                val folder = row.folder
                                FolderRow(
                                    folder = folder,
                                    previewIcons = vm.appsInFolder(folder.id).take(4).map { it.icon },
                                    onOpen = { onOpenFolder(folder.id) },
                                    onRename = { renameFolder = folder },
                                    onDelete = { vm.deleteFolder(folder.id) }
                                )
                            }
                            row.app != null -> {
                                val app = row.app
                                AppRow(
                                    app = app,
                                    isFavorite = vm.isFavorite(app.packageName),
                                    folders = folders,
                                    currentFolder = vm.folderOf(app.packageName),
                                    displayName = vm.displayLabel(app),
                                    originalName = app.label,
                                    onLaunch = { vm.launchApp(app.packageName) },
                                    onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                                    onAddToFolder = { folderId ->
                                        vm.addToFolder(folderId, app.packageName)
                                    },
                                    onCreateFolderWithApp = { name ->
                                        vm.createFolder(name, app.packageName)
                                    },
                                    onRemoveFromFolder = { folderId ->
                                        vm.removeFromFolder(folderId, app.packageName)
                                    },
                                    onRename = { name -> vm.setCustomLabel(app.packageName, name) },
                                    onResetName = { vm.clearCustomLabel(app.packageName) },
                                    labelSize = 18.sp,
                                    iconSize = 30.dp
                                )
                            }
                        }
                    }
                }

                if (query.isBlank() && letterIndex.isNotEmpty()) {
                    AlphabetScroller(
                        letters = letterIndex.keys.toList(),
                        activeLetter = activeLetter,
                        onActiveChange = { activeLetter = it },
                        onLetter = { c ->
                            letterIndex[c]?.let { idx ->
                                scope.launch { listState.scrollToItem(idx) }
                            }
                        }
                    )
                }
            }

            activeLetter?.let { c ->
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 42.dp)
                        .size(78.dp)
                        .hudFrame(
                            fill = G.PanelStrong,
                            bracket = if (c == FAV_HEADER) G.Yellow else G.Cyan
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = c.toString(),
                        color = if (c == FAV_HEADER) G.Yellow else G.White,
                        fontSize = if (c == FAV_HEADER || c == FOLDER_HEADER) 36.sp else 42.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    renameFolder?.let { folder ->
        FolderNameDialog(
            title = "フォルダ名を変更",
            initial = folder.name,
            onDismiss = { renameFolder = null },
            onConfirm = { name ->
                vm.renameFolder(folder.id, name)
                renameFolder = null
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .hudFrame()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\uD83D\uDD0D", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "SEARCH UNIT / 検索…",
                    color = G.Dim,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = G.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(G.Cyan),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "\u2715",
            color = G.Red,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun AlphabetScroller(
    letters: List<Char>,
    activeLetter: Char?,
    onActiveChange: (Char?) -> Unit,
    onLetter: (Char) -> Unit
) {
    var heightPx by remember { mutableStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(30.dp)
            .padding(start = 4.dp)
            .background(G.Panel)
            .onSizeChanged { heightPx = if (it.height > 0) it.height else 1 }
            .pointerInput(letters, heightPx) {
                awaitEachGesture {
                    fun pick(y: Float) {
                        if (letters.isEmpty()) return
                        val idx = ((y / heightPx) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.size - 1)
                        val c = letters[idx]
                        onActiveChange(c)
                        onLetter(c)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pick(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        pick(change.position.y)
                        change.consume()
                    }
                    onActiveChange(null)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { c ->
            val on = c == activeLetter
            val special = c == FAV_HEADER || c == FOLDER_HEADER
            Text(
                text = c.toString(),
                color = when {
                    on || c == FAV_HEADER -> G.Yellow
                    else -> G.Cyan
                },
                fontSize = if (on) 12.sp else 10.sp,
                fontWeight = if (on || special) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* APP ROW + FOLDERS                                                   */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    isFavorite: Boolean,
    folders: List<AppFolder>,
    currentFolder: AppFolder?,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToFolder: (String) -> Unit,
    onCreateFolderWithApp: (String) -> Unit,
    onRemoveFromFolder: (String) -> Unit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    iconSize: androidx.compose.ui.unit.Dp,
    displayName: String = app.label,
    originalName: String = app.label,
    onRename: ((String) -> Unit)? = null,
    onResetName: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var pickFolderOpen by remember { mutableStateOf(false) }
    var createNameOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onLaunch,
                onLongClick = { sheetOpen = true }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = displayName,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = G.White,
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (currentFolder != null) {
                Text(
                    text = "\u25A3 ${currentFolder.name}",
                    color = G.Dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMoveUp != null || onMoveDown != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\u25B2",
                    color = if (onMoveUp != null) G.Cyan else G.Dim.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (onMoveUp != null) Modifier.clickable(onClick = onMoveUp)
                            else Modifier
                        )
                        .padding(2.dp)
                )
                Text(
                    text = "\u25BC",
                    color = if (onMoveDown != null) G.Cyan else G.Dim.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (onMoveDown != null) Modifier.clickable(onClick = onMoveDown)
                            else Modifier
                        )
                        .padding(2.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        if (isFavorite) {
            Text(text = "\u2605", color = G.Yellow, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
        }
        MenuDotsButton(onClick = { sheetOpen = true })
    }

    if (sheetOpen) {
        AppActionSheet(
            app = app,
            displayName = displayName,
            isFavorite = isFavorite,
            folders = folders,
            currentFolder = currentFolder,
            onDismiss = { sheetOpen = false },
            onLaunch = onLaunch,
            onToggleFavorite = onToggleFavorite,
            onAddToFolder = {
                sheetOpen = false
                pickFolderOpen = true
            },
            onRemoveFromFolder = {
                currentFolder?.let { onRemoveFromFolder(it.id) }
            },
            onRename = {
                sheetOpen = false
                renameOpen = true
            },
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }

    if (pickFolderOpen) {
        FolderPickDialog(
            folders = folders,
            onDismiss = { pickFolderOpen = false },
            onPick = { id ->
                pickFolderOpen = false
                onAddToFolder(id)
            },
            onCreateNew = {
                pickFolderOpen = false
                createNameOpen = true
            }
        )
    }
    if (createNameOpen) {
        FolderNameDialog(
            title = "新しいフォルダにしまう",
            initial = "フォルダ",
            onDismiss = { createNameOpen = false },
            onConfirm = { name ->
                createNameOpen = false
                onCreateFolderWithApp(name)
            }
        )
    }
    if (renameOpen && onRename != null) {
        RenameAppDialog(
            currentName = displayName,
            originalName = originalName,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                onRename(name)
            },
            onReset = onResetName?.let { reset ->
                {
                    renameOpen = false
                    reset()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: AppFolder,
    previewIcons: List<androidx.compose.ui.graphics.ImageBitmap>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { sheetOpen = true }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .hudFrame(fill = G.PanelStrong, bracket = G.Cyan)
                .padding(3.dp)
        ) {
            when {
                previewIcons.isEmpty() -> Text(
                    "\u25A3",
                    color = G.Cyan,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                previewIcons.size == 1 -> Image(
                    bitmap = previewIcons[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            previewIcons.getOrNull(0)?.let {
                                Image(it, null, Modifier.weight(1f).fillMaxHeight().padding(0.5.dp))
                            } ?: Spacer(Modifier.weight(1f))
                            previewIcons.getOrNull(1)?.let {
                                Image(it, null, Modifier.weight(1f).fillMaxHeight().padding(0.5.dp))
                            } ?: Spacer(Modifier.weight(1f))
                        }
                        Row(Modifier.weight(1f)) {
                            previewIcons.getOrNull(2)?.let {
                                Image(it, null, Modifier.weight(1f).fillMaxHeight().padding(0.5.dp))
                            } ?: Spacer(Modifier.weight(1f))
                            previewIcons.getOrNull(3)?.let {
                                Image(it, null, Modifier.weight(1f).fillMaxHeight().padding(0.5.dp))
                            } ?: Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = G.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.packageNames.size} apps",
                color = G.Dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        MenuDotsButton(onClick = { sheetOpen = true })
    }

    if (sheetOpen) {
        FolderActionSheet(
            folder = folder,
            onDismiss = { sheetOpen = false },
            onOpen = onOpen,
            onRename = onRename,
            onDelete = onDelete
        )
    }
}

@Composable
private fun FolderScreen(
    vm: LauncherViewModel,
    folderId: String,
    onClose: () -> Unit
) {
    val folder = vm.folders.firstOrNull { it.id == folderId } ?: return
    val apps = vm.appsInFolder(folderId)
    var renameOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .hudFrame()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u25C0",
                color = G.Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "\u25A3  ${folder.name}",
                    color = G.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${apps.size} STORED UNITS",
                    color = G.Dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "\u270E",
                color = G.Yellow,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable { renameOpen = true }
                    .padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        if (apps.isEmpty()) {
            Text(
                text = "空です — 全アプリで ⋮ を押し、\n「フォルダにしまう」で入れてください。",
                color = G.Dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(apps.size) { i ->
                    val app = apps[i]
                    AppRow(
                        app = app,
                        isFavorite = vm.isFavorite(app.packageName),
                        folders = vm.folders,
                        currentFolder = folder,
                        displayName = vm.displayLabel(app),
                        originalName = app.label,
                        onLaunch = { vm.launchApp(app.packageName) },
                        onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                        onAddToFolder = { id -> vm.addToFolder(id, app.packageName) },
                        onCreateFolderWithApp = { name -> vm.createFolder(name, app.packageName) },
                        onRemoveFromFolder = { id -> vm.removeFromFolder(id, app.packageName) },
                        onRename = { name -> vm.setCustomLabel(app.packageName, name) },
                        onResetName = { vm.clearCustomLabel(app.packageName) },
                        labelSize = 18.sp,
                        iconSize = 30.dp
                    )
                }
            }
        }
    }

    if (renameOpen) {
        FolderNameDialog(
            title = "フォルダ名を変更",
            initial = folder.name,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                vm.renameFolder(folder.id, name)
                renameOpen = false
            }
        )
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Text(title, color = G.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = G.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(G.Cyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .hudFrame()
                    .padding(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK", color = G.Cyan, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun RenameAppDialog(
    currentName: String,
    originalName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onReset: (() -> Unit)?,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Text(
                "名前を変更",
                color = G.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "元の名前: $originalName",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = G.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(G.Cyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .hudFrame()
                        .padding(12.dp)
                )
                if (onReset != null && currentName != originalName) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "元の名前に戻す",
                        color = G.Cyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onReset)
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK", color = G.Cyan, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun FolderPickDialog(
    folders: List<AppFolder>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Text(
                "フォルダにしまう",
                color = G.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "選んだアプリは A〜Z 一覧から隠れます。",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(10.dp))
                folders.forEach { folder ->
                    Text(
                        text = "\u25A3  ${folder.name}  (${folder.packageNames.size})",
                        color = G.White,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(folder.id) }
                            .padding(vertical = 10.dp)
                    )
                }
                Text(
                    text = "\uFF0B  新しいフォルダ…",
                    color = G.Cyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreateNew)
                        .padding(vertical = 10.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

@Composable
private fun SectionLabel(text: String, onAdd: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 10.dp)
                .background(G.Red, SkewTag)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = G.Dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        if (onAdd != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "\uFF0B",
                color = G.Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onAdd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun rememberClock(): Pair<String, String> {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (EEE)", Locale.JAPAN))
    return time to date
}

@Composable
private fun rememberBatteryPercent(): Int {
    val context = LocalContext.current
    var pct by remember { mutableStateOf(-1) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                pct = percentFrom(i)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        percentFrom(sticky).let { if (it >= 0) pct = it }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return pct
}

private fun percentFrom(intent: Intent?): Int {
    intent ?: return -1
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else -1
}
