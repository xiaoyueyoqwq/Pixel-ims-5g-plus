package io.github.xiaoyueyoqwq.ims.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.flyfishxu.kadb.Kadb
import io.github.xiaoyueyoqwq.ims.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class AdbController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageName: String = appContext.packageName
    private val pairingDeviceName: String = appContext.getString(R.string.app_name)

    suspend fun isAuthorized(port: Int): Boolean = withContext(Dispatchers.IO) {
        AdbIdentity.ensure(appContext)
        val ok = withTimeoutOrNull(AUTH_TIMEOUT_MS.toLong()) {
            runCatching {
                Kadb.create(HOST, port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                    kadb.shell("echo 1").exitCode == 0
                }
            }.getOrDefault(false)
        } ?: false
        if (ok) AdbPortStore.saveConnectPort(appContext, port)
        ok
    }

    suspend fun pair(port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        AdbIdentity.ensure(appContext)
        runCatching {
            withTimeout(PAIR_TIMEOUT_MS) {
                Kadb.pair(HOST, port, code, pairingDeviceName)
            }
        }
    }

    suspend fun runApply(port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        AdbIdentity.ensure(appContext)
        runCatching {
            Kadb.create(HOST, port, CONNECT_TIMEOUT_MS, APPLY_TIMEOUT_MS).use { kadb ->
                val target = "$packageName/$packageName.PrivilegedProcess"
                val command = "am instrument -w --no-restart $target"
                val response = kadb.shell(command)
                check(response.exitCode == 0) {
                    "Exit code ${response.exitCode}: ${response.output}"
                }
                check(!response.output.contains("INSTRUMENTATION_FAILED")) {
                    response.output.trim()
                }
            }
        }
    }

    suspend fun grantPermissionsIfNeeded(port: Int) {
        val missing = grantablePermissions().filterNot(::hasPermission)
        if (missing.isEmpty()) return
        AdbIdentity.ensure(appContext)
        withContext(Dispatchers.IO) {
            runCatching {
                Kadb.create(HOST, port, AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MS).use { kadb ->
                    missing.forEach { permission ->
                        val response = kadb.shell("pm grant $packageName $permission")
                        if (response.exitCode != 0) {
                            Log.w(TAG, "pm grant $permission failed: ${response.output.trim()}")
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to self-grant permissions", it) }
        }
    }

    private fun grantablePermissions(): List<String> = buildList {
        add(Manifest.permission.WRITE_SECURE_SETTINGS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 37) {
            add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "AdbController"
        private const val HOST = "127.0.0.1"
        private const val AUTH_TIMEOUT_MS = 3_000
        private const val PAIR_TIMEOUT_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val APPLY_TIMEOUT_MS = 90_000
    }
}
