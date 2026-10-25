package com.outsmartis.sofa.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outsmartis.sofa.data.SavedTv
import com.outsmartis.sofa.data.TvHub
import com.outsmartis.sofa.tv.Keys
import com.outsmartis.sofa.tv.Link
import com.outsmartis.sofa.tv.RemoteSession
import com.outsmartis.sofa.tv.TvState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private data class TvApp(val name: String, val link: String, val color: Color)

// App links the TV's remote service opens; the TV picks the installed app that handles them.
private val APPS = listOf(
    TvApp("YouTube", "https://www.youtube.com", Color(0xFFFF3B30)),
    TvApp("Netflix", "https://www.netflix.com/title", Color(0xFFE50914)),
    TvApp("Prime Video", "https://app.primevideo.com", Color(0xFF1FA2FF)),
    TvApp("Disney+", "https://www.disneyplus.com", Color(0xFF3D6BFF)),
    TvApp("Max", "https://play.max.com", Color(0xFF7B5CFF)),
    TvApp("Spotify", "spotify://", Color(0xFF1ED760)),
    TvApp("Plex", "plex://", Color(0xFFE5A00D)),
    TvApp("Twitch", "twitch://home", Color(0xFF9146FF)),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(hub: TvHub, onAddTv: () -> Unit) {
    val tvs by hub.tvs.collectAsState()
    val selectedId by hub.selected.collectAsState()
    val selected = tvs.firstOrNull { it.id == selectedId } ?: tvs.first()
    val session: RemoteSession? = remember(selected) { hub.session(selected.id) }
    val state by (session?.state ?: remember { MutableStateFlow(TvState()) }).collectAsState()
    val settings = hub.settings
    var touchpad by remember { mutableStateOf(settings.touchpad) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedTv?>(null) }
    var repair by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var autoOpened by remember { mutableStateOf(false) }
    val presser = rememberPresser(settings.haptics) { code, dir -> session?.key(code, dir) }

    LaunchedEffect(selected.id) { if (selectedId != selected.id) hub.select(selected.id) else session?.start() }
    // Follow the TV: open the phone keyboard when a text field gets focus there, close it when it goes.
    LaunchedEffect(state.textField != null) {
        if (state.textField != null && settings.autoKeyboard && !typing) {
            typing = true
            autoOpened = true
        }
        if (state.textField == null && autoOpened) {
            typing = false
            autoOpened = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // TV switcher
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tvs, key = { it.id }) { tv ->
                    val active = tv.id == selected.id
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (active) Accent.copy(alpha = 0.18f) else Surface1)
                            .combinedClickable(onClick = { hub.select(tv.id) }, onLongClick = { editing = tv })
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            StatusDot(linkColor(state.link))
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(tv.name, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) Accent else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }
                item {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Surface1).clickable(onClick = onAddTv).padding(9.dp)) {
                        Icon(Icons.Rounded.Add, "Add TV", tint = TextDim)
                    }
                }
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, "Settings", tint = TextDim) }
        }

        StatusLine(state, onRepair = { repair = true }, onRetry = { session?.stop(); session?.start() })

        if (typing) {
            TypingPanel(session, state, presser, onClose = { typing = false; autoOpened = false })
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KeyIcon(presser, Keys.POWER, Icons.Rounded.PowerSettingsNew, "Power", tint = Bad)
                KeyIcon(presser, Keys.TV_INPUT, Icons.Rounded.Input, "Input")
                KeyIcon(presser, Keys.SETTINGS, Icons.Rounded.Settings, "TV settings")
                KeyIcon(presser, Keys.SEARCH, Icons.Rounded.Search, "Search")
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(if (typing) Accent else Surface2).clickable { typing = !typing; autoOpened = false }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Keyboard, "Keyboard", tint = if (typing) Color.Black else MaterialTheme.colorScheme.onSurface)
                }
            }

            if (!typing) {
                if (touchpad) Touchpad(presser, Modifier.widthIn(max = 360.dp)) else DPad(presser, Modifier.widthIn(max = 300.dp).fillMaxWidth())
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                KeyIcon(presser, Keys.BACK, Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                KeyIcon(presser, Keys.HOME, Icons.Rounded.Home, "Home")
                KeyIcon(presser, Keys.APP_SWITCH, Icons.Rounded.Apps, "Recent apps")
                KeyIcon(presser, Keys.MENU, Icons.Rounded.Menu, "Menu")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Rocker(presser, Keys.VOLUME_UP, Keys.VOLUME_DOWN, state.volume?.let { if (it.muted) "muted" else "vol ${it.level}" } ?: "vol")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyIcon(presser, Keys.REWIND, Icons.Rounded.FastRewind, "Rewind", size = 50.dp, repeat = true)
                        KeyButton(presser, Keys.PLAY_PAUSE, size = 50.dp, color = Accent.copy(alpha = 0.2f)) { Icon(Icons.Rounded.PlayArrow, "Play/pause", tint = Accent) }
                        KeyIcon(presser, Keys.FAST_FORWARD, Icons.Rounded.FastForward, "Fast forward", size = 50.dp, repeat = true)
                    }
                    KeyIcon(presser, Keys.VOLUME_MUTE, Icons.AutoMirrored.Rounded.VolumeOff, "Mute", size = 50.dp)
                }
                Rocker(presser, Keys.CHANNEL_UP, Keys.CHANNEL_DOWN, "ch")
            }
        }

        if (!typing) {
            LazyRow(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                items(APPS) { app ->
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(app.color.copy(alpha = 0.16f))
                            .clickable { presser.haptic(); session?.launch(app.link) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text(app.name, color = app.color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(hub, touchpad, onTouchpad = { touchpad = it; settings.touchpad = it }, onClose = { showSettings = false })
    }
    editing?.let { tv ->
        TvDialog(tv, onRename = { hub.rename(tv.id, it) }, onRepair = { editing = null; hub.select(tv.id); repair = true }, onForget = { hub.forget(tv.id) }, onClose = { editing = null })
    }
    if (repair) {
        PairDialog(hub, selected.id, selected.name, selected.host, onClose = { repair = false })
    }
}

private fun linkColor(link: Link) = when (link) {
    Link.Connected -> Ok
    Link.Connecting, Link.Idle -> Warn
    else -> Bad
}

@Composable
private fun StatusLine(state: TvState, onRepair: () -> Unit, onRetry: () -> Unit) {
    val text = when (state.link) {
        Link.Connected -> listOf(state.vendor, state.model).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Connected" } +
            if (state.isOn == false) " · screen off" else ""
        Link.Connecting, Link.Idle -> "Connecting…"
        Link.NeedsPairing -> "This TV doesn't know this phone anymore."
        Link.Unreachable -> "Can't reach the TV. Is it on and on this Wi-Fi?"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        when (state.link) {
            Link.NeedsPairing -> TextButton(onClick = onRepair) { Text("Pair again") }
            Link.Unreachable -> TextButton(onClick = onRetry) { Text("Retry") }
            else -> Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * The phone keyboard, mirrored live into the TV's text field. Every edit (typing, autocorrect,
 * swipe typing, paste, backspace) goes over as the whole field, so what you see is what the TV has.
 */
@Composable
private fun TypingPanel(session: RemoteSession?, state: TvState, presser: Presser, onClose: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(state.textField?.value.orEmpty(), TextRange(state.textField?.value?.length ?: 0))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var lastLocalEdit by remember { mutableStateOf(0L) }
    // Mirror the TV's field (it echoes every edit, and changes on its own when another field gets
    // focus or a suggestion is picked), but not while the user is mid-word: echoes lag behind.
    LaunchedEffect(state.textField?.value) {
        val tvText = state.textField?.value ?: return@LaunchedEffect
        val quiet = 1_200 - (System.currentTimeMillis() - lastLocalEdit)
        if (quiet > 0) kotlinx.coroutines.delay(quiet)
        if (tvText != value.text) value = TextFieldValue(tvText, TextRange(tvText.length))
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    val label = state.textField?.label?.takeIf { it.isNotBlank() }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = value,
            onValueChange = { v ->
                val old = value.text
                value = v
                if (old != v.text) lastLocalEdit = System.currentTimeMillis()
                session?.type(old, v.text)
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(label ?: if (state.textField != null) "Type here, it appears on the TV" else "Select a text box on the TV, then type") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { presser.tap(Keys.ENTER) }),
        )
        IconButton(onClick = { presser.tap(Keys.ENTER) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter") }
        IconButton(onClick = { keyboard?.hide(); onClose() }) { Icon(Icons.Rounded.Close, "Close keyboard") }
    }
}

@Composable
private fun SettingsDialog(hub: TvHub, touchpad: Boolean, onTouchpad: (Boolean) -> Unit, onClose: () -> Unit) {
    val s = hub.settings
    var auto by remember { mutableStateOf(s.autoKeyboard) }
    var vol by remember { mutableStateOf(s.volumeKeys) }
    var hap by remember { mutableStateOf(s.haptics) }
    var allOffResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column {
                SwitchRow("Touchpad instead of arrows", touchpad, onTouchpad)
                SwitchRow("Open keyboard when the TV asks for text", auto) { auto = it; s.autoKeyboard = it }
                SwitchRow("Phone volume buttons control the TV", vol) { vol = it; s.volumeKeys = it }
                SwitchRow("Vibrate on press", hap) { hap = it; s.haptics = it }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                OutlinedButton(onClick = {
                    allOffResult = "Turning off…"
                    scope.launch {
                        val n = hub.allOff()
                        allOffResult = if (n == 0) "No TV was on." else "Turned off $n TV${if (n > 1) "s" else ""}."
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PowerSettingsNew, null)
                    Text("  Turn off every TV")
                }
                allOffResult?.let { Text(it, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                Text("Sofa ${com.outsmartis.sofa.BuildConfig.VERSION_NAME} · no ads, no tracking", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun TvDialog(tv: SavedTv, onRename: (String) -> Unit, onRepair: () -> Unit, onForget: () -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(tv.name) }
    var confirmForget by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tv.name) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                Text(tv.host, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRepair) { Text("Pair again") }
                    OutlinedButton(onClick = { if (confirmForget) { onForget(); onClose() } else confirmForget = true }) {
                        Text(if (confirmForget) "Tap again to forget" else "Forget", color = Bad)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onRename(name.trim()); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
