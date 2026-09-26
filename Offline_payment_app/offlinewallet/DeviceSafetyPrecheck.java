package com.example.offlinewallet;

import android.content.Context;
import android.provider.Settings;

// Instant, no-network, no-setup check — run this FIRST, before the
// hardware-guard check and before requesting a Play Integrity token.
// Cheap early red flag; not a substitute for the deeper checks.
public class DeviceSafetyPrecheck {

    public static boolean isDeveloperOptionsEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    public static boolean isUsbDebuggingEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) != 0;
    }

    public static boolean failsPrecheck(Context context) {
        return isDeveloperOptionsEnabled(context) || isUsbDebuggingEnabled(context);
    }
}
