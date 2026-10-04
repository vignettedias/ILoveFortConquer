package io.github.vignettedias.fortconquer.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.SystemClock;
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
 * (API 36) the platform no longer dispatches KEYCODE_BACK nor calls onBackPressed(), so without
 * this bridge back would leave the game without any of that handling.
 *
 * <p>The manifest sets {@code android:enableOnBackInvokedCallback="true"}, so on API 33+ back is
 * delivered only through this callback (never twice). On older releases the original key path is
 * used unchanged.
 */
@TargetApi(33)
final class BackCompat {
    private BackCompat() {}

    static void install(final Activity activity) {
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        long now = SystemClock.uptimeMillis();
                        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
                        activity.onKeyDown(KeyEvent.KEYCODE_BACK, down);
                    }
                });
    }
}
