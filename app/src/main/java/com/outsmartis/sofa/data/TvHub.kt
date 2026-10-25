package com.outsmartis.sofa.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.outsmartis.sofa.tv.Identity
import com.outsmartis.sofa.tv.Keys
import com.outsmartis.sofa.tv.Link
import com.outsmartis.sofa.tv.PairingSession
import com.outsmartis.sofa.tv.RemoteSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Every paired TV, its live session, and the one the remote is pointed at. */
class TvHub(context: Context) {
    private val store = TvStore(context)
    val settings = Settings(context)
    val discovery = Discovery(context)
    private val identityFile = File(context.filesDir, "client.p12")
    private val identity: Identity by lazy { Identity.loadOrCreate(identityFile, "Sofa on ${Build.MODEL}") }

    private val _tvs = MutableStateFlow(store.all())
    val tvs: StateFlow<List<SavedTv>> = _tvs.asStateFlow()

    private val _selected = MutableStateFlow(store.selectedId ?: _tvs.value.firstOrNull()?.id)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val sessions = HashMap<String, RemoteSession>()
    private var pairing: PairingSession? = null
    private var foreground = false

    fun session(id: String): RemoteSession? {
        val tv = _tvs.value.firstOrNull { it.id == id } ?: return null
        sessions[id]?.let { if (it.host == tv.host) return it else it.close() }
        return RemoteSession(identity, tv.host, log = { Log.d("Sofa/${tv.name}", it) }).also { sessions[id] = it }
    }

    fun current(): RemoteSession? = _selected.value?.let(::session)

    fun select(id: String) {
        _selected.value = id
        store.selectedId = id
        if (foreground) current()?.start()
    }

    /** Called when the app comes to the front / goes away: TVs drop idle remotes, so connect only while visible. */
    fun onForeground(visible: Boolean) {
        foreground = visible
        if (visible) {
            discovery.start()
            current()?.start()
        } else {
            discovery.stop()
            sessions.values.forEach { it.stop() }
        }
    }

    /** mDNS sees a paired TV at a new address (DHCP): follow it. */
    fun refreshHosts(found: Map<String, FoundTv>) {
        val changed = _tvs.value.map { tv -> found[tv.id]?.takeIf { it.host != tv.host }?.let { tv.copy(host = it.host) } ?: tv }
        if (changed != _tvs.value) {
            _tvs.value = changed
            store.save(changed)
            if (foreground) current()?.let { it.stop(); it.start() }
        }
    }

    suspend fun startPairing(host: String) = withContext(Dispatchers.IO) {
        pairing?.close()
        val p = PairingSession(identity, host)
        pairing = p
        try {
            p.start("Sofa on ${Build.MODEL}")
        } catch (e: Exception) {
            p.close()
            pairing = null
            throw e
        }
    }

    suspend fun finishPairing(id: String, name: String, host: String, code: String) = withContext(Dispatchers.IO) {
        val p = pairing ?: error("Pairing not started")
        try {
            p.finish(code)
        } finally {
            p.close()
            pairing = null
        }
        val tv = SavedTv(id, name, host)
        val list = _tvs.value.filterNot { it.id == id } + tv
        _tvs.value = list
        store.save(list)
        sessions.remove(id)?.close()
        withContext(Dispatchers.Main) { select(id) }
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
    }

    fun rename(id: String, name: String) {
        val list = _tvs.value.map { if (it.id == id) it.copy(name = name) else it }
        _tvs.value = list
        store.save(list)
    }

    fun forget(id: String) {
        sessions.remove(id)?.close()
        val list = _tvs.value.filterNot { it.id == id }
        _tvs.value = list
        store.save(list)
        if (_selected.value == id) list.firstOrNull()?.let { select(it.id) } ?: run { _selected.value = null; store.selectedId = null }
    }

    /** Turns off every paired TV that is on, e.g. at bedtime. Returns how many were switched off. */
    suspend fun allOff(): Int = withContext(Dispatchers.Main) {
        var off = 0
        for (tv in _tvs.value) {
            val s = session(tv.id) ?: continue
            s.start()
            val state = withTimeoutOrNull(6_000) { s.state.first { it.link == Link.Connected } }
            // POWER toggles, so only press it on TVs that report being on.
            if (state?.isOn == true) {
                s.key(Keys.POWER)
                off++
                delay(300) // let the key leave before the socket closes
            }
            if (!foreground || tv.id != _selected.value) s.stop()
        }
        off
    }
}
