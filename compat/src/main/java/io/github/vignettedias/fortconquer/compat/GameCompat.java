package io.github.vignettedias.fortconquer.compat;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Entry points called from the patched original bytecode (see patches/0004 and patches/0006).
 *
 * <p>Each hook is a thin adapter between the 2012-era game code and a modern Android platform
 * requirement. Version-specific framework types live in separate classes ({@link CutoutCompat},
 * {@link BackCompat}) so that this class verifies cleanly on every runtime from API 16 upwards.
 */
public final class GameCompat {
    private GameCompat() {}

    /**
     * Called from {@code GameActivity.onCreate()} right after AndEngine has installed its
     * {@code RenderSurfaceView} as the content view.
     */
    public static void onActivityCreated(Activity activity) {
        Log.i(PreservationInfo.TAG, PreservationInfo.DESCRIPTION + "; Android SDK " + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 28) {
            CutoutCompat.install(activity);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            BackCompat.install(activity);
        }
    }

    /**
     * Replacement for {@code Activity.startActivity(Intent)} at call sites that open external
     * links (e.g. the billing "Learn more" button). The original code assumed a browser is always
     * installed; on devices without a handler the call threw ActivityNotFoundException on the UI
     * thread and killed the game. Returns whether an activity was started.
     */
    public static boolean startActivitySafely(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(PreservationInfo.TAG, "No activity can handle " + intent + "; ignoring", e);
        } catch (SecurityException e) {
            Log.w(PreservationInfo.TAG, "Not allowed to start " + intent + "; ignoring", e);
        }
        return false;
    }
}
