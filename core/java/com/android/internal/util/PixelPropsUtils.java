/*
 * Copyright (C) 2024 Shinkai Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;
// Standard AOSP package for internal framework utilities.
// This makes the class accessible from core framework code
// (Application.java, ApplicationPackageManager.java) but NOT from apps.

import android.app.Application;
// We need Application.getProcessName() to know which process we're in.
// An app can have multiple processes (e.g., GMS has .unstable, .persistent, etc.)
// but we only care about the main process name for logging/debugging.

import android.content.Context;
// Context gives us getPackageName() so we know WHICH app is loading.

import android.os.Build;
// The heart of the spoofing. Build.BRAND, Build.DEVICE, Build.MODEL, etc.
// are public static final fields. We use reflection to overwrite them.

import android.os.SystemProperties;
// Reads persist.sys.* properties from the system. We use this to let users
// toggle game spoofing without recompiling the ROM.

import android.text.TextUtils;
// Utility to check if strings are null or empty. Safer than manual checks.

import android.util.Log;
// Standard Android logging. We use Log.d() for debug and Log.e() for errors.

import java.lang.reflect.Field;
// Reflection API. Build fields are 'public static final' — normally immutable.
// Reflection lets us bypass that and overwrite them at runtime.

import java.util.Arrays;
// Utility for working with arrays (we use Arrays.asList() to check
// if a package is in the YouTube list).

import java.util.List;
// Java list interface. We use List.of() for immutable package lists.

import java.util.Map;
// Key-value pairs for device properties. Each device (Pixel, S23, ROG6)
// is stored as a Map<String, Object> where key = Build field name,
// value = the spoofed value.

/**
 * Clean per-app device spoofing for Shinkai Project.
 *
 * - Spoofs Build fields via reflection in each app's process
 * - Filters PackageManager.hasSystemFeature() for Google Photos
 * - NO Play Integrity / PIF logic
 * - NO key attestation blocking
 * - NO keystore provider spoofing
 */
public final class PixelPropsUtils {
    // 'final' = cannot be subclassed. This is a utility class with only static methods.
    // No need to instantiate it.

    /* ================================================================ */
    /*  DEBUGGING                                                         */
    /* ================================================================ */

    private static final String TAG = "PixelPropsUtils";
    // Log tag. All logcat output from this class appears as "PixelPropsUtils".
    // Filter with: adb logcat -s PixelPropsUtils:D

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    // Checks at runtime if the log tag is set to DEBUG level.
    // You enable it with: adb shell setprop log.tag.PixelPropsUtils DEBUG
    // When disabled (default), all dlog() calls are skipped — zero overhead.

    /* ================================================================ */
    /*  TOGGLES                                                           */
    /* ================================================================ */

    private static final boolean sGamesEnabled =
            SystemProperties.getBoolean("persist.sys.pixelprops.games", true);
    // Reads the system property 'persist.sys.pixelprops.games'.
    // 'persist.' means it survives reboots.
    // Default value is 'true' (games spoofing ON).
    // Users can disable with: adb shell setprop persist.sys.pixelprops.games false

    /* ================================================================ */
    /*  PACKAGE CONSTANTS                                                 */
    /* ================================================================ */

    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    // Google Photos package name. We give this SPECIAL treatment:
    // - Spoof to original Pixel (sailfish) for unlimited backup
    // - Filter hasSystemFeature() to fake NEXUS_PRELOAD

    private static final String PACKAGE_PREFIX_GOOGLE = "com.google.android.";
    // Prefix shared by almost all Google apps. We use startsWith() to catch
    // Google Calendar, Gmail, Drive, etc. without listing them all.

    private static final String PACKAGE_VENDING = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_WALLET = "com.google.android.apps.walletnfcrel";

    /* ================================================================ */
    /*  DEVICE PROP MAPS                                                  */
    /*  Each Map<String, Object> defines a fake device profile.         */
    /*  Key   = Build field name (BRAND, DEVICE, MODEL, etc.)            */
    /*  Value = The fake value to inject                                   */
    /* ================================================================ */

    // ------------------------------------------------------------------
    // PROPS_PIXEL_XL  --  Google Pixel XL(marlin)
    // ------------------------------------------------------------------
    // This is the EXACT device profile that Google Photos checks for
    // "original quality unlimited backup".
    // Photos looks at: BRAND=google, MANUFACTURER=Google, DEVICE=sailfish,
    // MODEL=Pixel, and the fingerprint must be from the sailfish device.
    // We use Android 9 (PPR1...) fingerprint because it's the last version
    // sailfish received, and Photos trusts it.
    private static final Map<String, Object> PROPS_PIXEL_XL = Map.of(
            "BRAND",         "google",
            "MANUFACTURER",  "Google",
            "DEVICE",        "marlin",
            "PRODUCT",       "marlin",
            "MODEL",         "Pixel XL",
            "FINGERPRINT",   "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
            //"HARDWARE", "marlin",
            //"BOARD", "marlin",
            //"VERSION.RELEASE", "10",
            //"VERSION.SDK_INT", "29",
            //"ID", "QP1A.191005.007.A3"
            //"DISPLAY", "QP1A.191005.007.A3",
            //"INCREMENTAL", "5972272",
            //"TYPE", "user",
            //"TAGS", "release-keys",
            //"VERSION.SECURITY_PATCH", "2019-10-06",
            //"DEVICE_INITIAL_SDK_INT","25"

    );

    // ------------------------------------------------------------------
    // PROPS_PIXEL_11_PRO_XL  --  Pixel 11 Pro XL (kodiak)
    // ------------------------------------------------------------------
    // Latest Pixel device profile. Apps like Recorder, Call Screen,
    // Magic Eraser, etc. check for recent Pixel fingerprints to enable
    // features. We use this for all Google apps EXCEPT Photos.
    private static final Map<String, Object> PROPS_PIXEL_11_PRO_XL = Map.ofEntries(
            Map.entry("BRAND",         "google"),
            Map.entry("MANUFACTURER",  "Google"),
            Map.entry("DEVICE",        "kodiak"),
            Map.entry("PRODUCT",       "kodiak"),
            Map.entry("MODEL",         "Pixel 11 Pro XL"),
            Map.entry("FINGERPRINT",   "google/kodiak/kodiak:17/CD1A.260714.001.A9/15938155:user/release-keys"),
            Map.entry("VERSION.RELEASE", "17"),
            Map.entry("VERSION.INCREMENTAL", "15938155"),
            Map.entry("VERSION.SECURITY_PATCH", "2026-08-05")
    );

    // ------------------------------------------------------------------
    // GENERIC_PROPS_TEMPLATE
    // ------------------------------------------------------------------
    // 
    // 
    // private static final Map<String, Object> PROPS = Map.of(
    //         "BRAND",         "samsung",
    //         "MANUFACTURER",  "samsung",
    //         "DEVICE",        "dm1q",
    //         "MODEL",         "SM-S911B",
    //         "FINGERPRINT",   "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXS3AWF7:user/release-keys"
    // );

    /* ================================================================ */
    /*  GAME PROP MAPS                                                    */
    /*  These are minimal prop sets — games usually only check            */
    /*  MANUFACTURER and MODEL to decide FPS / graphics settings.         */
    /* ================================================================ */

    private static final Map<String, Object> propsBS4 = Map.of(
        "BRAND", "blackshark",
        "MANUFACTURER", "blackshark",
        "MODEL", "SHARK PRS-A0"
    );

    private static final Map<String, Object> propsPF4 = Map.of(
        "BRAND", "Xiaomi",
        "MANUFACTURER", "Xiaomi",
        "MODEL", "22021211RG"
    );

    private static final Map<String, Object> propsiQ11 = Map.of(
        "BRAND", "vivo",
        "MANUFACTURER", "vivo",
        "MODEL", "V2243A"
    );

    private static final Map<String, Object> propsMI11T = Map.of(
        "BRAND", "Xiaomi",
        "MANUFACTURER", "Xiaomi",
        "MODEL", "21081111RG"
    );

    private static final Map<String, Object> propsMI13P = Map.of(
        "BRAND", "Xiaomi",
        "MANUFACTURER", "Xiaomi",
        "MODEL", "2210132C"
    );

    private static final Map<String, Object> propsNX729J = Map.of(
        "BRAND", "nubia",
        "DEVICE", "NX729J",
        "MANUFACTURER", "nubia",
        "MODEL", "NX729J"
    );

    private static final Map<String, Object> propsOP8P = Map.of(
        "BRAND", "OnePlus",
        "DEVICE", "OnePlus8Pro",
        "MANUFACTURER", "OnePlus",
        "MODEL", "IN2020"
    );

    private static final Map<String, Object> propsOP9P = Map.of(
        "BRAND", "OnePlus",
        "DEVICE", "OnePlus9Pro",
        "MANUFACTURER", "OnePlus",
        "MODEL", "LE2101"
    );

    private static final Map<String, Object> propsROG3 = Map.of(
        "MANUFACTURER", "asus",
        "MODEL", "ASUS_I003D"
    );

    private static final Map<String, Object> propsROG6 = Map.of(
        "BRAND", "asus",
        "MANUFACTURER", "asus",
        "MODEL", "ASUS_AI2201"
    );

    private static final Map<String, Object> propsROG8 = Map.of(
        "BRAND", "asus",
        "MANUFACTURER", "asus",
        "MODEL", "ASUS_AI2401_A"
    );

    private static final Map<String, Object> propsXP5 = Map.of(
        "BRAND", "Sony",
        "MANUFACTURER", "Sony",
        "MODEL", "SO-52A"
    );

    private static final Map<String, Object> propsLY700 = Map.of(
        "BRAND", "Lenovo",
        "MANUFACTURER", "Lenovo",
        "MODEL", "TB-9707F"
    );

    /* ================================================================ */
    /*  GAME PACKAGE LISTS                                                */
    /*  Which packages get which game device profile.                   */
    /* ================================================================ */

    // These packages get ROG Phone 6 props
    private static final List<String> GAMES_BS4 = List.of(
            "com.proximabeta.mf.uamo"
    );

    // These packages get Sony Xperia 5 props
    private static final List<String> GAMES_PF4 = List.of(
            "com.mobile.legends"
    );

    // These packages get OnePlus 8 Pro props
    private static final List<String> GAMES_iQ11 = List.of(
            "com.tencent.KiHan",
            "com.tencent.tmgp.cf",
            "com.tencent.tmgp.cod",
            "com.tencent.tmgp.gnyx"
    );

    // These packages get OnePlus 9 Pro props
    private static final List<String> GAMES_MI11T = List.of(
            "com.levelinfinite.hotta.gp",
            "com.vng.mlbbvn"
    );

    // These packages get Xiaomi 11T props
    private static final List<String> GAMES_MI13P = List.of(
            "com.levelinfinite.sgameGlobal",
            "com.tencent.tmgp.sgame"
    );

    // These packages get Xiaomi 13 Pro props
    private static final List<String> GAMES_NX729J = List.of(
            "com.YoStar.AetherGazer"
    );

    // These packages get POCO F4 props
    private static final List<String> GAMES_OP8P = List.of(
            "com.netease.lztgglobal",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn"
    );

    private static final List<String> GAMES_OP9P = List.of(
            "com.epicgames.fortnite",
            "com.epicgames.portal",
            "com.tencent.lolm",
            "jp.konami.pesam"
    );

    private static final List<String> GAMES_ROG3 = List.of(
            "com.ea.gp.fifamobile",
            "com.pearlabyss.blackdesertm",
            "com.pearlabyss.blackdesertm.gl"
    );
    
    private static final List<String> GAMES_ROG6 = List.of(
            "com.gameloft.android.ANMP.GloftA9HM",
            "com.madfingergames.legends",
            "com.riotgames.league.teamfighttactics",
            "com.riotgames.league.teamfighttacticstw",
            "com.riotgames.league.teamfighttacticsvn"
    );

    private static final List<String> GAMES_ROG8 = List.of(
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.rekoo.pubgm",
            "com.tencent.ig",
            "com.tencent.tmgp.pubgmhd",
            "com.vng.pubgmobile"
    );

    private static final List<String> GAMES_LY700 = List.of(
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.tencent.tmgp.kr.codm",
            "com.vng.codmvn"
    );
    

    /* ================================================================ */
    /*  RUNTIME STATE                                                     */
    /*  These are set per-process when setProps() is called.            */
    /* ================================================================ */

    private static volatile boolean sIsPhotos = false;
    // 'volatile' ensures thread-safe visibility across threads.
    // We set this to true when the current process belongs to Google Photos.
    // hasSystemFeature() checks this flag to know whether to filter features.
    // It's per-process because setProps() runs once per app process.

    /* ================================================================ */
    /*  MAIN ENTRY POINT                                                  */
    /*  Call this from Application.attachBaseContext()                  */
    /* ================================================================ */

    public static void setProps(Context context) {
        // 'context' is the Application instance. From it we get the package name.
        // This method runs VERY EARLY in app startup — before any app code runs.
        // That's why the spoofing is invisible to the app.

        final String pkg = context.getPackageName();
        // The package name of the app that's starting (e.g., "com.google.android.apps.photos")

        final String proc = Application.getProcessName();
        // The process name. Usually same as package, but can differ for
        // multi-process apps (e.g., "com.google.android.gms.unstable").
        // We mostly use this for debug logging.

        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(proc)) {
            // Safety check. If either is null/empty, something is very wrong.
            // Bail out rather than crash.
            return;
        }

        sIsPhotos = pkg.equals(PACKAGE_GPHOTOS);
        // Remember if THIS process is Google Photos. hasSystemFeature() will check this.

        // ------------------------------------------------------------------
        // DECISION TREE: Which device profile to apply?
        // ------------------------------------------------------------------

        if (pkg.equals(PACKAGE_VENDING)
        || pkg.equals(PACKAGE_GMS)
        || pkg.equals(PACKAGE_WALLET)) {
            dlog("Skipping spoof for excluded package: " + pkg);
            return;
        }

        if (sIsPhotos) {
            // Google Photos gets the ORIGINAL Pixel (sailfish) profile.
            // This is the KEY to unlimited backup. Photos checks:
            //   1. Is it a Pixel? (BRAND=google, MANUFACTURER=Google)
            //   2. Is it an ORIGINAL Pixel? (DEVICE=sailfish, MODEL=Pixel)
            //   3. Does it have NEXUS_PRELOAD feature? (we fake this in hasSystemFeature)
            // If all three are true, Photos offers "Original quality" for free.
            dlog("Spoofing Pixel (sailfish) for Google Photos");
            PROPS_PIXEL_XL.forEach(PixelPropsUtils::setField);
            // Map.forEach() iterates every entry and calls setField(key, value).
            // This overwrites all 6 Build fields in one shot.

        // } else if (Arrays.asList(PACKAGE_YOUTUBE).contains(pkg)) {
            // YouTube and YouTube Music get Samsung S23.
            // YouTube's server-side logic sends 4K streams only to
            // whitelisted devices. Samsung flagships are always whitelisted.
        //    dlog("Spoofing Samsung S23 for: " + pkg);
        //    PROPS_S23.forEach(PixelPropsUtils::setField);

        } else if (pkg.startsWith(PACKAGE_PREFIX_GOOGLE)) {
            // ALL other Google apps (Gmail, Drive, Calendar, etc.) get
            // the latest Pixel 9 Pro XL profile. This enables:
            // - Call Screen
            // - Hold for Me
            // - Direct My Call
            // - Magic Eraser (in Photos editor, not the backup thing)
            // - Recorder transcriptions
            // - etc.
            dlog("Spoofing Pixel 9 Pro XL for: " + pkg);
            PROPS_PIXEL_11_PRO_XL.forEach(PixelPropsUtils::setField);
        }

        // ------------------------------------------------------------------
        // GAME SPOOFING (runs AFTER app spoofing, so it can OVERRIDE)
        // ------------------------------------------------------------------
        // Example: If a game is also a Google app (rare), the game props
        // take precedence because they run second and overwrite the fields.
        if (sGamesEnabled) {
            setGameProps(pkg, proc);
        }
    }

    /* ================================================================ */
    /*  GAME SPOOFING LOGIC                                               */
    /* ================================================================ */

    private static void setGameProps(String pkg, String proc) {
        // Check which game list contains this package, then apply the
        // corresponding device profile. Only one profile per app.

        Map<String, Object> props = null;
        // 'null' means "no match found, don't spoof anything"

        if (GAMES_BS4.contains(pkg)) {
            props = propsBS4;
        } else if (GAMES_PF4.contains(pkg)) {
            props = propsPF4;
        } else if (GAMES_iQ11.contains(pkg)) {
            props = propsiQ11;
        } else if (GAMES_MI11T.contains(pkg)) {
            props = propsMI11T;
        } else if (GAMES_MI13P.contains(pkg)) {
            props = propsMI13P;
        } else if (GAMES_NX729J.contains(pkg)) {
            props = propsNX729J;
        } else if (GAMES_OP8P.contains(pkg)) {
            props = propsOP8P;
        } else if (GAMES_OP9P.contains(pkg)) {
            props = propsOP9P;
        } else if (GAMES_ROG3.contains(pkg)) {
            props = propsROG3;
        } else if (GAMES_ROG6.contains(pkg)) {
            props = propsROG6;
        } else if (GAMES_ROG8.contains(pkg)) {
            props = propsROG8;
        } else if (GAMES_LY700.contains(pkg)) {
            props = propsLY700;
        }

        if (props != null) {
            // If we found a match, apply the props
            dlog("Spoofing game props for: " + pkg);
            props.forEach(PixelPropsUtils::setField);
        }
        // If no match, we do nothing — the app sees the real device.
    }

    /* ================================================================ */
    /*  hasSystemFeature HOOK                                             */
    /*  Call this from ApplicationPackageManager.hasSystemFeature()       */
    /* ================================================================ */

    public static boolean hasSystemFeature(String name, boolean original) {
        if (!sIsPhotos) {
            return original;
        }

        // Log EVERY feature Photos queries, regardless of whether we spoof it.
        // This lets you see in logcat exactly what Photos is asking for.
        dlog("Photos feature query: [" + name + "] original=" + original);

        // Null safety guard — should never happen, but prevents NPE if it does.
        if (name == null) {
            return original;
        }

        // Case-insensitive check for NEXUS_PRELOAD.
        // Photos queries either:
        //   com.google.android.apps.photos.NEXUS_PRELOAD   (uppercase)
        //   com.google.android.apps.photos.nexus_preload     (lowercase)
        // We catch both with toUpperCase().contains().
        if (name != null && name.toUpperCase().contains("NEXUS_PRELOAD")) {
            dlog("Feature [" + name + "]: forced TRUE for Photos");
            return true;
        }

        // Block ALL newer Pixel experience features (2018 through 2026).
        // If Photos sees these, it knows the device is a modern Pixel and
        // applies quota-based storage rules instead of unlimited original.
        if (name.contains("PIXEL_2018") || name.contains("PIXEL_2019")
                || name.contains("PIXEL_2020") || name.contains("PIXEL_2021")
                || name.contains("PIXEL_2022") || name.contains("PIXEL_2023")
                || name.contains("PIXEL_2024") || name.contains("PIXEL_2025")
                || name.contains("PIXEL_2026")) {
            dlog("Feature [" + name + "]: forced FALSE for Photos");
            return false;
        }

        // Allow 2017-era Pixel features. This is the fallback tier.
        if (name.contains("PIXEL_2017")) {
            dlog("Feature [" + name + "]: forced TRUE for Photos");
            return true;
        }

        // For any other feature, return the real value. Don't interfere.
        return original;
    }

    /* ================================================================ */
    /*  REFLECTION HELPER                                                 */
    /*  The core mechanism: overwriting Build fields at runtime.          */
    /* ================================================================ */

    private static void setField(String key, Object value) {
        // 'key'   = Build field name, e.g. "MODEL", "BRAND", "FINGERPRINT"
        // 'value' = The fake value, e.g. "Pixel", "google", etc.

        try {
            Class<?> clazz = Build.class;
            // Default to android.os.Build

            if (key.startsWith("VERSION.")) {
                // Handle VERSION fields like "VERSION.SECURITY_PATCH"
                // by stripping the prefix and using Build.VERSION instead.
                clazz = Build.VERSION.class;
                key = key.substring(8); // Remove "VERSION." prefix
            }

            Field field = clazz.getDeclaredField(key);
            // Get the Field object via reflection. This works even for
            // 'public static final' fields because reflection bypasses
            // normal access controls.

            field.setAccessible(true);
            // Disable Java access checks. Without this, 'final' fields
            // cannot be modified.

            field.set(null,
                    field.getType().equals(Integer.TYPE)
                            ? Integer.parseInt(value.toString())
                            : value);
            // field.set(null, ...) overwrites a STATIC field (instance would be 'obj').
            // If the field type is 'int', we parse the string to Integer.
            // Otherwise we pass the value as-is (String).

            field.setAccessible(false);
            // Restore access checks (good hygiene, though not strictly necessary
            // since we're in the same process and nobody else reflects on this).

            dlog("Set " + key + " = " + value);

        } catch (Exception e) {
            // Catch EVERYTHING (NoSuchFieldException, IllegalAccessException, etc.)
            // We NEVER want to crash an app because spoofing failed.
            Log.e(TAG, "Failed to set " + key, e);
        }
    }

    /* ================================================================ */
    /*  DEBUG HELPER                                                      */
    /* ================================================================ */

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
        // Only logs if log.tag.PixelPropsUtils is set to DEBUG.
        // When disabled, this method does nothing — zero overhead.
    }
}
