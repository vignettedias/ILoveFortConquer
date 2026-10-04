package io.github.vignettedias.fortconquer.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

/**
 * Keeps the game's OpenGL surface out of display cutouts.
 *
 * <p>Fort Conquer 1.2.4 targeted API 30 and used a full-screen legacy theme, so on phones with a
 * notch or punch-hole the platform letterboxed the landscape window away from the cutout
 * ({@code LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT}). For apps targeting API 35+, Android forces
 * {@code LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS}, which would place the camera hole over the game's
 * HUD (fort HP bars, pause button, unit cards). Padding the content view by the cutout's safe
 * insets reproduces the original effective viewport. System-bar insets are deliberately ignored:
 * the original layout already drew behind the (immersive, normally hidden) system bars.
 *
 * <p>Touch input stays correct because AndEngine maps MotionEvent coordinates relative to the
 * {@code RenderSurfaceView}, whose size simply shrinks by the padding.
 */
@TargetApi(28)
final class CutoutCompat {
    private CutoutCompat() {}

    static void install(Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                DisplayCutout cutout = insets.getDisplayCutout();
                int left = 0, top = 0, right = 0, bottom = 0;
                if (cutout != null) {
                    left = cutout.getSafeInsetLeft();
                    top = cutout.getSafeInsetTop();
                    right = cutout.getSafeInsetRight();
                    bottom = cutout.getSafeInsetBottom();
                }
                if (v.getPaddingLeft() != left || v.getPaddingTop() != top
                        || v.getPaddingRight() != right || v.getPaddingBottom() != bottom) {
                    v.setPadding(left, top, right, bottom);
                }
                return insets;
            }
        });
        content.requestApplyInsets();
    }
}
