package com.outsmartis.sofa.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

data class FoundTv(val id: String, val host: String)

/**
 * Finds Android / Google TVs (Xiaomi, Sony, TCL, Philips, Chromecast…) on the Wi-Fi through
 * the `_androidtvremote2._tcp` mDNS service their remote service advertises.
 */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val _found = MutableStateFlow<Map<String, FoundTv>>(emptyMap())
    val found: StateFlow<Map<String, FoundTv>> = _found.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    // Before Android 14, NsdManager resolves one service at a time.
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (listener != null) return
        lock = wifi?.createMulticastLock("sofa-discovery")?.apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.update { it - info.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.w(TAG, "discoverServices", it) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        lock?.let { runCatching { it.release() } }
        lock = null
        synchronized(pending) { pending.clear() }
    }

    private fun enqueue(info: NsdServiceInfo) {
        synchronized(pending) {
            pending.add(info)
            if (resolving) return
            resolving = true
        }
        next()
    }

    @Suppress("DEPRECATION")
    private fun next() {
        val info = synchronized(pending) {
            pending.poll() ?: run { resolving = false; return }
        }
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "resolve ${serviceInfo.serviceName} failed $errorCode")
                next()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                if (host != null && !host.contains(':')) {
                    _found.update { it + (serviceInfo.serviceName to FoundTv(serviceInfo.serviceName, host)) }
                }
                next()
            }
        })
    }

    companion object {
        private const val TAG = "SofaDiscovery"
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
