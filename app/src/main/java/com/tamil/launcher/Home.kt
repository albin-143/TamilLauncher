package com.tamil.launcher

import android.app.WallpaperManager
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val pager = rememberPagerState(initialPage = 1) { vm.pages.size + 1 } // page 0 = widgets page

    LaunchedEffect(vm.homeSignal) { if (vm.homeSignal > 0) pager.animateScrollToPage(1) }

    // wallpaper parallax
    LaunchedEffect(pager) {
        val wm = WallpaperManager.getInstance(ctx)
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { p ->
            val n = pager.pageCount
            val x = if (n > 1) (p / (n - 1)).coerceIn(0f, 1f) else 0.5f
            try { wm.setWallpaperOffsets(view.windowToken, x, 0.5f) } catch (_: Exception) {}
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding()
            .pointerInput(vm.dblLock) {
                detectTapGestures(
                    onDoubleTap = { if (vm.dblLock) lockScreen(ctx) },
                    onLongPress = { vm.homeMenu = true }
                )
            }
            .pointerInput(vm.swipeNotif) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, d -> total += d },
                    onDragEnd = {
                        if (total < -120f) vm.screen = LauncherViewModel.Screen.DRAWER
                        else if (total > 120f && vm.swipeNotif) expandNotifications(ctx)
                    }
                )
            }
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Box(Modifier.fillMaxSize().graphicsLayer {
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val a = abs(off).coerceAtMost(1f)
                when (vm.transition) {
                    1 -> { alpha = 1f - a * 0.6f; scaleX = 1f - a * 0.15f; scaleY = scaleX }
                    2 -> { rotationY = off * -30f; cameraDistance = 12f * density }
                }
            }) { if (page == 0) WidgetsPage(vm) else HomeGrid(vm, page - 1) }
        }
        Row(Modifier.fillMaxWidth().padding(4.dp), Arrangement.Center) {
            repeat(pager.pageCount) {
                Box(
                    Modifier.padding(3.dp).size(if (it == pager.currentPage) 8.dp else 6.dp)
                        .background(Color.White.copy(if (it == pager.currentPage) 0.95f else 0.45f), CircleShape)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(0.3f)).heightIn(min = 76.dp).padding(8.dp),
            Arrangement.SpaceEvenly, Alignment.CenterVertically
        ) { vm.dock.forEach { AppTile(vm, it, Place.DOCK) } }
    }
}

@Composable
fun HomeGrid(vm: LauncherViewModel, idx: Int) {
    val items = vm.pages.getOrNull(idx) ?: return
    if (vm.minimal) {
        Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center) { items.forEach { ItemView(vm, it) } }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        repeat(vm.rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(vm.cols) { c ->
                    Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
                        items.getOrNull(r * vm.cols + c)?.let { ItemView(vm, it) }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemView(vm: LauncherViewModel, item: HomeItem) = when (item) {
    is HomeItem.App -> AppTile(vm, item.pkg, Place.HOME)
    is HomeItem.Folder -> FolderTile(vm, item)
}

@Composable
fun WidgetsPage(vm: LauncherViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ClockCard() }
        items(vm.widgetIds, key = { it }) { id ->
            val info = vm.awm.getAppWidgetInfo(id)
            if (info != null) {
                val h = with(LocalDensity.current) { info.minHeight.toDp() }.coerceAtLeast(80.dp)
                AndroidView(
                    factory = { c -> vm.widgetHost.createView(c, id, info) },
                    modifier = Modifier.fillMaxWidth().height(h)
                )
            }
        }
    }
}

@Composable
fun ClockCard() {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(15_000) } }
    val time = remember(now / 60_000) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)) }
    val date = remember(now / 3_600_000) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    Column {
        Text(time, color = Color.White, fontSize = 56.sp)
        Text(date, color = Color.White.copy(0.9f), fontSize = 18.sp)
    }
}

@Composable
fun HomeMenuDialog(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { vm.homeMenu = false },
        confirmButton = {},
        text = {
            Column {
                TextButton({ vm.homeMenu = false; vm.screen = LauncherViewModel.Screen.SETTINGS }) { Text(stringResource(R.string.settings)) }
                TextButton({
                    vm.homeMenu = false
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), ctx.getString(R.string.wallpaper)))
                }) { Text(stringResource(R.string.wallpaper)) }
            }
        }
    )
}
