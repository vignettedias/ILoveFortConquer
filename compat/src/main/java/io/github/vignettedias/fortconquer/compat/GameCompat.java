package io.github.vignettedias.fortconquer.compat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Entry points called from the patched original bytecode (patches/0003, 0007 and 0008).
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
     * Called at the start of {@code GameActivity.onKeyDown()} (patches/0008) so the back bridge
     * can tell key-based back (already delivered as KEYCODE_BACK) from gesture back.
     */
    public static void onKeyDown(int keyCode, KeyEvent event) {
        if (Build.VERSION.SDK_INT >= 33) {
            BackCompat.onKeyDown(keyCode, event);
        }
    }

    /**
     * Shows a dialog built by the original code on the UI thread.
     *
     * <p>The coin store calls {@code PurchaseManager.buyItemInMainThread()} from AndEngine's
     * update thread (touch events are processed there). When billing is unavailable the original
     * code built the "Can't make purchases" AlertDialog on that Looper-less thread; dialog
     * creation threw and the exception was swallowed, so the BUY buttons silently did nothing.
     * Building the dialog content off-thread is harmless; only create()/show() must run on the
     * UI thread.
     */
    public static void showDialogOnUiThread(final Activity activity, final AlertDialog.Builder builder) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing()) return;
                try {
                    builder.create().show();
                } catch (RuntimeException e) {
                    Log.w(PreservationInfo.TAG, "Could not show dialog", e);
                }
            }
        });
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
