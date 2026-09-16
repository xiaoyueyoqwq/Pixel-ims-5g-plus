package io.github.xiaoyueyoqwq.ims.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

sealed interface AdbEndpoint {
    val port: Int

    data class Connect(override val port: Int) : AdbEndpoint
    data class Pairing(override val port: Int) : AdbEndpoint
}

/**
 * Wireless debugging ports via [NsdManager], same path as Shizuku's AdbMdns.
 *
 * Pairing mDNS only exists while the system "Pair with device" dialog is open.
 * Results are restricted to this device's addresses, and the port must already
 * be bound on loopback so we do not pair with another machine on the LAN.
 */
class AdbDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val retryExecutor = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "AdbDiscovery-retry").apply { isDaemon = true }
    }.apply {
        executeExistingDelayedTasksAfterShutdownPolicy = false
        setRejectedExecutionHandler { _, _ -> }
    }

    fun discover(): Flow<AdbEndpoint> = callbackFlow {
        if (nsdManager == null) {
            retryExecutor.shutdown()
            close()
            return@callbackFlow
        }
        logLocalNetworkAccess()
        val connectListener = discoveryListener(MATCH_CONNECT) { port ->
            trySend(AdbEndpoint.Connect(port))
        }
        val pairingListener = discoveryListener(MATCH_PAIRING) { port ->
            trySend(AdbEndpoint.Pairing(port))
        }
        runCatching {
            nsdManager.discoverServices(SERVICE_CONNECT, NsdManager.PROTOCOL_DNS_SD, connectListener)
        }.onFailure { Log.e(TAG, "start connect discovery failed", it) }
        runCatching {
            nsdManager.discoverServices(SERVICE_PAIRING, NsdManager.PROTOCOL_DNS_SD, pairingListener)
        }.onFailure { Log.e(TAG, "start pairing discovery failed", it) }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(connectListener) }
            runCatching { nsdManager.stopServiceDiscovery(pairingListener) }
            retryExecutor.shutdown()
        }
    }

    suspend fun awaitPairingPort(timeoutMs: Long = DISCOVER_TIMEOUT_MS): Int? {
        return withTimeoutOrNull(timeoutMs) {
            discover().filterIsInstance<AdbEndpoint.Pairing>().first().port
        }
    }

    suspend fun awaitConnectPort(timeoutMs: Long = DISCOVER_TIMEOUT_MS): Int? {
        return withTimeoutOrNull(timeoutMs) {
            discover().filterIsInstance<AdbEndpoint.Connect>().first().port
        }
    }

    private fun discoveryListener(
        serviceTypeMatch: String,
        onPort: (Int) -> Unit,
    ) = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(type: String?, errorCode: Int) {
            Log.e(TAG, "start failed $type code=$errorCode")
            if (errorCode == FAILURE_BAD_PERMISSION) {
                Log.e(TAG, "NSD blocked; ACCESS_LOCAL_NETWORK not granted")
            }
        }

        override fun onStopDiscoveryFailed(type: String?, errorCode: Int) {
            Log.e(TAG, "stop failed $type code=$errorCode")
        }

        override fun onDiscoveryStarted(type: String?) {
            Log.i(TAG, "started $type")
        }

        override fun onDiscoveryStopped(type: String?) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.contains(serviceTypeMatch)) return
            Log.i(TAG, "found ${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "resolve failed $serviceTypeMatch code=$errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    emitWhenPortReady(info, serviceTypeMatch, onPort)
                }
            })
        }
    }

    private fun emitWhenPortReady(
        info: NsdServiceInfo,
        serviceTypeMatch: String,
        onPort: (Int) -> Unit,
        attemptsLeft: Int = LOOPBACK_RETRY_COUNT,
    ) {
        if (isLocalAdbPort(info)) {
            Log.i(TAG, "resolved $serviceTypeMatch port ${info.port}")
            onPort(info.port)
            return
        }
        val host = info.host?.hostAddress
        if (!isAddressOnThisDevice(host)) {
            Log.w(
                TAG,
                "ignore $serviceTypeMatch ${info.host}:${info.port} (not local)",
            )
            return
        }
        if (attemptsLeft <= 1) {
            Log.w(
                TAG,
                "ignore $serviceTypeMatch ${info.host}:${info.port} (loopback not bound)",
            )
            return
        }
        Log.i(
            TAG,
            "port ${info.port} not yet on loopback; retry ${attemptsLeft - 1}",
        )
        if (retryExecutor.isShutdown) return
        try {
            retryExecutor.schedule(
                { emitWhenPortReady(info, serviceTypeMatch, onPort, attemptsLeft - 1) },
                LOOPBACK_RETRY_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun isLocalAdbPort(info: NsdServiceInfo): Boolean {
        val host = info.host ?: return false
        if (info.port <= 0) return false
        if (!isAddressOnThisDevice(host.hostAddress)) return false
        return isPortBoundOnLoopback(info.port)
    }

    private fun logLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < 37) return
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "ACCESS_LOCAL_NETWORK denied; pairing mDNS may miss")
        }
    }

    companion object {
        private const val TAG = "AdbDiscovery"
        // NsdManager.FAILURE_BAD_PERMISSION (API 37): discovery without
        // ACCESS_LOCAL_NETWORK. Hardcoded so this compiles on older stubs.
        private const val FAILURE_BAD_PERMISSION = 5
        private const val SERVICE_CONNECT = "_adb-tls-connect._tcp"
        private const val SERVICE_PAIRING = "_adb-tls-pairing._tcp"
        private const val MATCH_CONNECT = "adb-tls-connect"
        private const val MATCH_PAIRING = "adb-tls-pairing"
        const val DISCOVER_TIMEOUT_MS = 5_000L
        private const val LOOPBACK_RETRY_COUNT = 10
        private const val LOOPBACK_RETRY_MS = 200L

        internal fun isAddressOnThisDevice(hostAddress: String?): Boolean {
            if (hostAddress.isNullOrEmpty()) return false
            val normalized = hostAddress.substringBefore('%')
            if (normalized == "127.0.0.1" || normalized == "::1") return true
            return runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { iface ->
                    iface.inetAddresses.toList().any { addr ->
                        addr.hostAddress?.substringBefore('%') == normalized
                    }
                }
            }.getOrDefault(false)
        }

        /**
         * Shizuku: if we can bind 127.0.0.1:port, nothing is listening there.
         * adbd pairing/connect is bound on loopback as well as the LAN address.
         */
        internal fun isPortBoundOnLoopback(port: Int): Boolean {
            return try {
                ServerSocket().use { server ->
                    server.bind(InetSocketAddress("127.0.0.1", port), 1)
                    false
                }
            } catch (_: IOException) {
                true
            }
        }
    }
}
