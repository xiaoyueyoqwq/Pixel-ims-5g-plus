package io.github.vvb2060.ims;

import static rikka.shizuku.ShizukuProvider.METHOD_GET_BINDER;

import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import rikka.shizuku.ShizukuBinderWrapper;

public class PrivilegedProcess extends Instrumentation {
    static final String TAG = "vvb";
    private static final String KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ =
            "nr_advanced_threshold_bandwidth_khz_int";
    private static final String KEY_ADDITIONAL_NR_ADVANCED_BANDS =
            "additional_nr_advanced_bands_int_array";
    private static final String KEY_5G_ICON_CONFIGURATION = "5g_icon_configuration_string";
    private static final String KEY_NR_ADVANCED_CAPABLE_PCO_ID =
            "nr_advanced_capable_pco_id_int";
    private static final String KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH =
            "include_lte_for_nr_advanced_threshold_bandwidth_bool";
    private static final int NR_ADVANCED_THRESHOLD_KHZ_FOR_5GA = 110_000;
    private static final int[] NR_ADVANCED_BANDS_FOR_CHINA = new int[]{1, 3, 8, 28, 41, 78, 79};
    private static final String NR_ICON_CONFIGURATION_5GA =
            "connected_mmwave:5G_Plus,connected:5G,connected_rrc_idle:5G,"
                    + "not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G";

    @Override
    public void onCreate(Bundle arguments) {
        var context = getContext();
        if (Process.isSdkSandbox()) {
            var extras = makeExtras(context);
            var cr = getContext().getContentResolver();
            cr.call(BuildConfig.APPLICATION_ID + ".shizuku", METHOD_GET_BINDER, null, extras);
        } else if (arguments.getInt("pid", 0) == Process.myPid()) {
            var binder = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
            try {
                am.startDelegateShellPermissionIdentity(Os.getuid(), null);
                overrideConfig(context, false);
                am.stopDelegateShellPermissionIdentity();
            } catch (RemoteException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
            finish(0, new Bundle());
        } else {
            finish(0, new Bundle());
        }
    }

    private Bundle makeExtras(Context context) {
        var binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == 1) {
                    try {
                        overrideConfig(context, true);
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    var handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> finish(0, new Bundle()), 1000);
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        var extras = new Bundle();
        extras.putBinder("binder", binder);
        return extras;
    }

    @SuppressLint("MissingPermission")
    private static void overrideConfig(Context context, boolean persistent) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        var values = getConfig();
        int[] subIds;
        try {
            subIds = (int[]) sm.getClass().getMethod("getActiveSubscriptionIdList").invoke(sm);
        } catch (Exception e) {
            throw new IllegalStateException("failed to get active subscription ids", e);
        }
        for (var subId : subIds) {
            values.putInt("vvb2060_config_version", BuildConfig.VERSION_CODE);
            try {
                invokeOverrideConfig(cm, subId, values, persistent);
            } catch (SecurityException e) {
                Log.w(TAG, "overrideConfig failed for subId " + subId, e);
                if (persistent) {
                    persistent = false;
                    invokeOverrideConfig(cm, subId, values, persistent);
                }
            }
            var bundle = cm.getConfigForSubId(subId, "vvb2060_config_version");
            if (bundle.getInt("vvb2060_config_version", 0) == BuildConfig.VERSION_CODE) {
                Log.i(TAG, "overrideConfig succeeded for subId " + subId + ", persistent=" + persistent);
            } else {
                Log.e(TAG, "overrideConfig failed for subId " + subId + ", persistent=" + persistent);
            }
        }
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

    private static PersistableBundle getConfig() {
        var bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL, true);
        //bundle.putString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, "jp");

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
        bundle.putBoolean(KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH, false);
        bundle.putIntArray(KEY_ADDITIONAL_NR_ADVANCED_BANDS, NR_ADVANCED_BANDS_FOR_CHINA);
        bundle.putString(KEY_5G_ICON_CONFIGURATION, NR_ICON_CONFIGURATION_5GA);
        bundle.putInt(KEY_NR_ADVANCED_CAPABLE_PCO_ID, 0);
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                // Boundaries: [-140 dBm, -44 dBm]
                new int[]{
                        -128, /* SIGNAL_STRENGTH_POOR */
                        -118, /* SIGNAL_STRENGTH_MODERATE */
                        -108, /* SIGNAL_STRENGTH_GOOD */
                        -98,  /* SIGNAL_STRENGTH_GREAT */
                });
        return bundle;
    }
}
