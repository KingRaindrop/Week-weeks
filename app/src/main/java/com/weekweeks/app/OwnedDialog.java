package com.weekweeks.app;

import android.app.Dialog;
import android.content.DialogInterface;

/**
 * Binds an unshown framework dialog to one {@link ModalCoordinator} token.
 * This helper owns the dialog's dismiss listener; pass extra cleanup instead of replacing it.
 */
final class OwnedDialog {
    private OwnedDialog() { }

    /**
     * Shows {@code dialog} under {@code token}. On dismissal, {@code cleanup} runs first, then
     * {@code successor} (if any) opens synchronously before ownership is released. A successor
     * reserved earlier by {@link #forward} takes precedence over this one.
     */
    static void show(ModalCoordinator coordinator, ModalCoordinator.Token token, Dialog dialog,
                     Runnable cleanup, Runnable successor) {
        dialog.setOnDismissListener(d -> {
            try {
                if (cleanup != null) cleanup.run();
            } finally {
                if (successor != null) coordinator.switchAfterDismiss(token, successor);
                coordinator.dismissed(token);
            }
        });
        try {
            dialog.show();
            coordinator.opened(token);
        } catch (RuntimeException failure) {
            coordinator.dismissed(token);
            throw failure;
        }
    }

    /** Button listener for AlertDialog buttons that auto-dismiss: opens {@code next} after the dialog is gone. */
    static DialogInterface.OnClickListener forward(ModalCoordinator coordinator, ModalCoordinator.Token token,
                                                   Runnable next) {
        return (dialog, which) -> coordinator.switchAfterDismiss(token, next);
    }
}
