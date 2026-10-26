package com.tamil.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun LazyGridScope.header(text: String) = item(span = { GridItemSpan(maxLineSpan) }) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(8.dp, 12.dp, 8.dp, 4.dp))
}

@Composable
fun Drawer(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var q by remember { mutableStateOf("") }
    var contactsOk by remember { mutableStateOf(hasContactsPermission(ctx)) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsOk = it }

    val visible = vm.apps.filter { it.pkg !in vm.hidden }
    val usage = vm.usage
    val filtered = if (q.isBlank()) visible else visible.filter { it.label.contains(q, ignoreCase = true) }
    val recent = usage.entries.sortedByDescending { it.value.second }.mapNotNull { e -> visible.find { it.pkg == e.key } }.take(vm.cols)
    val frequent = usage.entries.sortedByDescending { it.value.first }.mapNotNull { e -> visible.find { it.pkg == e.key } }.take(vm.cols)
    val contacts by produceState(emptyList<Pair<String, Uri>>(), q, contactsOk) {
        value = withContext(Dispatchers.IO) { searchContacts(ctx, q) }
    }
    val cols = if (vm.minimal) 1 else vm.cols
    val othersLabel = stringResource(R.string.others)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = vm.dim))
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding().imePadding()
    ) {
        OutlinedTextField(
            q, { q = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton({ vm.screen = LauncherViewModel.Screen.SETTINGS }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (filtered.size == 1) vm.open(ctx, filtered[0].pkg) else focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth().padding(12.dp).pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f }, onVerticalDrag = { _, d -> total += d },
                    onDragEnd = { if (total > 100f) vm.screen = LauncherViewModel.Screen.HOME }
                )
            }
        )
        LazyVerticalGrid(GridCells.Fixed(cols), Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            if (q.isBlank()) {
                if (recent.isNotEmpty()) {
                    header(stringResource(R.string.recent))
                    items(recent, key = { "r" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                }
                if (frequent.isNotEmpty()) {
                    header(stringResource(R.string.most_used))
                    items(frequent, key = { "f" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                }
                when (vm.sortMode) {
                    2 -> visible.groupBy { it.category }.entries
                        .sortedBy { (c, _) -> if (c < 0) "~" else ApplicationInfo.getCategoryTitle(ctx, c)?.toString() ?: "~" }
                        .forEach { (c, list) ->
                            header(if (c < 0) othersLabel else ApplicationInfo.getCategoryTitle(ctx, c)?.toString() ?: othersLabel)
                            items(list, key = { "c" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                        }
                    1 -> items(visible.sortedWith(compareByDescending<AppInfo> { usage[it.pkg]?.first ?: 0 }.thenBy { it.label.lowercase() }),
                        key = { "a" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                    else -> items(visible, key = { "a" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                }
            } else {
                items(filtered, key = { "s" + it.pkg }) { AppTile(vm, it.pkg, Place.DRAWER) }
                header(stringResource(R.string.contacts))
                if (!contactsOk) item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton({ askContacts.launch(android.Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.grant_contacts)) }
                }
                items(contacts, span = { GridItemSpan(maxLineSpan) }) { (name, uri) ->
                    Text(name, Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.search_web, q),
                        Modifier.fillMaxWidth().clickable {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
                        }.padding(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
