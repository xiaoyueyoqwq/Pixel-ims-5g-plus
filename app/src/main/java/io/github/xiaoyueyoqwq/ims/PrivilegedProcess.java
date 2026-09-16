package io.github.xiaoyueyoqwq.ims;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import android.os.ServiceManager;

public class PrivilegedProcess extends Instrumentation {
    static final String TAG = "vvb";
    static final String KEY_CONFIG_VERSION = "vvb2060_config_version";

    static final String KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ =
            "nr_advanced_threshold_bandwidth_khz_int";
    static final String KEY_ADDITIONAL_NR_ADVANCED_BANDS =
            "additional_nr_advanced_bands_int_array";
    static final String KEY_5G_ICON_CONFIGURATION = "5g_icon_configuration_string";
    private static final String KEY_NR_ADVANCED_CAPABLE_PCO_ID =
            "nr_advanced_capable_pco_id_int";
    private static final String KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH =
            "include_lte_for_nr_advanced_threshold_bandwidth_bool";
    // 0 = no bandwidth gate. A positive value is kHz of NR DL bandwidth, not bitrate.
    // China n78 is commonly 100 MHz, so 110_000 previously blocked NR_ADVANCED.
    private static final int NR_ADVANCED_THRESHOLD_KHZ_FOR_5GA = 0;
    private static final int[] NR_ADVANCED_BANDS_FOR_CHINA = new int[]{1, 3, 8, 28, 41, 78, 79};
    private static final String NR_ICON_CONFIGURATION_5GA =
            "connected_mmwave:5G_Plus,nr_advanced:5G_Plus,connected:5G,connected_rrc_idle:5G,"
                    + "not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        new Thread(() -> {
            try {
                runCatchingExemptions();
                UiAutomation uiAutomation = connectUiAutomation();
                if (uiAutomation == null) {
                    Log.e(TAG, "UiAutomation unavailable; cannot adopt shell identity");
                    return;
                }
                uiAutomation.adoptShellPermissionIdentity();
                try {
                    var context = getContext();
                    if (overrideConfig(context)) {
                        showVoLTE(context);
                        resetIms(context);
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "Skipping lockdown in debug so USB adb stays connected");
                        } else {
                            lockdownDebugging(context);
                        }
                    }
                } finally {
                    try {
                        uiAutomation.dropShellPermissionIdentity();
                    } catch (Exception e) {
                        Log.w(TAG, "dropShellPermissionIdentity failed", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, Log.getStackTraceString(e));
            } finally {
                finish(0, new Bundle());
            }
        }, "ims-patch").start();
    }

    private UiAutomation connectUiAutomation() {
        for (int i = 0; i < 5; i++) {
            try {
                UiAutomation uiAutomation = getUiAutomation();
                if (uiAutomation != null) return uiAutomation;
            } catch (Exception e) {
                Log.w(TAG, "getUiAutomation attempt " + i + " failed", e);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static void runCatchingExemptions() {
        try {
            HiddenApiBypass.addHiddenApiExemptions("L");
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply HiddenApiBypass exemptions", e);
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean overrideConfig(Context context) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        var values = getConfig();
        int[] subIds;
        try {
            subIds = (int[]) sm.getClass().getMethod("getActiveSubscriptionIdList").invoke(sm);
        } catch (Exception e) {
            Log.e(TAG, "failed to get active subscription ids", e);
            return false;
        }
        if (subIds == null || subIds.length == 0) {
            Log.e(TAG, "no active subscriptions");
            return false;
        }
        boolean anySuccess = false;
        for (var subId : subIds) {
            values.putInt(KEY_CONFIG_VERSION, BuildConfig.VERSION_CODE);
            boolean persistent = true;
            try {
                invokeOverrideConfig(cm, subId, values, true);
            } catch (SecurityException e) {
                Log.w(TAG, "persistent overrideConfig failed for subId " + subId
                        + ": " + e.getMessage(), e);
                persistent = false;
                try {
                    invokeOverrideConfig(cm, subId, values, false);
                } catch (RuntimeException inner) {
                    Log.e(TAG, "non-persistent overrideConfig failed for subId " + subId
                            + ": " + inner.getMessage(), inner);
                    continue;
                }
            }
            var bundle = cm.getConfigForSubId(subId, KEY_CONFIG_VERSION);
            if (bundle.getInt(KEY_CONFIG_VERSION, 0) == BuildConfig.VERSION_CODE) {
                Log.i(TAG, "overrideConfig succeeded for subId " + subId + ", persistent=" + persistent);
                anySuccess = true;
            } else {
                Log.e(TAG, "overrideConfig failed for subId " + subId + ", persistent=" + persistent);
            }
        }
        return anySuccess;
    }

    private static void invokeOverrideConfig(CarrierConfigManager cm, int subId,
                                             PersistableBundle values, boolean persistent) {
        try {
            cm.getClass().getMethod("overrideConfig", int.class, PersistableBundle.class,
                    boolean.class).invoke(cm, subId, values, persistent);
        } catch (NoSuchMethodException e) {
            try {
                cm.getClass().getMethod("overrideConfig", int.class, PersistableBundle.class)
                        .invoke(cm, subId, values);
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException("overrideConfig invocation failed", inner);
            }
        } catch (ReflectiveOperationException e) {
            var cause = e.getCause();
            if (cause instanceof SecurityException securityException) {
                throw securityException;
            }
            throw new IllegalStateException("overrideConfig invocation failed", e);
        }
    }

    @SuppressLint("PrivateApi")
    private static void showVoLTE(Context context) {
        var subId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        var binder = ServiceManager.getService(Context.TELEPHONY_SERVICE);
        var phone = ITelephony.Stub.asInterface(binder);
        try {
            var key = ProvisioningManager.class.getDeclaredField("KEY_VOIMS_OPT_IN_STATUS")
                    .getInt(null);
            var enabled = ProvisioningManager.class
                    .getDeclaredField("PROVISIONING_VALUE_ENABLED").getInt(null);
            var value = phone.getImsProvisioningInt(subId, key);
            if (value == enabled) return;
            phone.setImsProvisioningInt(subId, key, enabled);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, Log.getStackTraceString(e));
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
    }

    @SuppressLint("MissingPermission")
    private static void resetIms(Context context) {
        var telephony = context.getSystemService(TelephonyManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        if (telephony == null || sm == null) return;
        try {
            var list = sm.getActiveSubscriptionInfoList();
            if (list == null) return;
            var method = telephony.getClass().getMethod("resetIms", int.class);
            for (var info : list) {
                try {
                    method.invoke(telephony, info.getSimSlotIndex());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to reset IMS for slot " + info.getSimSlotIndex(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "resetIms failed", e);
        }
    }

    /**
     * Only called after a successful override. Turns wireless/USB debugging off and tries to hide
     * developer options. Failures are logged and ignored.
     */
    private static void lockdownDebugging(Context context) {
        var resolver = context.getContentResolver();
        putGlobal(resolver, "adb_wifi_enabled", 0);
        putGlobal(resolver, "adb_enabled", 0);
        putGlobal(resolver, "development_settings_enabled", 0);
    }

    private static void putGlobal(android.content.ContentResolver resolver, String key, int value) {
        try {
            if (!Settings.Global.putInt(resolver, key, value)) {
                Log.w(TAG, "Settings.Global.putInt returned false for " + key);
            } else {
                Log.i(TAG, "Set " + key + "=" + value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to write " + key, e);
        }
    }

    static PersistableBundle getConfig() {
        var bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true);

        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true);

        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true);

        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true);
        bundle.putBoolean("show_wifi_calling_icon_in_status_bar_bool", true);
        bundle.putInt("wfc_spn_format_idx_int", 6);

        bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false);
        bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false);

        bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true);
        bundle.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
                new int[]{CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
                        CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA});
        bundle.putInt(KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ, NR_ADVANCED_THRESHOLD_KHZ_FOR_5GA);
        bundle.putBoolean(KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH, true);
        bundle.putIntArray(KEY_ADDITIONAL_NR_ADVANCED_BANDS, NR_ADVANCED_BANDS_FOR_CHINA);
        bundle.putString(KEY_5G_ICON_CONFIGURATION, NR_ICON_CONFIGURATION_5GA);
        bundle.putInt(KEY_NR_ADVANCED_CAPABLE_PCO_ID, 0);
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                new int[]{
                        -128,
                        -118,
                        -108,
                        -98,
                });
        return bundle;
    }
}
