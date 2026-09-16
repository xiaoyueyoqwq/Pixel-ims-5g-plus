package io.github.xiaoyueyoqwq.ims

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Best-effort check used *before* shell privileges are available.
 *
 * Effectiveness is the 5G+ carrier-config keys, not the version stamp (an old
 * stamp can match while the bandwidth gate still blocks NR_ADVANCED) and not
 * the radio overlay (indoor LTE must not keep prompting).
 *
 * Unknown / unreadable is treated as not effective so a missed patch is preferred over silence.
 */
object FiveGStatus {
    private const val TAG = "FiveGStatus"

    fun isEffective(context: Context): Boolean = hasFiveGPlusKeys(context)

    @SuppressLint("MissingPermission")
    private fun hasFiveGPlusKeys(context: Context): Boolean {
        val cm = context.getSystemService(CarrierConfigManager::class.java) ?: return false
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return false
        return try {
            val list = sm.activeSubscriptionInfoList
            if (list.isNullOrEmpty()) return false
            list.any { info ->
                val bundle = cm.getConfigForSubId(
                    info.subscriptionId,
                    PrivilegedProcess.KEY_5G_ICON_CONFIGURATION,
                    PrivilegedProcess.KEY_ADDITIONAL_NR_ADVANCED_BANDS,
                    PrivilegedProcess.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
                )
                val icon = bundle.getString(PrivilegedProcess.KEY_5G_ICON_CONFIGURATION).orEmpty()
                val bands = bundle.getIntArray(PrivilegedProcess.KEY_ADDITIONAL_NR_ADVANCED_BANDS)
                val threshold = bundle.getInt(
                    PrivilegedProcess.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
                    Int.MIN_VALUE,
                )
                icon.contains("5G_Plus") && bands != null && bands.isNotEmpty() && threshold == 0
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "5G+ keys unreadable", e)
            false
        } catch (e: Exception) {
            Log.d(TAG, "5G+ key check failed", e)
            false
        }
    }
}
