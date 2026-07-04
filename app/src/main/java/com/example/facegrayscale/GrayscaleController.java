package com.example.facegrayscale;

import android.content.Context;
import android.provider.Settings;

/**
 * Toggles the system's built-in "Color correction" (Daltonizer) feature to
 * flip the WHOLE screen (all apps) to grayscale and back.
 *
 * IMPORTANT ONE-TIME SETUP:
 * Writing these secure settings requires the WRITE_SECURE_SETTINGS permission,
 * which cannot be granted through a normal runtime dialog. You must grant it
 * once via ADB (connect phone to a PC with USB debugging on) after installing:
 *
 *   adb shell pm grant com.example.facegrayscale android.permission.WRITE_SECURE_SETTINGS
 *
 * This is the same technique used by published grayscale-toggle apps on the
 * Play Store, since there's no public runtime-permission API for this.
 */
public class GrayscaleController {

    public static void enableGrayscale(Context context) {
        Settings.Secure.putInt(context.getContentResolver(),
                "accessibility_display_daltonizer_enabled", 1);
        Settings.Secure.putInt(context.getContentResolver(),
                "accessibility_display_daltonizer", 0); // 0 = full monochromacy
    }

    public static void disableGrayscale(Context context) {
        Settings.Secure.putInt(context.getContentResolver(),
                "accessibility_display_daltonizer_enabled", 0);
    }
}
