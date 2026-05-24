package io.github.vvb2060.ims;

import static io.github.vvb2060.ims.PrivilegedProcess.TAG;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.app.UiAutomationConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.system.Os;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;

import org.lsposed.hiddenapibypass.LSPass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public class ShizukuProvider extends rikka.shizuku.ShizukuProvider {
    static {
        LSPass.setHiddenApiExemptions("");
    }

    private boolean skip = false;

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        var sdkUid = resolveSdkSandboxUid();
        var callingUid = Binder.getCallingUid();
        if (sdkUid != null && callingUid != sdkUid && callingUid != Process.SHELL_UID
                && callingUid != Process.ROOT_UID) {
            return new Bundle();
        }

        if (METHOD_SEND_BINDER.equals(method)) {
            Shizuku.addBinderReceivedListener(() -> {
                if (!skip && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    showVoLTE();
                    var context = getContext();
                    assert context != null;
                    if (needOverride(context)) startInstrument(context, canPersistent(context));
                }
            });
        } else if (METHOD_GET_BINDER.equals(method) && sdkUid != null
                && callingUid == sdkUid && extras != null) {
            skip = true;
            Shizuku.addBinderReceivedListener(() -> {
                var binder = extras.getBinder("binder");
                if (binder != null && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    startShellPermissionDelegate(binder, sdkUid);
                }
            });
        }
        return super.call(method, arg, extras);
    }

    private static void startShellPermissionDelegate(IBinder binder, int sdkUid) {
        try {
            var activity = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(activity));
            am.startDelegateShellPermissionIdentity(sdkUid, null);
            var data = Parcel.obtain();
            binder.transact(1, data, null, 0);
            data.recycle();
            am.stopDelegateShellPermissionIdentity();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static void startInstrument(Context context, boolean sdkSandbox) {
        try {
            var binder = ServiceManager.getService(Context.ACTIVITY_SERVICE);
            var am = IActivityManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
            var name = new ComponentName(context, PrivilegedProcess.class);
            var flags = 1;
            if (sdkSandbox) {
                flags |= 32;
            } else {
                flags |= 8;
            }
            var args = new Bundle();
            args.putInt("pid", Process.myPid());
            var connection = new UiAutomationConnection();
            am.startInstrumentation(name, null, flags, args, null, connection, 0, null);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private static boolean needOverride(Context context) {
        var cm = context.getSystemService(CarrierConfigManager.class);
        var sm = context.getSystemService(SubscriptionManager.class);
        try {
            var list = sm.getActiveSubscriptionInfoList();
            if (list == null || list.isEmpty()) {
                return true;
            }
            for (var subinfo : list) {
                var subId = subinfo.getSubscriptionId();
                var bundle = cm.getConfigForSubId(subId, "vvb2060_config_version");
                if (bundle.getInt("vvb2060_config_version", 0) != BuildConfig.VERSION_CODE) {
                    return true;
                }
            }
            Log.i(TAG, "no need to override carrier config");
            return false;
        } catch (SecurityException e) {
            return true;
        }
    }

    @SuppressLint("PrivateApi")
    private static boolean canPersistent(Context context) {
        try {
            var phone = context.createPackageContext("com.android.phone",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            var clazz = phone.getClassLoader().loadClass("com.android.phone.CarrierConfigLoader");
            try {
                clazz.getDeclaredMethod("isSystemApp");
            } catch (NoSuchMethodException e) {
                return true;
            }
            clazz.getDeclaredMethod("secureOverrideConfig", PersistableBundle.class, boolean.class);
            try {
                clazz.getDeclaredMethod("isSdkSandboxUidInternal", int.class);
                return false;
            } catch (NoSuchMethodException e) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void showVoLTE() {
        var subId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        var binder = ServiceManager.getService(Context.TELEPHONY_SERVICE);
        var phone = ITelephony.Stub.asInterface(new ShizukuBinderWrapper(binder));
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

    private static Integer resolveSdkSandboxUid() {
        try {
            return (Integer) Process.class
                    .getMethod("toSdkSandboxUid", int.class)
                    .invoke(null, Os.getuid());
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
            return null;
        }
    }
}
