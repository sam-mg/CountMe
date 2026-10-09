package com.jd_s4nd_b0x.CountMe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Light/dark choice. Stored only in this device's private preferences: it is not part of the data
 * snapshot synced to Drive, and the file is excluded from Android backup.
 */
public final class ThemePrefs {

    private static final String PREFS = "UiPrefs";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemePrefs() {}

    /** Call once at app start. Without a saved choice the app follows the system setting. */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(
                prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
    }

    public static boolean isDark(Context context) {
        int mode =
                prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (mode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            return mode == AppCompatDelegate.MODE_NIGHT_YES;
        }
        return (context.getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static void setDark(Context context, boolean dark) {
        int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
