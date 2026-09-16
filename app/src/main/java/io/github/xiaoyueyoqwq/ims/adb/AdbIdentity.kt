package io.github.xiaoyueyoqwq.ims.adb

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

/**
 * Persist kadb's ADB host key so Android treats this app as one device.
 *
 * The library default is [com.flyfishxu.kadb.cert.InMemoryPrivateKeyStore],
 * which mints a new key every process and shows a second "Ims" after reboot.
 */
object AdbIdentity {
    private const val TAG = "AdbIdentity"
    private const val KEY_FILE = "adbkey.pem"

    @Volatile
    private var ready = false
    private val lock = Any()

    fun ensure(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            val filesDir = context.applicationContext.filesDir
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                Log.w(TAG, "filesDir not ready yet: $filesDir")
                return
            }
            val keyFile = File(filesDir, KEY_FILE)
            try {
                KadbCert.configure(
                    OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
                    KadbCertPolicy(autoHealInvalidPrivateKey = true),
                )
                KadbCert.ensureReady()
                ready = true
                Log.i(TAG, "host identity ready at ${keyFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "host identity not ready", e)
            }
        }
    }
}
