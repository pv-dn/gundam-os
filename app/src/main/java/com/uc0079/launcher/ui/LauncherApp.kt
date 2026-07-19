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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc0079.launcher.AppInfo
import com.uc0079.launcher.LauncherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen { HOME, ALL }

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    GundamTheme {
        var screen by remember { mutableStateOf(Screen.HOME) }

        ScrimBackground(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm) { screen = Screen.ALL }
                Screen.ALL -> {
                    BackHandler { screen = Screen.HOME }
                    AllAppsScreen(vm) { screen = Screen.HOME }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* HOME                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun HomeScreen(vm: LauncherViewModel, onOpenAll: () -> Unit) {
    val favorites = vm.favoriteApps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -90f) onOpenAll() },
                    onDragCancel = { total = 0f }
                )
            }
            .padding(horizontal = 18.dp)
    ) {
        HudHeader(unitCount = vm.apps.size)

        Spacer(Modifier.height(20.dp))
        SectionLabel("FAVORITE UNITS / お気に入り")
        Spacer(Modifier.height(6.dp))

        Column(Modifier.weight(1f)) {
            if (favorites.isEmpty()) {
                Text(
                    text = "登録なし — 下の [ALL UNITS] を開き、\nアプリを長押しして「お気に入りに追加」してください。",
                    color = G.Dim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                favorites.forEach { app ->
                    AppRow(
                        app = app,
                        isFavorite = true,
                        onLaunch = { vm.launchApp(app.packageName) },
                        onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                        onInfo = { vm.openAppInfo(app.packageName) },
                        onUninstall = { vm.uninstall(app.packageName) },
                        labelSize = 22.sp,
                        iconSize = 34.dp
                    )
                }
            }
        }

        AllUnitsButton(onOpenAll)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HudHeader(unitCount: Int) {
    val (time, date) = rememberClock()
    val battery = rememberBatteryPercent()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .hudFrame()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "A.E.U.G. // MSZ-006",
                color = G.Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (battery >= 0) "PWR $battery%" else "PWR --",
                color = if (battery in 0..15) G.Red else G.Yellow,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = time,
            color = G.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = date,
                color = G.Dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "U.C.0087 \u00B7 UNITS:$unitCount",
                color = G.Dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
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

private data class ListRow(val header: Char?, val app: AppInfo?)

@Composable
private fun AllAppsScreen(vm: LauncherViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filtered = remember(vm.apps, query) {
        if (query.isBlank()) vm.apps
        else vm.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    val rows = remember(filtered, query) {
        if (query.isNotBlank()) {
            filtered.map { ListRow(null, it) }
        } else {
            val grouped = LinkedHashMap<Char, MutableList<AppInfo>>()
            filtered.forEach { app ->
                val c = firstLetter(app.label)
                grouped.getOrPut(c) { mutableListOf() }.add(app)
            }
            buildList {
                grouped.forEach { (letter, list) ->
                    add(ListRow(letter, null))
                    list.forEach { add(ListRow(null, it)) }
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

        Row(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(rows.size) { i ->
                    val row = rows[i]
                    if (row.header != null) {
                        Text(
                            text = row.header.toString(),
                            color = G.Cyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                        )
                    } else if (row.app != null) {
                        val app = row.app
                        AppRow(
                            app = app,
                            isFavorite = vm.isFavorite(app.packageName),
                            onLaunch = { vm.launchApp(app.packageName) },
                            onToggleFavorite = { vm.toggleFavorite(app.packageName) },
                            onInfo = { vm.openAppInfo(app.packageName) },
                            onUninstall = { vm.uninstall(app.packageName) },
                            labelSize = 18.sp,
                            iconSize = 30.dp
                        )
                    }
                }
            }

            if (query.isBlank() && letterIndex.isNotEmpty()) {
                AlphabetScroller(
                    letters = letterIndex.keys.toList(),
                    onLetter = { c ->
                        letterIndex[c]?.let { idx ->
                            scope.launch { listState.scrollToItem(idx) }
                        }
                    }
                )
            }
        }
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
private fun AlphabetScroller(letters: List<Char>, onLetter: (Char) -> Unit) {
    var heightPx by remember { mutableStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(26.dp)
            .padding(start = 4.dp)
            .onSizeChanged { heightPx = if (it.height > 0) it.height else 1 }
            .pointerInput(letters, heightPx) {
                awaitEachGesture {
                    fun pick(y: Float) {
                        if (letters.isEmpty()) return
                        val idx = ((y / heightPx) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.size - 1)
                        onLetter(letters[idx])
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
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { c ->
            Text(
                text = c.toString(),
                color = G.Cyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* APP ROW                                                             */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    isFavorite: Boolean,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = { menuOpen = true }
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = app.label,
                color = G.White,
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isFavorite) {
                Text(text = "\u2605", color = G.Yellow, fontSize = 12.sp)
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(G.PanelStrong)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavorite) "お気に入りから削除" else "お気に入りに追加",
                        color = G.White,
                        fontFamily = FontFamily.Monospace
                    )
                },
                onClick = { menuOpen = false; onToggleFavorite() }
            )
            DropdownMenuItem(
                text = { Text("アプリ情報", color = G.White, fontFamily = FontFamily.Monospace) },
                onClick = { menuOpen = false; onInfo() }
            )
            DropdownMenuItem(
                text = { Text("アンインストール", color = G.Red, fontFamily = FontFamily.Monospace) },
                onClick = { menuOpen = false; onUninstall() }
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

private fun firstLetter(label: String): Char {
    val ch = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (ch in 'A'..'Z') ch else '#'
}

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
