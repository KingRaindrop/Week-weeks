package com.weekweeks.app;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Classic editor: all user cancellation routes pass through the same draft guard. */
final class DirtyConfirmDialog extends AlertDialog {
    interface DirtyCheck { boolean isDirty(); }

    private final DirtyCheck dirtyCheck;
    private AlertDialog confirmation;

    DirtyConfirmDialog(Context context, DirtyCheck dirtyCheck) {
        super(context);
        this.dirtyCheck = dirtyCheck;
    }

    @Override public void cancel() {
        if (!dirtyCheck.isDirty()) {
            super.cancel();
            return;
        }
        if (confirmation != null && confirmation.isShowing()) return;
        confirmation = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.view_discard_title)
                .setMessage(R.string.view_discard_message)
                .setNegativeButton(R.string.action_continue_editing, null)
                .setPositiveButton(R.string.action_discard_changes, (dialog, which) -> discard())
                .create();
        confirmation.setOnDismissListener(dialog -> confirmation = null);
        confirmation.show();
        AppFonts.install(confirmation);
    }

    private void discard() {
        super.cancel();
    }

    /** Successful saves/deletes dismiss directly, without asking to discard saved work. */
    @Override public void dismiss() {
        AlertDialog pending = confirmation;
        confirmation = null;
        if (pending != null) pending.dismiss();
        super.dismiss();
    }
}
