package io.github.vignettedias.fortconquer.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * Routes system back navigation into the game's original KEYCODE_BACK handler.
 *
 * <p>The game (GameActivity.onKeyDown -> SmartScene.onKeyDown) implements back entirely itself:
 * in battle it opens the pause dialog, on menu scenes it navigates to the previous scene, and on
 * the title screen it consumes the key without doing anything. The original never lets the
 * platform finish the activity (players leave with Home/Recents). For apps targeting Android 16
 * (API 36) back is routed to OnBackInvokedCallbacks and onBackPressed() is no longer called; with
 * no app callback the system's default back sends the game to the background (observed), and
 * gesture back never reaches onKeyDown at all.
 *
 * <p>The manifest sets {@code android:enableOnBackInvokedCallback="true"}. Observed on Android 16:
 * key-based back (navigation-bar button, hardware key) still delivers KEYCODE_BACK ACTION_DOWN to
 * GameActivity.onKeyDown and then also invokes this callback, whereas gesture/predictive back
 * invokes only this callback. To run the original handler exactly once, GameActivity.onKeyDown
 * reports real BACK presses (patches/0008) and the callback only synthesizes the key when none was
 * delivered. On releases before API 33 the original key path is used unchanged.
 */
@TargetApi(33)
final class BackCompat {
    /** Uptime of the last real KEYCODE_BACK ACTION_DOWN seen by the game, or -1. UI thread only. */
    private static long sKeyBackDownUptime = -1;
    private static boolean sSynthesizing;

    private BackCompat() {}

    /** Called (via GameCompat) at the start of GameActivity.onKeyDown for every key event. */
    static void onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && !sSynthesizing) {
            sKeyBackDownUptime = SystemClock.uptimeMillis();
        }
    }

    private static boolean consumeDeliveredKeyBack() {
        long t = sKeyBackDownUptime;
        sKeyBackDownUptime = -1;
        // a key press is released well within this window; a stale mark never suppresses later backs
        return t >= 0 && SystemClock.uptimeMillis() - t < 10000;
    }

    static void install(final Activity activity) {
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (consumeDeliveredKeyBack()) {
                            // Key-based back (3-button nav bar, hardware key): the platform already
                            // delivered KEYCODE_BACK ACTION_DOWN to GameActivity.onKeyDown, which
                            // handled it. Running the handler again would e.g. pause a battle and
                            // then immediately quit it.
                            Log.d(PreservationInfo.TAG, "back invoked after KEYCODE_BACK; already handled");
                            return;
                        }
                        // Gesture/predictive back: no key event reached the game.
                        Log.d(PreservationInfo.TAG, "back invoked -> GameActivity.onKeyDown(KEYCODE_BACK)");
                        long now = SystemClock.uptimeMillis();
                        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
                        sSynthesizing = true;
                        try {
                            activity.onKeyDown(KeyEvent.KEYCODE_BACK, down);
                        } finally {
                            sSynthesizing = false;
                        }
                    }
                });
    }
}
