package com.outsmartis.sofa.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

enum class Link { Idle, Connecting, Connected, NeedsPairing, Unreachable }

data class Volume(val level: Int, val max: Int, val muted: Boolean)

/** What the TV's focused text field holds, when the TV is asking for text. */
data class TextField(val value: String, val label: String, val app: String)

data class TvState(
    val link: Link = Link.Idle,
    val error: String? = null,
    val isOn: Boolean? = null,
    val model: String = "",
    val vendor: String = "",
    val currentApp: String = "",
    val volume: Volume? = null,
    val textField: TextField? = null,
    val features: Int = 0,
)

/**
 * Remote control channel on port 6466. Keeps one TLS connection open, answers the TV's
 * pings, and reconnects with backoff while [start]ed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSession(
    private val identity: Identity,
    val host: String,
    private val port: Int = REMOTE_PORT,
    private val log: (String) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state.asStateFlow()

    @Volatile private var socket: SSLSocket? = null
    private var loop: Job? = null
    private var imeCounter = 0
    private var fieldCounter = 0

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                val result = runCatching { runOnce() }
                val e = result.exceptionOrNull()
                if (!isActive) break
                when {
                    e is SSLException && _state.value.link == Link.Connecting -> {
                        // The TV drops the handshake when it doesn't know our certificate.
                        _state.update { it.copy(link = Link.NeedsPairing, error = null, textField = null) }
                        log("handshake refused, needs pairing: $e")
                        return@launch
                    }
                    else -> {
                        log("connection ended: $e")
                        _state.update { it.copy(link = Link.Unreachable, error = e?.message, textField = null) }
                    }
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        runCatching { socket?.close() }
        socket = null
        _state.update { it.copy(link = Link.Idle, textField = null) }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    fun key(code: Int, direction: Int = Keys.SHORT) = send { message(10) { int(1, code); int(2, direction) } }

    fun launch(appLink: String) = send { message(90) { string(1, appLink) } }

    private val edits = Channel<Pair<String, String>>(Channel.UNLIMITED)

    init {
        // Edits run one at a time: Backspaces are key events and the insert goes through the
        // IME, and the TV applies those out of order unless we wait for its echo in between.
        scope.launch {
            for (first in edits) {
                // Coalesce whatever queued up while we waited on the TV: only the latest text matters.
                var target = first.second
                while (true) target = edits.tryReceive().getOrNull()?.second ?: break
                val st = _state.value
                val ime = st.textField != null && st.features and 4 != 0
                // Diff against what the TV actually holds, so a missed edit heals on the next one.
                val plan = Typist.plan(st.textField?.value ?: first.first, target, ime)
                repeat(plan.deletes) { key(Keys.DEL) }
                if (plan.deletes > 0 && plan.insert.isNotEmpty()) awaitField(plan.keep)
                insert(plan.insert)
                plan.keys.forEach { key(it) }
                // Every edit bumps the TV's field counter; the next insert needs the new one.
                if (ime) awaitField(target)
            }
        }
    }

    private suspend fun awaitField(value: String) {
        withTimeoutOrNull(1_000) { _state.first { it.textField == null || it.textField.value == value } }
    }

    /** Makes the TV's text field follow the phone's box going from [old] to [new]. */
    fun type(old: String, new: String) {
        if (old != new) edits.trySend(old to new)
    }

    /** Inserts [text] at the cursor of the TV's focused text field (the TV never replaces). */
    fun insert(text: String) {
        if (text.isEmpty()) return
        val pos = text.length - 1
        send {
            message(21) {
                int(1, imeCounter)
                int(2, fieldCounter)
                message(3) {
                    int(1, 1)
                    message(2) { int(1, pos); int(2, pos); string(3, text) }
                }
            }
        }
    }

    private fun runOnce() {
        _state.update { it.copy(link = Link.Connecting, error = null) }
        val s = identity.socketFactory().createSocket() as SSLSocket
        socket = s
        try {
            s.connect(InetSocketAddress(host, port), 5_000)
            // The TV pings every ~5 s; three missed pings means the link is dead.
            s.soTimeout = 16_000
            s.startHandshake()
            val input = s.inputStream
            while (true) handle(ProtoMessage.parse(readFrame(input)))
        } finally {
            runCatching { s.close() }
            if (socket === s) socket = null
        }
    }

    private fun handle(msg: ProtoMessage) {
        if (!msg.has(8)) log("<- $msg")
        msg.message(1)?.let { cfg ->
            val info = cfg.message(2)
            val features = cfg.int(1) and WANTED_FEATURES
            _state.update { it.copy(model = info?.string(1).orEmpty(), vendor = info?.string(2).orEmpty(), features = features) }
            send {
                message(1) {
                    int(1, features)
                    message(2) { int(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                }
            }
        }
        if (msg.has(2)) send { message(2) { int(1, WANTED_FEATURES) } }
        msg.message(8)?.let { ping -> send(quiet = true) { message(9) { int(1, ping.int(1)) } } }
        msg.message(40)?.let { start ->
            _state.update { it.copy(link = Link.Connected, isOn = start.bool(1), error = null) }
        }
        msg.message(50)?.let { v ->
            _state.update { it.copy(volume = Volume(v.int(7), v.int(6), v.bool(8))) }
        }
        msg.message(20)?.let { inject ->
            val app = inject.message(1)
            val field = inject.message(2)
            // Edits must echo both counters: the IME session one (app info) and the field one.
            if (app != null && app.has(1)) imeCounter = app.int(1)
            field?.let { if (it.has(1)) fieldCounter = it.int(1) }
            _state.update { s ->
                s.copy(
                    currentApp = app?.string(12)?.ifEmpty { null } ?: s.currentApp,
                    textField = field?.let { TextField(it.string(2), it.string(6), app?.string(12).orEmpty()) } ?: s.textField,
                )
            }
        }
        msg.message(21)?.let { edit ->
            // ime_counter 0 is how the TV says its keyboard closed (e.g. after Back).
            if (edit.int(1) == 0) _state.update { it.copy(textField = null) }
        }
        msg.message(22)?.let { show ->
            val field = show.message(2)
            field?.let { if (it.has(1)) fieldCounter = it.int(1) }
            // The TV echoes the field after every edit: the real text, for the phone to mirror.
            if (field != null) _state.update { s -> s.copy(textField = TextField(field.string(2), field.string(6), s.currentApp)) }
        }
        msg.message(3)?.let { log("TV error: $it") }
    }

    private fun send(quiet: Boolean = false, body: ProtoWriter.() -> Unit) {
        val bytes = proto(body)
        scope.launch(writer) {
            val s = socket ?: return@launch
            try {
                if (!quiet) log("-> ${ProtoMessage.parse(bytes)}")
                writeFrame(s.outputStream, bytes)
            } catch (e: IOException) {
                log("send failed: $e")
                runCatching { s.close() }
            }
        }
    }

    companion object {
        const val REMOTE_PORT = 6466
        // ping | key | ime | power | volume | app link
        const val WANTED_FEATURES = 1 or 2 or 4 or 32 or 64 or 512
    }
}
