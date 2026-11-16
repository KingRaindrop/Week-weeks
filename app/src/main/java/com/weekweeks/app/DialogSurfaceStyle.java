package com.weekweeks.app;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Window;

/** Restyles Material dialogs without changing their outside-touch geometry. */
final class DialogSurfaceStyle {
    private DialogSurfaceStyle() { }

    static void apply(Dialog dialog, Drawable surface) {
        Window window = dialog.getWindow();
        if (window == null) return;
        Drawable background = window.getDecorView().getBackground();
        if (background instanceof InsetDrawable) {
            // MaterialAlertDialogBuilder installs matching background insets and an
            // InsetDialogOnTouchListener. Keep that wrapper (including themed/RTL
            // insets), replacing only the painted surface. No listener is changed.
            InsetDrawable inset = (InsetDrawable) background;
            // DrawableWrapper.setDrawable initially assigns the outer bounds to
            // its new child. Preserve the already-inset bounds until next layout.
            android.graphics.Rect bounds = new android.graphics.Rect(inset.getDrawable().getBounds());
            inset.setDrawable(surface);
            surface.setBounds(bounds);
            window.getDecorView().invalidate();
        }
        // Unknown/non-Material windows keep their existing geometry and appearance.
    }
}
