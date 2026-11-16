package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Real sheet controls and saved drafts. Fictional callbacks count actions without persisting data. */
final class RoundTwoUiDeviceChecks {
    private enum Kind { COURSE, EVENT, PASTE }
    private final Instrumentation test;
    private final Activity activity;
    private PastelCourseSheet sheet;
    private int saves, deletes;
    private PersonalEvent savedEvent;

    private RoundTwoUiDeviceChecks(Instrumentation test, Activity activity) {
        this.test = test;
        this.activity = activity;
    }

    static void run(Instrumentation test) throws Throwable {
        check("com.weekweeks.app.audit".equals(test.getTargetContext().getPackageName()),
                "Round two checks refuse production data");
        Activity activity = test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        RoundTwoUiDeviceChecks checks = new RoundTwoUiDeviceChecks(test, activity);
        try {
            test.waitForIdleSync();
            checks.temporaryWeeks();
            checks.supportedWeekShortcuts();
            for (Kind kind : Kind.values()) checks.discardConfirmation(kind);
            checks.deleteConfirmation(Kind.COURSE);
            checks.deleteConfirmation(Kind.EVENT);
            checks.expiredReminderConfirmation();
            checks.status("ROUND TWO UI PASS: temporary weeks, three discard prompts, two delete prompts, "
                    + "and expired reminder decisions survive saved-draft restoration without automatic actions.");
        } finally {
            checks.ui(() -> { if (checks.sheet != null) checks.sheet.dismissForDestroy(); activity.finish(); });
            test.waitForIdleSync();
        }
    }

    private void temporaryWeeks() throws Throwable {
        Bundle[] state = {null};
        ui(() -> {
            openCourse(null, false, 4);
            click(parent(Kind.COURSE), R.string.sheet_temporary_chip);
            View row = (View) field(sheet, "activeTemporaryRow");
            TextView plus = findText(row, "+");
            check(plus != null, "Temporary week increment is missing");
            plus.performClick(); plus.performClick(); plus.performClick();
            state[0] = sheet.captureCourseEditorState();
            check(state[0].getBoolean("temporary_visible"), "Expanded temporary week chooser was not captured");
            check(state[0].getInt("temporary_count") == 4, "Tentative four-week duration was not captured");
            check("1-16周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Tentative count applied without confirmation");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            openCourse(state[0], false, 5); // The real teaching week advanced while the draft was suspended.
            View row = (View) field(sheet, "activeTemporaryRow");
            check(row.getVisibility() == View.VISIBLE, "Temporary week chooser collapsed after restore");
            check(findText(row, "4") != null, "Temporary duration reset after restore");
            check(findText(row, activity.getString(R.string.temporary_from_week, 4)) != null,
                    "A resumed draft silently changed its original starting week");
            TextView apply = findText(row, activity.getString(R.string.sheet_apply));
            check(apply != null && apply.performClick(), "Temporary week apply is missing");
            check("4-7周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Restored temporary selection applied wrong weeks");
            check(sheet.captureCourseEditorState().getBoolean("temporary_visible") == false,
                    "Applied temporary chooser should remain collapsed after restore");
            check(saves == 0, "Tentative week choice must never save a course");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        status("PASS temporary week count, expanded state and starting week survive restore; apply uses the original range");
    }

    private void discardConfirmation(Kind kind) throws Throwable {
        Bundle[] state = {null};
        ui(() -> {
            open(kind, null, false);
            inputs(kind)[0].setText("Unsaved fictional " + kind);
            parent(kind).cancel();
            requireConfirmation("discard");
            state[0] = capture(kind);
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            open(kind, state[0], false);
            requireConfirmation("discard");
            check(saves == 0 && deletes == 0, "Restoring discard must not invoke persistence");
            click(confirmation(), R.string.sheet_think_again);
        });
        test.waitForIdleSync();
        ui(() -> {
            check(sheet.isShowing(), "Declining discard closed the parent editor");
            check(("Unsaved fictional " + kind).contentEquals(inputs(kind)[0].getText()), "Declining discard changed raw input");
            state[0] = capture(kind);
            check(!state[0].containsKey("confirmation"), "Cancelled discard was still captured");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            open(kind, state[0], false);
            check(confirmation() == null, "Cancelled discard reappeared");
            parent(kind).cancel();
            requireConfirmation("discard");
            click(confirmation(), kind == Kind.PASTE ? R.string.sheet_discard : R.string.action_discard_changes);
            check(!sheet.isShowing() && capture(kind) == null, "Confirmed discard left a restorable editor");
            check(saves == 0 && deletes == 0, "Discard invoked a save or delete callback");
        });
        test.waitForIdleSync();
        status("PASS " + kind + " discard prompt survives restore; decline preserves draft; confirmed discard closes once");
    }

    private void supportedWeekShortcuts() throws Throwable {
        ui(() -> {
            openCourse(null, false, 29, 52, "31-52周");
            check("31-52周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Opening a legacy term changed its raw draft");
            TextView all = findText(parent(Kind.COURSE).getWindow().getDecorView(),
                    activity.getString(R.string.sheet_weeks_all_chip, TimetableRules.MAX_WEEK));
            check(all != null && all.performClick(), "Legacy-term shortcut did not use the supported range");
            check(TimetableRules.parseWeeks(inputs(Kind.COURSE)[1].getText().toString()).valid, "All-weeks shortcut generated invalid weeks");
            click(parent(Kind.COURSE), R.string.sheet_odd_weeks);
            check(TimetableRules.parseWeeks(inputs(Kind.COURSE)[1].getText().toString()).valid, "Odd-week shortcut generated invalid weeks");
            click(parent(Kind.COURSE), R.string.sheet_even_weeks);
            check(TimetableRules.parseWeeks(inputs(Kind.COURSE)[1].getText().toString()).valid, "Even-week shortcut generated invalid weeks");
            click(parent(Kind.COURSE), R.string.sheet_temporary_chip);
            View row = (View) field(sheet, "activeTemporaryRow");
            TextView plus = findText(row, "+");
            check(plus != null, "Legacy temporary duration control missing");
            for (int i = 0; i < 10; i++) plus.performClick();
            TextView apply = findText(row, activity.getString(R.string.sheet_apply));
            check(apply != null && apply.performClick(), "Legacy temporary apply missing");
            check("29-30周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Temporary range exceeded parser limit");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            openCourse(null, false, 31, 52, "31-52周");
            TextView temporary = findText(parent(Kind.COURSE).getWindow().getDecorView(), activity.getString(R.string.sheet_temporary_chip));
            check(temporary != null && !temporary.isEnabled(), "Unsupported starting week still offered temporary input");
            check(findText(parent(Kind.COURSE).getWindow().getDecorView(),
                    activity.getString(R.string.sheet_temporary_week_limit, TimetableRules.MAX_WEEK)) != null,
                    "Unsupported temporary range did not explain the limit");
            check("31-52周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Unsupported start rewrote the raw draft");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            openCourse(null, false, 1, 1, "1周");
            TextView even = findText(parent(Kind.COURSE).getWindow().getDecorView(), activity.getString(R.string.sheet_even_weeks));
            check(even != null && !even.isEnabled(), "A one-week term offered an impossible even-week range");
            check("1周".contentEquals(inputs(Kind.COURSE)[1].getText()), "Disabled even-week shortcut changed the draft");
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        status("PASS legacy long-term shortcuts stay within supported weeks; unsupported temporary/even choices preserve raw input");
    }

    private void deleteConfirmation(Kind kind) throws Throwable {
        Bundle[] state = {null};
        int label = kind == Kind.COURSE ? R.string.sheet_delete_course : R.string.sheet_delete_event;
        ui(() -> {
            open(kind, null, true);
            click(parent(kind), label);
            requireConfirmation("delete");
            state[0] = capture(kind);
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            open(kind, state[0], true);
            requireConfirmation("delete");
            check(deletes == 0, "Restoring delete confirmation deleted data");
            click(confirmation(), R.string.sheet_think_again);
        });
        test.waitForIdleSync();
        ui(() -> {
            check(sheet.isShowing() && deletes == 0, "Declining deletion changed the parent or data");
            check(!capture(kind).containsKey("confirmation"), "Cancelled deletion was still captured");
            click(parent(kind), label);
            TextView delete = findText(confirmation().getWindow().getDecorView(), activity.getString(R.string.action_delete));
            check(delete != null, "Delete confirmation action missing");
            delete.performClick();
            delete.performClick(); // A stale queued click must not invoke the removed dialog again.
            check(deletes == 1 && !sheet.isShowing(), "Deletion was not performed exactly once");
            check(saves == 0, "Deletion should not save a separate draft");
        });
        test.waitForIdleSync();
        deletes = 0;
        status("PASS " + kind + " delete prompt survives restore; decline does nothing; confirm invokes one deletion");
    }

    private void expiredReminderConfirmation() throws Throwable {
        Bundle[] state = {null};
        ui(() -> {
            openExpired(null);
            click(parent(Kind.EVENT), R.string.sheet_add_event);
            requireConfirmation("expired_reminder");
            check(saves == 0, "Expired reminder was saved before an explicit decision");
            state[0] = capture(Kind.EVENT);
            sheet.dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(() -> {
            openExpired(state[0]);
            requireConfirmation("expired_reminder");
            check(saves == 0, "Restoring expired reminder automatically saved the event");
            click(confirmation(), R.string.sheet_go_back_edit);
        });
        test.waitForIdleSync();
        ui(() -> {
            check(sheet.isShowing() && saves == 0, "Return to edit saved or dismissed the draft");
            check(!capture(Kind.EVENT).containsKey("confirmation"), "Cancelled expired-reminder decision was still captured");
            click(parent(Kind.EVENT), R.string.sheet_add_event);
            TextView confirm = findText(confirmation().getWindow().getDecorView(), activity.getString(R.string.sheet_save_without_reminder));
            check(confirm != null, "Save-without-reminder action missing");
            confirm.performClick(); confirm.performClick();
            check(saves == 1 && savedEvent != null && savedEvent.remindMinutes == PersonalEvent.NO_REMINDER,
                    "Explicit decision must save exactly one event with reminders disabled");
            check(!sheet.isShowing(), "Confirmed event save did not close the editor");
        });
        test.waitForIdleSync();
        status("PASS expired reminder decision survives restore; no auto-save; explicit confirmation saves once without reminder");
    }

    private void open(Kind kind, Bundle state, boolean editing) {
        if (kind == Kind.COURSE) { openCourse(state, editing, 4); return; }
        sheet = new PastelCourseSheet(activity, false, null);
        if (kind == Kind.EVENT) {
            PersonalEvent event = event(false);
            sheet.showEventEditor(editing, event, candidate -> { saves++; savedEvent = candidate.copy(); return null; },
                    editing ? () -> { deletes++; return true; } : null, state, null);
        } else {
            sheet.showPasteImport("Audit format", "Audit example", text -> { saves++; return null; }, state, null, null);
        }
    }

    private void openCourse(Bundle state, boolean editing, int currentWeek) {
        openCourse(state, editing, currentWeek, 16, "1-16周");
    }

    private void openCourse(Bundle state, boolean editing, int currentWeek, int totalWeeks, String weeks) {
        sheet = new PastelCourseSheet(activity, false, null);
        PastelCourseSheet.Draft draft = new PastelCourseSheet.Draft();
        draft.name = "Audit course"; draft.weeks = weeks; draft.color = 0xFF347B80;
        sheet.showEditor(editing, draft, Arrays.asList("08:00-08:45", "08:55-09:40"),
                new int[]{draft.color}, new String[]{"Audit"}, totalWeeks, currentWeek,
                candidate -> { saves++; return null; }, editing ? () -> { deletes++; return true; } : null, state, null);
    }

    private void openExpired(Bundle state) {
        sheet = new PastelCourseSheet(activity, false, null);
        sheet.showEventEditor(false, event(true), candidate -> { saves++; savedEvent = candidate.copy(); return null; },
                null, state, null);
    }

    private static PersonalEvent event(boolean expired) {
        PersonalEvent event = new PersonalEvent();
        event.title = "Audit event"; event.year = expired ? 2000 : 2099; event.month = 1; event.day = 2;
        event.startMinutes = 600; event.remindMinutes = expired ? 10 : PersonalEvent.NO_REMINDER;
        return event;
    }

    private Bundle capture(Kind kind) {
        return kind == Kind.COURSE ? sheet.captureCourseEditorState()
                : kind == Kind.EVENT ? sheet.captureEventEditorState() : sheet.capturePasteImportState();
    }

    private EditText[] inputs(Kind kind) throws Exception {
        return kind == Kind.PASTE ? new EditText[]{(EditText) field(sheet, "activePasteInput")}
                : (EditText[]) field(sheet, kind == Kind.COURSE ? "activeCourseInputs" : "activeEventInputs");
    }

    private Dialog parent(Kind kind) throws Exception {
        return (Dialog) field(sheet, kind == Kind.COURSE ? "activeCourseDialog"
                : kind == Kind.EVENT ? "activeEventDialog" : "activePasteDialog");
    }

    private Dialog confirmation() throws Exception { return (Dialog) field(sheet, "confirmation"); }

    private void requireConfirmation(String kind) throws Exception {
        check(confirmation() != null && confirmation().isShowing(), "Missing restored " + kind + " confirmation");
        check(kind.equals(field(sheet, "confirmationKind")), "Restored the wrong confirmation kind");
    }

    private void click(Dialog dialog, int resource) {
        TextView control = findAction(dialog.getWindow().getDecorView(), activity.getString(resource));
        check(control != null && control.performClick(), "Missing action: " + activity.getString(resource));
    }

    private static TextView findAction(View view, String value) {
        // A sheet title and its submit button may have identical text, such as "Add event".
        if (view instanceof TextView && view.isClickable() && view.isEnabled()
                && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findAction(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static TextView findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), value); if (found != null) return found;
            }
        }
        return null;
    }

    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private void status(String message) { Bundle result = new Bundle(); result.putString("stream", message + "\n"); test.sendStatus(0, result); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
