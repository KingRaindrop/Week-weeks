package com.weekweeks.app;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Routes system Back through cancel() before Material can hide a dirty editor.
 * Material 1.12 otherwise hides the sheet first, leaving it hidden when a discard
 * confirmation is declined. Non-editing sheets keep Material's normal animation.
 */
class GuardedBottomSheetDialog extends BottomSheetDialog {
    // Object deliberately keeps API 33 framework classes out of this class's signatures.
    private Object backRegistration;
    private boolean attached;
    private boolean editorCancelable = true;

    GuardedBottomSheetDialog(Context context) {
        super(context);
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        updateBackRegistration();
    }

    @Override public void onDetachedFromWindow() {
        attached = false;
        clearBackRegistration();
        super.onDetachedFromWindow();
    }

    @Override public void setCancelable(boolean cancelable) {
        super.setCancelable(cancelable);
        editorCancelable = cancelable;
        updateBackRegistration();
    }

    private void updateBackRegistration() {
        clearBackRegistration();
        if (Build.VERSION.SDK_INT >= 33 && attached && editorCancelable && getWindow() != null) {
            backRegistration = Api33.register(getWindow(), this::handleSystemBack);
        }
    }

    private void clearBackRegistration() {
        if (Build.VERSION.SDK_INT >= 33 && backRegistration != null) {
            Api33.unregister(backRegistration);
            backRegistration = null;
        }
    }

    private void handleSystemBack() {
        // PRIORITY_OVERLAY also outranks the IME. Preserve keyboard-first Back.
        Window window = getWindow();
        View decor = window == null ? null : window.getDecorView();
        WindowInsetsCompat insets = decor == null ? null : ViewCompat.getRootWindowInsets(decor);
        WindowInsetsControllerCompat controller = decor == null ? null : ViewCompat.getWindowInsetsController(decor);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime()) && controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
            return;
        }
        // Virtual dispatch is essential: each editor overrides cancel() to protect its draft.
        cancel();
    }

    /** Loaded only on Android 13+, so API 23 never resolves android.window types. */
    @androidx.annotation.RequiresApi(33)
    private static final class Api33 {
        static Object register(Window window, Runnable action) {
            android.window.OnBackInvokedDispatcher dispatcher = window.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = action::run;
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            return new Registration(dispatcher, callback);
        }

        static void unregister(Object token) {
            Registration registration = (Registration) token;
            registration.dispatcher.unregisterOnBackInvokedCallback(registration.callback);
        }

        private static final class Registration {
            final android.window.OnBackInvokedDispatcher dispatcher;
            final android.window.OnBackInvokedCallback callback;

            Registration(android.window.OnBackInvokedDispatcher dispatcher,
                         android.window.OnBackInvokedCallback callback) {
                this.dispatcher = dispatcher;
                this.callback = callback;
            }
        }
    }
}
