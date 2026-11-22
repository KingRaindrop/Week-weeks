package com.weekweeks.app;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Source-wiring checks for windows outside the course/event sheets; real windows need device coverage. */
public class ModalChainFlowTest {
    private String source(String name) throws Exception {
        Path root = Path.of("src/main/java/com/weekweeks/app");
        if (!Files.exists(root)) root = Path.of("app/src/main/java/com/weekweeks/app");
        return Files.readString(root.resolve(name)).replace("\r\n", "\n");
    }

    private String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("missing " + signature, start >= 0);
        int next = source.indexOf("\n    private ", start + signature.length());
        return source.substring(start, next < 0 ? source.length() : next);
    }

    @Test public void everyPrimaryDialogClaimsOwnershipBeforeShowing() throws Exception {
        String activity = source("MainActivity.java");
        String[][] owned = {
                {"private void showCourseDialog(", "classic_course"},
                {"private void confirmRandomizeCourseColors(", "randomize_colors"},
                {"private void showFontDialog(", "font"},
                {"private void showLanguageDialog(", "language"},
                {"private void showAccentColorDialog(", "accent"},
                {"private void showPeriodSettingsDialog(", "period_settings"},
                {"private void showNextReminderPopup(", "reminder_popup"},
                {"private void showRemindersDisabledDialog(", "reminders_off"},
                {"private void confirmDeleteCurrentConfiguration(", "delete_config"},
                {"private void showCalendarImportDialog(", "calendar_"},
                {"private void showImportReview(", "import_review"},
                {"private void showImportResult(", "import_result"},
        };
        for (String[] entry : owned) {
            String body = method(activity, entry[0]);
            assertTrue(entry[0], body.contains("acquireModal(") && body.contains(entry[1]));
            assertTrue(entry[0], body.contains("showOwnedDialog(token")
                    || body.contains("showOwnedSettingsDialog(token"));
            assertFalse(entry[0] + " must not bypass ownership", body.contains(".show());"));
        }
        assertFalse("legacy draft tracker replaced owner dismiss listeners", activity.contains("trackSettingsDraft("));
        assertTrue(method(activity, "private void showOwnedSettingsDialog(")
                .contains("showOwnedDialog(token, dialog, false, cleanup, successor)"));
    }

    @Test public void backgroundResultsWaitForIdleInsteadOfCoveringAWindow() throws Exception {
        String activity = source("MainActivity.java");
        String delivery = method(activity, "private void parsedAndShowCourses(");
        assertTrue(delivery.contains("importFlow = new ImportFlowState("));
        assertTrue(delivery.contains("resumeImportFlow();"));
        assertFalse(delivery.contains("showOwnedDialog("));
        String review = method(activity, "private void showImportReview(");
        assertTrue(review.contains("flow.configurePeriods();"));
        assertTrue(review.contains("modalCoordinator.switchAfterDismiss(token, this::resumeImportFlow)"));
        String popup = method(activity, "private void showClassReminderPopup(String name, String time, String room, int minutes, boolean event, long startAt");
        assertTrue(popup.contains("pendingReminderPopups.add(reminder)"));
        assertTrue(popup.contains("uiHandler.post(this::drainDeferredModals)"));
        String drain = method(activity, "private void drainDeferredModals(");
        assertTrue(drain.contains("restoringInteractions || activityRecreating || !activityResumed || !mayOpenModal()"));
        assertTrue(method(activity, "private boolean mayOpenModal()").contains("!settingsOverlayVisible()"));
        assertTrue(method(activity, "private ModalCoordinator.Token acquireModal(").contains("settingsOverlayVisible()"));
        assertTrue(activity.contains("modalCoordinator.setIdleListener(() -> uiHandler.post(this::drainDeferredModals));"));
        String calendar = method(activity, "private void showCalendarImportDialog(");
        assertTrue(calendar.contains("calendarDraft = new CalendarSetupDialog.State(firstRun,"));
        assertTrue(calendar.contains("if (token == null) return;"));
        assertTrue(activity.contains("ImportFlowStateCodec.save(importFlow)"));
        assertTrue(activity.contains("calendarEditor.capture()"));
    }

    @Test public void pasteReviewOpensOnlyAfterPasteSheetCloses() throws Exception {
        String activity = source("MainActivity.java");
        String paste = method(activity, "private void showPasteImport(");
        String submit = paste.substring(paste.indexOf("text -> {"), paste.indexOf("}, restored,"));
        assertFalse("review must not open while the paste sheet still owns the window",
                submit.contains("parsedAndShowCourses("));
        assertTrue(submit.contains("importFlow = new ImportFlowState(result.courses,"));
        assertTrue(paste.contains("}, this::resumeImportFlow)"));
        String sheet = source("PastelCourseSheet.java");
        assertTrue(sheet.contains("if (onAccepted != null) switchSheet(dialog, onAccepted);"));
    }

    @Test public void ownedDialogReleasesTokenAfterCleanupAndOnShowFailure() throws Exception {
        String helper = source("OwnedDialog.java");
        String listener = helper.substring(helper.indexOf("dialog.setOnDismissListener"), helper.indexOf("try {\n            dialog.show();"));
        assertTrue(listener.indexOf("cleanup.run()") < listener.indexOf("coordinator.dismissed(token)"));
        assertTrue(listener.contains("finally"));
        assertTrue(helper.contains("} catch (RuntimeException failure) {\n            coordinator.dismissed(token);"));
        String activity = source("MainActivity.java");
        String destroy = activity.substring(activity.indexOf("protected void onDestroy()"));
        assertTrue(destroy.indexOf("modalCoordinator.destroy();") < destroy.indexOf("ownedDialogs"));
    }
}
