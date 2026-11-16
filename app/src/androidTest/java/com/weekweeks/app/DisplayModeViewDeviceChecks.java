package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

/** Exercises shown UI with fictional callbacks; never persists courses, events or preferences. */
final class DisplayModeViewDeviceChecks {
    private DisplayModeViewDeviceChecks() { }

    static void run(Instrumentation test) throws Throwable {
        check("com.weekweeks.app.audit".equals(test.getTargetContext().getPackageName()),
                "Display-mode UI checks require the audit package");
        Activity activity = test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Dialog[] dialog = {null};
        PastelTimetableView[] view = {null};
        int[] eventAdds = {0}, courseAdds = {0}, eventOpens = {0};
        long[] selectedDate = {Long.MIN_VALUE};
        PastelTimetableView.Entry course = new PastelTimetableView.Entry();
        course.id = 91001; course.name = "Audit mode course"; course.day = 1;
        course.start = 1; course.end = 2; course.room = "Audit room";
        PastelTimetableView.EventEntry event = new PastelTimetableView.EventEntry();
        event.id = 92001; event.title = "Audit mode event"; event.day = 1;
        event.startMinutes = 600; event.time = "10:00"; event.endMillis = Long.MAX_VALUE;
        Calendar monday = Calendar.getInstance();
        monday.clear(); monday.set(2026, Calendar.NOVEMBER, 16);
        long[] dates = AgendaDates.weekdays(monday.getTimeInMillis());
        try {
            test.waitForIdleSync();
            ui(test, () -> {
                PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                        PastelTimetableView.Actions.class.getClassLoader(),
                        new Class<?>[]{PastelTimetableView.Actions.class}, (proxy, method, args) -> {
                            if ("onAddEvent".equals(method.getName())) eventAdds[0]++;
                            if ("onAdd".equals(method.getName())) courseAdds[0]++;
                            if ("onEvent".equals(method.getName())) eventOpens[0]++;
                            if ("onAddEventOnDate".equals(method.getName())) selectedDate[0] = (Long) args[0];
                            return null;
                        });
                view[0] = new PastelTimetableView(activity, false, null, actions);
                bind(view[0], course, false, false);
                view[0].setEvents(Collections.singletonList(event), null, dates);
                dialog[0] = new Dialog(activity);
                dialog[0].setContentView(view[0]);
                dialog[0].show();
                dialog[0].getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
            });
            awaitGrid(test, view[0]);
            verifyWeekLayouts(test, activity, view[0], course, event, dates, selectedDate, eventOpens);
            verifyListLayouts(test, activity, view[0], course, event, dates, eventAdds);
            verifyOutsideSemester(test, activity, view[0], dates, selectedDate, courseAdds, eventAdds);
            verifyMine(test, activity, view[0]);
            verifyLargeTextAddIcons(test, activity);
            Bundle result = new Bundle();
            result.putString("stream", "PASS display-mode UI: grid/list filtering, empty states, mode-safe "
                    + "actions, course-lock isolation, retained layout, Mine visibility/restoration, "
                    + "and 280dp/200% font add icons in light and dark themes.\n");
            test.sendStatus(0, result);
        } finally {
            ui(test, () -> {
                if (dialog[0] != null) dialog[0].dismiss();
                activity.finish();
            });
            test.waitForIdleSync();
        }
    }

    private static void verifyWeekLayouts(Instrumentation test, Activity activity, PastelTimetableView view,
                                         PastelTimetableView.Entry course, PastelTimetableView.EventEntry event,
                                         long[] dates, long[] selectedDate, int[] eventOpens) throws Throwable {
        ui(test, () -> {
            View grid = (View) field("grid").get(view);
            check(findText(grid, course.name) != null && findText(grid, event.title) != null,
                    "Default Combined mode must show both types in week view");
            view.setDisplayMode(DisplayMode.TIMETABLE);
            check(!view.isAgenda(), "Timetable mode changed the selected week layout");
            check(findText(grid, course.name) != null && findText(grid, event.title) == null,
                    "Timetable week view did not filter personal events");
            view.setDisplayMode(DisplayMode.AGENDA);
            check(!view.isAgenda(), "Agenda mode must preserve the week layout");
            check(findText(grid, course.name) == null && findText(grid, event.title) != null,
                    "Agenda week view did not filter courses");
            check(findText(grid, activity.getString(R.string.view_period_heading)) == null,
                    "Agenda week view still contains a period ruler");
            check(!((View) field("heroFrame").get(view)).isShown(), "Course hero leaked into Agenda mode");
            check(!((View) field("importButton").get(view)).isShown(), "Course import leaked into Agenda mode");
            view.setCountdown(System.currentTimeMillis() + 60000, false);
            check(!((View) field("countdown").get(view)).isShown(), "Course countdown leaked into Agenda mode");
            String tuesday = activity.getResources().getStringArray(R.array.view_days)[1];
            check(findDescription(grid, activity.getString(R.string.view_cell_desc, tuesday, 1)) == null,
                    "Agenda week view still has a blank add-course cell");
            View add = findDescription(grid, activity.getString(R.string.display_mode_event_add_on_day, tuesday));
            check(add != null && add.performClick(), "Agenda week view has no date-specific add action");
            check(selectedDate[0] == dates[1], "Agenda week add action changed the selected date");
            View eventCard = findDescription(grid, activity.getString(R.string.week_event_description,
                    tuesday, event.time, event.title));
            check(eventCard != null && eventCard.performClick() && eventOpens[0] == 1,
                    "Agenda week event cannot be opened");
            view.setDisplayMode(DisplayMode.COMBINED);
            check(findText(grid, course.name) != null && findText(grid, event.title) != null,
                    "Changing display modes lost previously bound data");
            check(((View) field("countdown").get(view)).isShown(),
                    "A future course countdown did not return with Combined mode");
        });
        test.waitForIdleSync();
    }

    private static void verifyListLayouts(Instrumentation test, Activity activity, PastelTimetableView view,
                                         PastelTimetableView.Entry course, PastelTimetableView.EventEntry event,
                                         long[] dates, int[] eventAdds) throws Throwable {
        ui(test, () -> {
            view.showAgenda();
            View list = (View) field("list").get(view);
            check(findText(list, course.name) != null && findText(list, event.title) != null,
                    "Combined list is missing an item type");
            View staleAdd = findDescription(list, activity.getString(R.string.view_add_event));
            check(staleAdd != null, "Combined list lacks an add-event action");
            view.setDisplayMode(DisplayMode.TIMETABLE);
            check(view.isAgenda(), "Timetable mode changed the selected list layout");
            check(findText(list, course.name) != null && findText(list, event.title) == null,
                    "Timetable list did not filter personal events");
            check(findDescription(list, activity.getString(R.string.view_add_event)) == null,
                    "Timetable list still offers event creation");
            staleAdd.performClick();
            check(eventAdds[0] == 0, "A stale event button bypassed Timetable mode");
            view.setDisplayMode(DisplayMode.AGENDA);
            check(view.isAgenda(), "Agenda mode changed the selected list layout");
            check(findText(list, course.name) == null && findText(list, event.title) != null,
                    "Agenda list did not filter courses");
            bind(view, null, true, false);
            view.setEvents(Collections.emptyList(), null, dates);
            check(findText(list, activity.getString(R.string.display_mode_agenda_empty)) != null,
                    "Agenda empty state is missing");
            check(findText(view, activity.getString(R.string.view_notice_storage)) == null,
                    "Agenda mode reports the unrelated course storage failure");
            check(findText(list, activity.getString(R.string.view_add_course)) == null
                            && findText(list, activity.getString(R.string.view_open_settings)) == null,
                    "Agenda empty state offers course-only recovery actions");
            View add = findDescription(list, activity.getString(R.string.view_add_event));
            check(add != null && add.performClick() && eventAdds[0] == 1,
                    "Course storage lock blocked event creation in Agenda mode");
            bind(view, course, false, false);
            view.setEvents(Collections.singletonList(event), null, dates);
            view.setDisplayMode(DisplayMode.COMBINED);
            check(findText(list, course.name) != null && findText(list, event.title) != null,
                    "Combined list did not restore both item types");
        });
        test.waitForIdleSync();
    }

    private static void verifyOutsideSemester(Instrumentation test, Activity activity, PastelTimetableView view,
                                              long[] dates, long[] selectedDate, int[] courseAdds,
                                              int[] eventAdds) throws Throwable {
        ui(test, () -> {
            view.restoreUiState(false, 0);
            bind(view, null, true, true);
            view.setEvents(Collections.emptyList(), null, dates);
            view.setDisplayMode(DisplayMode.AGENDA);
        });
        awaitGrid(test, view);
        ui(test, () -> {
            View grid = (View) field("grid").get(view);
            String monday = activity.getResources().getStringArray(R.array.view_days)[0];
            View add = findDescription(grid, activity.getString(R.string.display_mode_event_add_on_day, monday));
            check(add != null && add.performClick() && selectedDate[0] == dates[0],
                    "Holiday Agenda add action was blocked by course storage");
            check(findText(grid, activity.getString(R.string.display_mode_agenda_empty)) != null,
                    "Empty Agenda week grid lacks its event-specific state");
            view.setDisplayMode(DisplayMode.TIMETABLE);
            View cell = findDescription(grid, activity.getString(R.string.display_mode_course_outside_cell, monday));
            check(cell != null && cell.performLongClick(), "Timetable holiday cell is missing");
            check(selectedDate[0] == dates[0] && eventAdds[0] == 1 && courseAdds[0] == 0,
                    "Timetable holiday cell triggered course or hidden event creation");
            check(!view.isAgenda(), "Timetable holiday cell switched to the event layout");
            bind(view, null, false, true);
            view.showAgenda();
            check(findText((View) field("list").get(view),
                    activity.getString(R.string.display_mode_course_outside_empty)) != null,
                    "Timetable holiday empty state is not mode-specific");
        });
        test.waitForIdleSync();
    }

    private static void verifyMine(Instrumentation test, Activity activity, PastelTimetableView view) throws Throwable {
        int[] adds = {0}, settings = {0};
        PastelCourseSheet.MinePage[] mine = {null};
        PersonalEvent event = new PersonalEvent();
        event.id = 93001; event.year = 2027; event.month = 1; event.day = 18;
        event.startMinutes = 600; event.title = "Audit Mine mode event";
        ui(test, () -> {
            mine[0] = new PastelCourseSheet(activity, false, null).minePage(
                    () -> adds[0]++, ignored -> { }, () -> settings[0]++);
            mine[0].setEvents(Collections.singletonList(event));
            view.setMinePage(mine[0].view);
            view.setMineSelected(true);
        });
        test.waitForIdleSync();
        ui(test, () -> {
            check(findText(mine[0].view, event.title) != null, "Mine event did not bind");
            View add = findDescription(mine[0].view, activity.getString(R.string.sheet_add_event));
            check(add != null, "Mine add-event action is missing");
            mine[0].setDisplayMode(DisplayMode.TIMETABLE);
            view.setDisplayMode(DisplayMode.TIMETABLE);
            check(view.isMine(), "Display mode selection left the Mine tab");
            check(findText(mine[0].view, event.title) == null
                            && findDescription(mine[0].view, activity.getString(R.string.sheet_add_event)) == null,
                    "Timetable mode left Mine events or creation visible");
            check(findText(mine[0].view, activity.getString(R.string.display_mode_mine_courses_only)) != null,
                    "Mine gives no explanation for hidden events");
            add.performClick();
            check(adds[0] == 0, "A stale Mine add button bypassed Timetable mode");
            View settingsRow = findDescription(mine[0].view, activity.getString(R.string.sheet_label_with_sub,
                    activity.getString(R.string.sheet_settings), activity.getString(R.string.sheet_settings_sub)));
            check(settingsRow != null && settingsRow.performClick() && settings[0] == 1,
                    "Timetable mode hid Mine settings");
            mine[0].setDisplayMode(DisplayMode.AGENDA);
            check(findText(mine[0].view, event.title) != null,
                    "Restoring Agenda mode lost the bound Mine event");
            View restoredAdd = findDescription(mine[0].view, activity.getString(R.string.sheet_add_event));
            check(restoredAdd != null && restoredAdd.performClick() && adds[0] == 1,
                    "Mine event creation did not return with Agenda mode");
        });
        test.waitForIdleSync();
    }

    private static void bind(PastelTimetableView view, PastelTimetableView.Entry course,
                             boolean locked, boolean holiday) {
        view.bind(course == null ? Collections.emptyList() : Collections.singletonList(course),
                Arrays.asList("08:00-08:45", "08:55-09:40"), "Audit week", false, true, true,
                course, "", "", locked, holiday);
    }

    private static void verifyLargeTextAddIcons(Instrumentation test, Activity activity) throws Throwable {
        for (boolean dark : new boolean[]{false, true}) {
            Dialog[] dialog = {null};
            PastelTimetableView[] view = {null};
            Context[] context = {null};
            int[] additions = {0};
            try {
                ui(test, () -> {
                    Configuration config = new Configuration(activity.getResources().getConfiguration());
                    config.fontScale = 2f;
                    config.setLocale(Locale.ENGLISH);
                    context[0] = activity.createConfigurationContext(config);
                    PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                            PastelTimetableView.Actions.class.getClassLoader(),
                            new Class<?>[]{PastelTimetableView.Actions.class}, (proxy, method, args) -> {
                                if ("onAddEvent".equals(method.getName())) additions[0]++;
                                return null;
                            });
                    view[0] = new PastelTimetableView(context[0], dark, null, actions);
                    bind(view[0], null, false, false);
                    view[0].setDisplayMode(DisplayMode.AGENDA);
                    dialog[0] = new Dialog(activity);
                    dialog[0].setContentView(view[0]);
                    dialog[0].show();
                    int width = Math.round(280 * activity.getResources().getDisplayMetrics().density);
                    dialog[0].getWindow().setLayout(width, WindowManager.LayoutParams.MATCH_PARENT);
                });
                awaitGrid(test, view[0]);
                ui(test, () -> {
                    View grid = (View) field("grid").get(view[0]);
                    String monday = context[0].getResources().getStringArray(R.array.view_days)[0];
                    View add = findDescription(grid, context[0].getString(R.string.display_mode_event_add_on_day, monday));
                    check(add != null && add.getWidth() > 0 && add.getHeight() > 0,
                            "Large-font add control did not finish layout");
                    TextView previousGlyph = new TextView(context[0]);
                    previousGlyph.setTextSize(20);
                    check(previousGlyph.getPaint().measureText("＋") > add.getWidth(),
                            "The narrow large-font fixture must reproduce the old full-width glyph overflow");
                    Bitmap image = Bitmap.createBitmap(add.getWidth(), add.getHeight(), Bitmap.Config.ARGB_8888);
                    try {
                        add.draw(new Canvas(image));
                        int iconColor = UiSemanticPalette.of(dark, PastelAccent.classic(dark).accent).secondary;
                        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                            if (image.getPixel(x, y) != iconColor) continue;
                            minX = Math.min(minX, x); minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                        }
                        check(maxX > minX && maxY > minY, "The add icon is not visibly painted");
                        check(minX > 0 && minY > 0 && maxX < image.getWidth() - 1 && maxY < image.getHeight() - 1,
                                "The add icon touches a clipped edge under 200% text scaling");
                        check(Math.abs((minX + maxX + 1) / 2f - image.getWidth() / 2f) <= 1.5f
                                        && Math.abs((minY + maxY + 1) / 2f - image.getHeight() / 2f) <= 1.5f,
                                "The add icon is not centered in its touch area");
                    } finally {
                        image.recycle();
                    }
                    check(add.performClick() && additions[0] == 1,
                            "The large-font vector add action lost its accessible click behavior");
                    TextView empty = (TextView) findText(grid, context[0].getString(R.string.display_mode_agenda_empty));
                    check(empty != null && empty.getLayout() != null
                                    && empty.getLayout().getLineBottom(empty.getLayout().getLineCount() - 1)
                                    <= empty.getHeight() - empty.getCompoundPaddingTop() - empty.getCompoundPaddingBottom(),
                            "The English Agenda empty state clips under 200% text scaling");
                });
            } finally {
                ui(test, () -> { if (dialog[0] != null) dialog[0].dismiss(); });
                test.waitForIdleSync();
            }
        }
    }

    private static Field field(String name) throws Exception {
        Field field = PastelTimetableView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static View findText(View root, String value) {
        if (!root.isShown()) return null;
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findDescription(View root, String value) {
        if (!root.isShown()) return null;
        if (value.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void awaitGrid(Instrumentation test, PastelTimetableView view) throws Throwable {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        do {
            ui(test, () -> {
                ViewGroup grid = (ViewGroup) field("grid").get(view);
                ready[0] = grid.isAttachedToWindow() && grid.getWidth() > 0
                        && grid.getChildCount() > 0 && !grid.isLayoutRequested();
            });
            if (ready[0]) return;
            SystemClock.sleep(16);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Display-mode week grid never finished layout");
    }

    private interface CheckedAction { void run() throws Throwable; }

    private static void ui(Instrumentation test, CheckedAction action) throws Throwable {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } });
        if (error[0] != null) throw error[0];
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
