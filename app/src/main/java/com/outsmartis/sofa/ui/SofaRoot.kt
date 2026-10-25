package com.outsmartis.sofa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outsmartis.sofa.data.TvHub
import kotlinx.coroutines.launch

@Composable
fun SofaRoot(hub: TvHub) {
    val tvs by hub.tvs.collectAsState()
    val found by hub.discovery.found.collectAsState()
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(found) { hub.refreshHosts(found) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (tvs.isEmpty() || adding) {
            AddTvScreen(hub, canClose = tvs.isNotEmpty(), onDone = { adding = false })
        } else {
            RemoteScreen(hub, onAddTv = { adding = true })
        }
    }
}

private data class PairTarget(val id: String, val name: String, val host: String)

@Composable
fun AddTvScreen(hub: TvHub, canClose: Boolean, onDone: () -> Unit) {
    val found by hub.discovery.found.collectAsState()
    val tvs by hub.tvs.collectAsState()
    var target by remember { mutableStateOf<PairTarget?>(null) }
    var manual by remember { mutableStateOf(false) }
    val fresh = found.values.filter { f -> tvs.none { it.id == f.id } }.sortedBy { it.id }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add a TV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (canClose) TextButton(onClick = onDone) { Text("Close") }
        }
        Text(
            "TVs on this Wi-Fi show up below. Pick one and type the code it shows.",
            color = TextDim, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(fresh, key = { it.id }) { tv ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1)
                        .clickable { target = PairTarget(tv.id, tv.id, tv.host) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tv, null, tint = Accent)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(tv.id, fontWeight = FontWeight.SemiBold)
                        Text(tv.host, color = TextDim, fontSize = 13.sp)
                    }
                    Text("Pair", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TextDim)
                    Text("  Looking for TVs…", color = TextDim, fontSize = 13.sp)
                }
            }
            item {
                TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) { Text("Enter the TV's IP address instead") }
            }
            item {
                Text(
                    "Works with Android TV and Google TV: Xiaomi Mi TV / Mi Box / TV Stick, Sony, TCL, Philips, Hisense, Chromecast with Google TV, Nvidia Shield. The phone and the TV must be on the same network.",
                    color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    if (manual) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("TV address") },
            text = {
                OutlinedTextField(
                    ip, { ip = it.trim() }, singleLine = true, placeholder = { Text("192.168.1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            },
            confirmButton = {
                TextButton(enabled = ip.isNotBlank(), onClick = { manual = false; target = PairTarget(ip, "TV $ip", ip) }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } },
        )
    }

    target?.let { t ->
        PairDialog(hub, t.id, t.name, t.host, onClose = { paired ->
            target = null
            if (paired) onDone()
        })
    }
}

/** Asks the TV to show a code, then sends the code back. Also used to re-pair a known TV. */
@Composable
fun PairDialog(hub: TvHub, id: String, name: String, host: String, onClose: (paired: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("starting") }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var tvName by remember { mutableStateOf(name) }
    val focus = remember { FocusRequester() }

    fun begin() {
        step = "starting"
        error = null
        code = ""
        scope.launch {
            runCatching { hub.startPairing(host) }
                .onSuccess { step = "code" }
                .onFailure { error = "Couldn't reach the TV at $host. Is it on and on this Wi-Fi? (${it.message})"; step = "error" }
        }
    }

    fun submit() {
        step = "checking"
        scope.launch {
            runCatching { hub.finishPairing(id, tvName.ifBlank { name }, host, code) }
                .onSuccess { onClose(true) }
                .onFailure { error = it.message ?: "Pairing failed"; step = "error" }
        }
    }

    LaunchedEffect(Unit) { begin() }
    LaunchedEffect(step) { if (step == "code") runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = { hub.cancelPairing(); onClose(false) },
        title = { Text("Pair with $name") },
        text = {
            Column {
                when (step) {
                    "starting" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Asking the TV for a code…")
                    }
                    "code", "checking" -> {
                        Text("Type the code shown on the TV.", color = TextDim)
                        OutlinedTextField(
                            code,
                            { v ->
                                code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                                if (code.length == 6) submit()
                            },
                            singleLine = true,
                            enabled = step == "code",
                            textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, letterSpacing = 6.sp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.padding(top = 12.dp).focusRequester(focus),
                        )
                        OutlinedTextField(tvName, { tvName = it }, singleLine = true, label = { Text("Name (e.g. Living room)") }, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> Text(error ?: "", color = Bad)
                }
            }
        },
        confirmButton = {
            when (step) {
                "error" -> Button(onClick = ::begin) { Text("Try again") }
                "code" -> Button(enabled = code.length == 6, onClick = ::submit) { Text("Pair") }
                else -> Spacer(Modifier.height(1.dp))
            }
        },
        dismissButton = { TextButton(onClick = { hub.cancelPairing(); onClose(false) }) { Text("Cancel") } },
    )
}

@Composable
fun StatusDot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}
