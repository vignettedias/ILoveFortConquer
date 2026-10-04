package io.github.vignettedias.fortconquer.compat;

/**
 * Identifies the unofficial modern-Android preservation build of Fort Conquer.
 *
 * <p>Everything in this package was written for the preservation project; it is not DroidHen
 * code. It is compiled to Dalvik bytecode and merged into the original classes.dex, and is only
 * reached from small, documented call-site patches in {@code patches/}.
 */
public final class PreservationInfo {
    public static final String TAG = "FCPreservation";
    public static final String DESCRIPTION =
            "Fort Conquer 1.2.4 - unofficial modern Android preservation build (not a DroidHen release)";

    private PreservationInfo() {}
}
