package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual Activity/editor/chooser checks, restricted to the disposable audit package. */
public final class AgendaTimelineDeviceTest extends Instrumentation {
    private static final String APP_PREFS = "week_weeks";
    private static final String DIAGNOSTICS = "week_weeks_reminder_diagnostics_v1";
    private static final String SCHEDULE = "class_reminder_schedule_encrypted_v1";
    private static final String COURSE = "时间轴审查课程";
    private static final String HALF = "阅读30分钟", HOUR = "学习60分钟", TWO_HOURS = "运动120分钟";
    private static final String OVERLAP_A = "重叠安排甲", OVERLAP_B = "重叠安排乙", OVERLAP_C = "重叠安排丙";
    private static final String NO_END = "无结束时间安排", LATE_POINT = "午夜前待办";
    private static final String LATE_SHORT = "午夜前一分钟", EARLY = "零点安排";
    private static final long HALF_ID = 88101L, NO_END_ID = 88107L, LATE_POINT_ID = 88108L;
    private static final SecureStorage STORAGE = new SecureStorage("week_weeks_courses_key");
    private final List<Activity> activities = new ArrayList<>();
    private MainActivity current;
    private SharedPreferences prefs;
    private long browseDate;
    private String courseCipher, eventCipher, expectedPlan;
    private int checks;
    private int originalHalfHeight;
    private String selectedFont = AppFonts.SYSTEM;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        if (args != null) selectedFont = args.getString("font", AppFonts.SYSTEM);
        start();
    }

    @Override public void onStart() {
        Throwable failure = null;
        Context context = getTargetContext();
        Map<String, ?> beforeApp = null, beforeDiagnostics = null;
        SharedPreferences diagnostics = null;
        try {
            check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing production target");
            check(!BuildConfig.ENABLE_TEST_COURSE, "Use agenda-timeline-audit.init.gradle");
            if (!AppFonts.SYSTEM.equals(selectedFont) && !AppFonts.OPPO_SANS.equals(selectedFont))
                throw new IllegalArgumentException("Unsupported timeline audit font: " + selectedFont);
            check(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED, "Grant the audit app notification permission first");
            prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
            diagnostics = context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE);
            beforeApp = new HashMap<>(prefs.getAll());
            beforeDiagnostics = new HashMap<>(diagnostics.getAll());
            installFixture();
            current = (MainActivity) startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activities.add(current);
            settle();
            portrait();
            ui(() -> {
                setField(current, "pastelBrowseDate", browseDate);
                invoke(current, "render");
                timetable().restoreUiState(false, 0);
                checkpointSources();
            });
            awaitGrid();
            verifyDurations();
            captureThemes();
            editDuration();
            verifyOverlapChooser();
            verifyCombinedAndList();
            verifyMidnightBoundaries();
            verifyNarrowLargeText();
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                ui(() -> { for (Activity activity : activities) if (!activity.isDestroyed()) activity.finish(); });
                waitForIdleSync();
                if (beforeApp != null) {
                    Map<String, ?> restoreApp = beforeApp, restoreDiagnostics = beforeDiagnostics;
                    SharedPreferences diagnosticStore = diagnostics;
                    ui(() -> {
                        ReminderScheduler.clear(context);
                        restore(prefs, restoreApp);
                        ReminderScheduler.rescheduleStored(context);
                        restore(prefs, restoreApp);
                        restore(diagnosticStore, restoreDiagnostics);
                        check(restoreApp.equals(prefs.getAll()) && restoreDiagnostics.equals(diagnosticStore.getAll()),
                                "Audit preferences were not restored exactly");
                    });
                }
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
                ? "AGENDA TIMELINE DEVICE PASS: " + checks + " checks; audit preferences restored\n"
                : "AGENDA TIMELINE DEVICE FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void installFixture() throws Throwable {
        long termStart = TimetableRules.weekAnchorMillis(System.currentTimeMillis());
        browseDate = AgendaDates.shift(termStart, 7);
        JSONArray events = new JSONArray()
                .put(event(HALF_ID, HALF, 0, 540, 570))
                .put(event(88102L, HOUR, 1, 540, 600))
                .put(event(88103L, TWO_HOURS, 2, 540, 660))
                .put(event(88104L, OVERLAP_A, 3, 600, 720))
                .put(event(88105L, OVERLAP_B, 3, 615, 675))
                .put(event(88106L, OVERLAP_C, 3, 630, 645))
                .put(event(NO_END_ID, NO_END, 4, 660, PersonalEvent.NO_END));
        JSONObject course = new JSONObject().put("id", 88201L).put("day", 0).put("period", 1)
                .put("endPeriod", 1).put("name", COURSE).put("weeks", "1-20")
                .put("room", "审查教室").put("teacher", "").put("color", Course.DEFAULT_COLOR);
        String encodedCourses = STORAGE.encrypt(new JSONArray().put(course).toString());
        String encodedEvents = STORAGE.encrypt(events.toString());
        ui(() -> {
            ReminderScheduler.clear(getTargetContext());
            restore(prefs, new HashMap<>());
            PreferenceTransaction fixture = AcademicCalendarStore.putDates(new PreferenceTransaction(),
                    termStart, AgendaDates.shift(termStart, 20 * 7 - 1))
                    .put("calendar_total_weeks", 20).put(AppLanguage.KEY, AppLanguage.ZH_HANS)
                    .put(AppFonts.KEY, selectedFont)
                    .put("theme_mode", 1).put("notifications_enabled", true)
                    .put(DisplayMode.PREF_KEY, DisplayMode.AGENDA.storedValue)
                    .put(StoredTimetableSnapshot.COURSES_KEY, encodedCourses)
                    .put(StoredTimetableSnapshot.EVENTS_KEY, encodedEvents);
            check(SafePreferences.commit(prefs, fixture), "Could not install timeline fixture");
        });
    }

    private JSONObject event(long id, String title, int day, int start, int end) throws Exception {
        Calendar date = Calendar.getInstance(); date.setTimeInMillis(AgendaDates.shift(browseDate, day));
        return new JSONObject().put("id", id).put("year", date.get(Calendar.YEAR))
                .put("month", date.get(Calendar.MONTH) + 1).put("day", date.get(Calendar.DAY_OF_MONTH))
                .put("start", start).put("end", end).put("remind", 10).put("title", title)
                .put("location", "").put("note", "").put("showInTimetable", true);
    }

    private void verifyDurations() throws Throwable {
        ui(() -> {
            assertWeek();
            assertSelectedFont(grid(), current);
            View half = card(HALF), hour = card(HOUR), two = card(TWO_HOURS);
            originalHalfHeight = half.getHeight();
            double base = half.getHeight() + dp(4);
            check(Math.abs((hour.getHeight() + dp(4)) / base - 2) < .08,
                    "60-minute block is not twice the 30-minute duration height");
            check(Math.abs((two.getHeight() + dp(4)) / base - 4) < .10,
                    "120-minute block is not four times the 30-minute duration height");
            check(Math.abs(half.getTop() - hour.getTop()) <= 1 && Math.abs(half.getTop() - two.getTop()) <= 1,
                    "Equal start times are not aligned across weekdays");
            check(Math.abs(card(NO_END).getHeight() - half.getHeight()) <= 2,
                    "No-end event has no usable minimum visual height");
            assertNoEnd(NO_END_ID);
            assertSources(null);
            check(new JSONArray(expectedPlan).length() == 8, "Fixture must remind for seven events and its hidden course");
        });
        status("PASS actual Main binding: 30/60/120-minute proportions, equal-time alignment, no-end semantics");
    }

    private void captureThemes() throws Throwable {
        for (int theme : new int[]{1, 2}) {
            ui(() -> check(SafePreferences.commit(prefs, new PreferenceTransaction().put("theme_mode", theme)),
                    "Could not set audit screenshot theme"));
            rebuild(false);
            awaitGrid();
            ui(() -> {
                assertWeek();
                assertSelectedFont(grid(), current);
                check(((Boolean) field(current, "isDarkMode")) == (theme == 2), "Requested screenshot theme did not apply");
                Configuration config = current.getResources().getConfiguration();
                check("zh".equals((Build.VERSION.SDK_INT >= 24 ? config.getLocales().get(0) : config.locale).getLanguage()),
                        "Timeline screenshots must use Chinese resources");
                timetable().restoreUiState(false, 0);
                assertSources(null);
            });
            screenshot(theme == 1 ? "zh-timeline-light.png" : "zh-timeline-dark.png");
        }
        ui(() -> check(SafePreferences.commit(prefs, new PreferenceTransaction().put("theme_mode", 1)),
                "Could not restore light audit theme"));
        rebuild(false);
        awaitGrid();
    }

    private void editDuration() throws Throwable {
        ui(() -> check(card(HALF).performClick(), "Timeline event card was not clickable"));
        settle();
        ui(() -> {
            check((Long) field(current, "activeEventDetailId") == HALF_ID, "Card opened the wrong event");
            Dialog detail = sheetDialog("activeEventDetail", "activeDetailDialog");
            clickText(detail.getWindow().getDecorView(), current.getString(R.string.sheet_edit_event));
        });
        settle();
        ui(() -> {
            Object editor = field(current, "activeEventEditor");
            check(editor != null, "Detail action did not open the real editor");
            EditText[] inputs = (EditText[]) field(editor, "activeEventInputs");
            check("09:30".contentEquals(inputs[2].getText()), "Editor lost the original end time");
            inputs[2].setText("10:30");
            Dialog dialog = (Dialog) field(editor, "activeEventDialog");
            clickText(dialog.getWindow().getDecorView(), current.getString(R.string.sheet_save_changes));
        });
        settle();
        awaitGrid();
        ui(() -> {
            assertWeek();
            check(field(current, "activeEventEditor") == null, "Successful event save left the editor open");
            check(storedEvent(HALF_ID).getInt("end") == 630, "Actual editor did not persist the new end time");
            boolean bound = false;
            for (Object item : (List<?>) field(timetable(), "events")) {
                PastelTimetableView.EventEntry entry = (PastelTimetableView.EventEntry) item;
                if (entry.id == HALF_ID) {
                    bound = true;
                    check(entry.endMinutes == 630 && entry.hasEnd,
                            "Actual Main binding disagrees with the saved end time");
                }
            }
            check(bound, "Saved event disappeared from actual View entries");
            check(card(HALF).getHeight() > originalHalfHeight * 2,
                    "Saving a longer end time did not enlarge the actual timeline block");
            double changed = card(HALF).getHeight() + dp(4), hour = card(HOUR).getHeight() + dp(4);
            check(Math.abs(changed / hour - 1.5) < .08, "Edited 90-minute block has the wrong duration height");
            check(courseCipher.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null)),
                    "Editing an event rewrote the hidden course store");
            check(!eventCipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)),
                    "Editor did not produce a durable event update");
            checkpointSources();
            assertNoEnd(NO_END_ID);
        });
        status("PASS real card → detail → editor → save: duration changed and week layout/date retained");
    }

    private void verifyOverlapChooser() throws Throwable {
        ui(() -> {
            View group = overlapCard();
            View badge = group.findViewWithTag("agenda:group_count");
            check(badge instanceof TextView && "3".contentEquals(((TextView) badge).getText()),
                    "Overlapping timeline group lost its numeric count badge");
            check(group.performClick(), "Overlapping timeline group is not clickable");
        });
        settle();
        ui(this::assertOverlapChoices);
        rebuild(true);
        ui(() -> {
            assertOverlapChoices();
            overlapDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        settle();
        rebuild(false);
        ui(() -> {
            check(dialogWithTitle(current.getString(R.string.agenda_timeline_events_title)) == null,
                    "Canceled overlap chooser returned after recreation");
            assertSources(null);
        });
        portrait();
        awaitGrid();
        ui(() -> check(overlapCard().performClick(), "Overlap group failed to reopen"));
        settle();
        ui(() -> {
            assertOverlapChoices();
            ListView list = overlapDialog().getListView();
            int position = choiceIndex(list, OVERLAP_B);
            View row = list.getChildAt(position - list.getFirstVisiblePosition());
            check(row != null, "Chosen overlap row is not visible");
            list.performItemClick(row, position, list.getAdapter().getItemId(position));
        });
        settle();
        ui(() -> {
            check((Long) field(current, "activeEventDetailId") == 88105L, "Overlap selection opened the wrong detail");
            Dialog detail = sheetDialog("activeEventDetail", "activeDetailDialog");
            check(findText(detail.getWindow().getDecorView(), OVERLAP_B) != null, "Selected event title is missing");
            check(dialogWithTitle(current.getString(R.string.agenda_timeline_events_title)) == null,
                    "Chooser remained behind the event detail");
            clickText(detail.getWindow().getDecorView(), current.getString(R.string.action_close));
        });
        settle();
        ui(() -> { assertWeek(); assertSources(null); });
        status("PASS real overlap group: all choices, rotation recovery, cancel cleanup and selected detail");
    }

    private void verifyCombinedAndList() throws Throwable {
        String[] beforeSchedule = {null};
        ui(() -> beforeSchedule[0] = prefs.getString(SCHEDULE, null));
        chooseMode(DisplayMode.COMBINED);
        awaitGrid();
        ui(() -> {
            check(!timetable().isAgenda(), "Combined mode changed the week layout");
            check(findText(grid(), COURSE) != null && findText(grid(), HALF) != null,
                    "Combined week view lost its course grid or event strip");
            check(findText(grid(), OVERLAP_A) != null && findText(grid(), OVERLAP_B) != null
                            && findText(grid(), OVERLAP_C) == null,
                    "Combined event strip no longer previews its first two events");
            check(Math.abs(card(HALF).getHeight() - card(TWO_HOURS).getHeight()) <= 1,
                    "Combined mode unexpectedly uses timeline block heights");
            timetable().showAgenda();
        });
        settle();
        ui(() -> {
            View list = (View) field(timetable(), "list");
            check(findText(list, COURSE) != null && findText(list, OVERLAP_C) != null && findText(list, NO_END) != null,
                    "Combined list lost course/event items hidden by the strip preview");
            assertSources(beforeSchedule[0]);
        });
        chooseMode(DisplayMode.AGENDA);
        ui(() -> {
            check(timetable().isAgenda(), "Returning to Agenda changed the selected list layout");
            View list = (View) field(timetable(), "list");
            check(findText(list, COURSE) == null && findText(list, OVERLAP_C) != null,
                    "Agenda list filtering regressed");
            timetable().restoreUiState(false, 0);
        });
        awaitGrid();
        ui(() -> { assertWeek(); assertSources(beforeSchedule[0]); });
        status("PASS Combined strip, full list and Agenda restoration; pure presentation preserved sources and schedule");
    }

    private void verifyMidnightBoundaries() throws Throwable {
        ui(() -> {
            JSONArray events = new JSONArray(STORAGE.decrypt(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)));
            events.put(event(LATE_POINT_ID, LATE_POINT, 4, 1439, PersonalEvent.NO_END));
            events.put(event(88109L, LATE_SHORT, 5, 1438, 1439));
            events.put(event(88110L, EARLY, 6, 0, 30));
            check(SafePreferences.commit(prefs, new PreferenceTransaction().put(StoredTimetableSnapshot.EVENTS_KEY,
                    STORAGE.encrypt(events.toString()))), "Could not install boundary fixtures");
            invoke(current, "loadPersonalEvents");
            invoke(current, "scheduleClassReminderNotifications");
            invoke(current, "render");
            checkpointSources();
        });
        awaitGrid();
        ui(() -> {
            View point = card(LATE_POINT), shortEvent = card(LATE_SHORT), early = card(EARLY);
            check(point.getHeight() > 0 && shortEvent.getHeight() > 0, "Midnight events became zero-height blocks");
            check(point.getTop() > early.getTop() && shortEvent.getTop() > early.getTop(),
                    "Late events wrapped into the next day or the top of the axis");
            check(point.getBottom() <= grid().getHeight() && shortEvent.getBottom() <= grid().getHeight(),
                    "Minimum-height midnight event extends outside the grid");
            double minute = (card(HOUR).getHeight() + dp(4)) / 60.0;
            check(Math.abs((point.getTop() - shortEvent.getTop()) - minute) <= 2,
                    "Minimum-height late event moved its true start time");
            check(findText(grid(), "24:00") != null && findText(grid(), "25:00") == null,
                    "Midnight gutter lost 24:00 or invented a next-day hour label");
            assertNoEnd(LATE_POINT_ID);
            assertNoEnd(NO_END_ID);
            String exactSchedule = prefs.getString(SCHEDULE, null);
            invoke(current, "render");
            timetable().tick();
            assertSources(exactSchedule);
        });
        status("PASS 00:00, 23:58–23:59 and 23:59 without end: true anchors, contained blocks and unchanged data");
    }

    private void assertOverlapChoices() throws Throwable {
        ListView list = overlapDialog().getListView();
        check(list.getAdapter().getCount() == 3, "Overlap chooser must include all three events");
        for (String name : new String[]{OVERLAP_A, OVERLAP_B, OVERLAP_C})
            check(choiceIndex(list, name) >= 0, "Missing overlap option: " + name);
    }

    /** Nonempty, narrow two-times-font geometry; callbacks are fictional and cannot save data. */
    private void verifyNarrowLargeText() throws Throwable {
        Dialog[] dialog = {null};
        PastelTimetableView[] view = {null};
        Context[] context = {null};
        long[][] picked = {null};
        long[] addedDate = {Long.MIN_VALUE}, opened = {Long.MIN_VALUE};
        String[] schedule = {null};
        long[] dates = AgendaDates.weekdays(browseDate);
        try {
            ui(() -> {
                schedule[0] = prefs.getString(SCHEDULE, null);
                Configuration configuration = new Configuration(current.getResources().getConfiguration());
                configuration.fontScale = 2f;
                configuration.setLocale(Locale.SIMPLIFIED_CHINESE);
                context[0] = current.createConfigurationContext(configuration);
                PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                        PastelTimetableView.Actions.class.getClassLoader(), new Class<?>[]{PastelTimetableView.Actions.class},
                        (proxy, method, args) -> {
                            if ("onEvents".equals(method.getName())) picked[0] = ((long[]) args[0]).clone();
                            if ("onAddEventOnDate".equals(method.getName())) addedDate[0] = (Long) args[0];
                            if ("onEvent".equals(method.getName())) opened[0] = (Long) args[0];
                            return null;
                        });
                view[0] = new PastelTimetableView(context[0], true, null, actions);
                view[0].setDisplayMode(DisplayMode.AGENDA);
                view[0].bind(Collections.emptyList(), Arrays.asList("08:00-08:45"), "时间轴大字检查",
                        false, true, true, null, "", "", false, false);
                view[0].setEvents(Arrays.asList(viewEvent(89101L, "短时安排", 0, 540, 541),
                        viewEvent(89102L, "大字重叠甲", 1, 540, 600),
                        viewEvent(89103L, "大字重叠乙", 1, 545, 590),
                        viewEvent(89104L, "大字重叠丙", 1, 550, 580)), null, dates);
                dialog[0] = new Dialog(current);
                dialog[0].setContentView(view[0]);
                dialog[0].show();
                AppFonts.install(dialog[0]);
                dialog[0].getWindow().setLayout(dp(280), WindowManager.LayoutParams.MATCH_PARENT);
            });
            long deadline = SystemClock.uptimeMillis() + 5000;
            boolean[] ready = {false};
            do {
                settle();
                ui(() -> {
                    ViewGroup narrowGrid = (ViewGroup) field(view[0], "grid");
                    ready[0] = narrowGrid.getWidth() > 0 && narrowGrid.getChildCount() > 0 && !narrowGrid.isLayoutRequested();
                });
            } while (!ready[0] && SystemClock.uptimeMillis() < deadline);
            check(ready[0], "Narrow nonempty timeline failed to lay out");
            ui(() -> {
                ViewGroup narrowGrid = (ViewGroup) field(view[0], "grid");
                assertSelectedFont(narrowGrid, context[0]);
                View shortCard = describedChild(narrowGrid, "短时安排");
                TextView title = (TextView) findText(shortCard, "短时安排");
                check(title != null && title.isShown() && title.getLayout() != null
                                && title.getLayout().getLineCount() >= 1 && title.getLayout().getLineWidth(0) > 0,
                        "Short block has no visible title line at 280dp/200%");
                int lastLine = title.getLayout().getLineCount() - 1;
                check(title.getLayout().getLineBottom(lastLine)
                                <= title.getHeight() - title.getCompoundPaddingTop() - title.getCompoundPaddingBottom(),
                        "Short event title clips inside its TextView at 200%");
                check(title.getBottom() <= shortCard.getHeight() - shortCard.getPaddingBottom()
                                && shortCard.getBottom() <= narrowGrid.getHeight(),
                        "Short event title or block extends beyond its vertical bounds");
                check(shortCard.performClick() && opened[0] == 89101L, "Short narrow block cannot open its event");
                int clocks = 0;
                for (int i = 0; i < narrowGrid.getChildCount(); i++) {
                    View child = narrowGrid.getChildAt(i);
                    if (!(child instanceof TextView)) continue;
                    TextView clock = (TextView) child;
                    if (!clock.getText().toString().matches("\\d{2}:00")) continue;
                    clocks++;
                    check(clock.getLayout() != null && clock.getPaint().measureText(clock.getText().toString())
                                    <= clock.getWidth() - clock.getCompoundPaddingLeft() - clock.getCompoundPaddingRight() + 1,
                            "Large-font hour label is horizontally clipped");
                    check(clock.getLayout().getLineBottom(0)
                                    <= clock.getHeight() - clock.getCompoundPaddingTop() - clock.getCompoundPaddingBottom(),
                            "Large-font hour label is vertically clipped");
                }
                check(clocks >= 2, "Nonempty narrow timeline has no complete hour axis");
                String monday = context[0].getResources().getStringArray(R.array.view_days)[0];
                String description = context[0].getString(R.string.display_mode_event_add_on_day, monday);
                View add = findDescription(narrowGrid, description);
                check(add != null && add.getWidth() > 0 && add.getHeight() > 0 && add.performClick()
                                && addedDate[0] == dates[0], "Narrow timeline add action lost its exact date");
                View group = describedChild(narrowGrid, "大字重叠甲");
                View badge = group.findViewWithTag("agenda:group_count");
                check(badge instanceof TextView && "3".contentEquals(((TextView) badge).getText()),
                        "Narrow overlapping group lost its numeric count badge");
                check(String.valueOf(group.getContentDescription()).contains("大字重叠乙")
                                && String.valueOf(group.getContentDescription()).contains("大字重叠丙")
                                && group.performClick(), "Narrow overlap group is incomplete or not clickable");
                check(Arrays.equals(new long[]{89102L, 89103L, 89104L}, picked[0]),
                        "Narrow overlap callback lost or reordered event IDs");
            });
            screenshot("zh-timeline-large-text-dark.png");
        } finally {
            ui(() -> { if (dialog[0] != null) dialog[0].dismiss(); });
            settle();
        }
        ui(() -> assertSources(schedule[0]));
        status("PASS nonempty 280dp/200% timeline: visible short title, bounded labels, date add and all overlap IDs");
    }

    /** Six fixed checks per invocation; system runs retain their existing check count. */
    private void assertSelectedFont(ViewGroup timeline, Context context) throws Exception {
        if (AppFonts.SYSTEM.equals(selectedFont)) return;
        check(AppFonts.OPPO_SANS.equals(AppFonts.current(context)), "Timeline did not retain the requested OPPO Sans preset");
        try (java.io.InputStream asset = context.getAssets().open("fonts/oppo_sans.ttf")) {
            check(asset.read() >= 0, "The real OPPO Sans font asset is empty");
        }
        Typeface regular = AppFonts.typeface(context, Typeface.NORMAL);
        Typeface bold = AppFonts.typeface(context, Typeface.BOLD);
        check(regular != null && !regular.equals(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)),
                "AppFonts regular OPPO Sans silently fell back to the platform font");
        check(bold != null && !bold.equals(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)),
                "AppFonts bold OPPO Sans silently fell back to the platform font");
        int cards = 0, clocks = 0;
        boolean cardFontsMatch = true, clockFontsMatch = true;
        for (int i = 0; i < timeline.getChildCount(); i++) {
            View child = timeline.getChildAt(i);
            if ("TimelineCard".equals(child.getClass().getSimpleName())) {
                cards++;
                cardFontsMatch &= ((TextView) field(child, "time")).getTypeface() == bold
                        && ((TextView) field(child, "title")).getTypeface() == bold;
            } else if (child instanceof TextView
                    && ((TextView) child).getText().toString().matches("\\d{2}:00")) {
                clocks++;
                clockFontsMatch &= ((TextView) child).getTypeface() == regular;
            }
        }
        check(cards > 0 && cardFontsMatch, "Timeline card time/title did not receive the actual OPPO Sans bold asset");
        check(clocks > 0 && clockFontsMatch, "Timeline hour labels did not receive the actual OPPO Sans regular asset");
        status("PASS real OPPO Sans asset, card and hour-label fonts: " + cards + " cards, " + clocks + " hour labels");
    }

    private static PastelTimetableView.EventEntry viewEvent(long id, String title, int day, int start, int end) {
        PastelTimetableView.EventEntry event = new PastelTimetableView.EventEntry();
        event.id = id; event.title = title; event.day = day; event.startMinutes = start;
        event.endMinutes = end; event.hasEnd = true; event.endMillis = Long.MAX_VALUE;
        event.time = PersonalEvent.formatTime(start) + "–" + PersonalEvent.formatTime(end);
        return event;
    }

    private static View describedChild(ViewGroup parent, String text) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (String.valueOf(child.getContentDescription()).contains(text)) return child;
        }
        throw new AssertionError("Missing described timeline block: " + text);
    }

    private static View findDescription(View root, String value) {
        if (value.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }

    private int choiceIndex(ListView list, String name) {
        for (int i = 0; i < list.getAdapter().getCount(); i++)
            if (String.valueOf(list.getAdapter().getItem(i)).contains(name)) return i;
        return -1;
    }

    private View overlapCard() throws Exception {
        for (int i = 0; i < grid().getChildCount(); i++) {
            View child = grid().getChildAt(i);
            String description = String.valueOf(child.getContentDescription());
            if (description.contains(OVERLAP_A) && description.contains(OVERLAP_B) && description.contains(OVERLAP_C)) return child;
        }
        throw new AssertionError("Narrow timeline did not offer one selectable overlap group");
    }

    private View card(String title) throws Exception {
        for (int i = 0; i < grid().getChildCount(); i++) {
            View child = grid().getChildAt(i);
            if (String.valueOf(child.getContentDescription()).contains(title)) return child;
        }
        throw new AssertionError("Missing actual event card: " + title);
    }

    private void assertWeek() throws Exception {
        check(field(current, "displayMode") == DisplayMode.AGENDA, "Timeline fixture left Agenda mode");
        check(!timetable().isAgenda() && !timetable().isMine(), "Timeline action changed the selected week layout");
        check((Long) field(current, "pastelBrowseDate") == browseDate, "Timeline action changed the browsed week");
    }

    private void assertNoEnd(long id) throws Exception {
        check(storedEvent(id).getInt("end") == PersonalEvent.NO_END, "Visual minimum duration overwrote no-end storage");
        boolean found = false;
        for (Object item : (List<?>) field(current, "personalEvents")) {
            PersonalEvent event = (PersonalEvent) item;
            if (event.id == id) {
                found = true;
                check(event.endMinutes == PersonalEvent.NO_END && event.endMillis() == event.startMillis(),
                        "Visual duration changed actual no-end event timing");
            }
        }
        check(found, "No-end fixture missing from actual Activity source");
    }

    private JSONObject storedEvent(long id) throws Exception {
        JSONArray events = new JSONArray(STORAGE.decrypt(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)));
        for (int i = 0; i < events.length(); i++) if (events.getJSONObject(i).getLong("id") == id) return events.getJSONObject(i);
        throw new AssertionError("Stored event missing: " + id);
    }

    private void checkpointSources() throws Exception {
        courseCipher = prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null);
        eventCipher = prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null);
        expectedPlan = planEntries();
    }

    private void assertSources(String exactSchedule) throws Exception {
        check(courseCipher.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null)), "Layout rewrote course ciphertext");
        check(eventCipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "Layout rewrote event ciphertext");
        check(prefs.getBoolean("notifications_enabled", false), "Layout disabled reminders");
        check(expectedPlan.equals(planEntries()), "Layout changed reminder entries");
        if (exactSchedule != null) check(exactSchedule.equals(prefs.getString(SCHEDULE, null)), "Pure layout rebuilt reminder schedule");
    }

    private String planEntries() throws Exception {
        String encrypted = prefs.getString(SCHEDULE, null);
        check(encrypted != null && !encrypted.isEmpty(), "Fixture reminder schedule missing");
        return new JSONObject(new SecureStorage("week_weeks_reminder_schedule_key").decrypt(encrypted))
                .getJSONArray("entries").toString();
    }

    private void chooseMode(DisplayMode mode) throws Throwable {
        ui(() -> {
            View settings = findDescription(current.getWindow().getDecorView(), current.getString(R.string.view_timetable_settings));
            check(settings != null && settings.performClick(), "Timeline settings entry is unreachable");
        });
        settle();
        ui(() -> {
            View overlay = (View) field(current, "settingsOverlay");
            check(overlay != null && overlay.getParent() != null, "Timeline settings did not open");
            View row = overlay.findViewWithTag("settings:row:display_mode");
            check(row != null && row.performClick(), "Actual display-mode row is unreachable");
        });
        settle();
        ui(() -> {
            AlertDialog dialog = dialogWithTitle(current.getString(R.string.display_mode_title));
            check(dialog != null, "Mode chooser failed to open");
            ListView list = dialog.getListView();
            int position = mode.ordinal();
            View row = list.getChildAt(position - list.getFirstVisiblePosition());
            check(row != null, "Mode option is not visible");
            list.performItemClick(row, position, list.getAdapter().getItemId(position));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        settle();
        ui(() -> {
            View overlay = (View) field(current, "settingsOverlay");
            check(overlay != null && overlay.getParent() != null, "Timeline mode choice did not return to settings");
            View close = overlay.findViewWithTag("settings:close");
            check(close != null && close.performClick(), "Returned timeline settings cannot be closed");
        });
        settle();
    }

    private void portrait() throws Throwable {
        if (current.getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) rebuild(true);
    }

    private void rebuild(boolean rotate) throws Throwable {
        MainActivity old = current;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(() -> {
                if (rotate) old.setRequestedOrientation(old.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                else old.recreate();
            });
            current = (MainActivity) waitForMonitorWithTimeout(monitor, 10000);
            check(current != null && current != old, "Timeline Activity did not recreate");
            activities.add(current);
            settle();
        } finally { removeMonitor(monitor); }
    }

    private void awaitGrid() throws Throwable {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        do {
            settle();
            ui(() -> ready[0] = grid().isAttachedToWindow() && grid().getWidth() > 0
                    && grid().getChildCount() > 0 && !grid().isLayoutRequested());
            if (ready[0]) return;
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Actual timeline grid did not finish layout");
    }

    private PastelTimetableView timetable() throws Exception { return (PastelTimetableView) field(current, "pastelTimetable"); }
    private ViewGroup grid() throws Exception { return (ViewGroup) field(timetable(), "grid"); }
    private int dp(float value) { return Math.round(value * current.getResources().getDisplayMetrics().density); }
    private Dialog sheetDialog(String ownerField, String dialogField) throws Exception {
        Object sheet = field(current, ownerField);
        check(sheet != null, "Missing active sheet: " + ownerField);
        Dialog dialog = (Dialog) field(sheet, dialogField);
        check(dialog != null && dialog.isShowing(), "Missing visible sheet dialog: " + dialogField);
        return dialog;
    }
    private AlertDialog overlapDialog() throws Exception {
        AlertDialog dialog = dialogWithTitle(current.getString(R.string.agenda_timeline_events_title));
        check(dialog != null, "Overlap chooser missing");
        return dialog;
    }
    private AlertDialog dialogWithTitle(String title) throws Exception {
        for (Object item : (List<?>) field(current, "ownedDialogs")) {
            Dialog dialog = (Dialog) item;
            if (dialog instanceof AlertDialog && dialog.isShowing()
                    && findText(dialog.getWindow().getDecorView(), title) != null) return (AlertDialog) dialog;
        }
        return null;
    }
    private void clickText(View root, String text) {
        View target = findText(root, text);
        check(target != null, "Missing real control: " + text);
        while (target != null && !target.isClickable()) target = target.getParent() instanceof View ? (View) target.getParent() : null;
        check(target != null && target.performClick(), "Real control did not handle click: " + text);
    }
    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findText(((ViewGroup) root).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void invoke(Object owner, String name) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(owner); } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private void restore(SharedPreferences store, Map<String, ?> snapshot) {
        PreferenceTransaction transaction = new PreferenceTransaction();
        for (String key : store.getAll().keySet()) if (!snapshot.containsKey(key)) transaction.remove(key);
        for (Map.Entry<String, ?> entry : snapshot.entrySet()) transaction.put(entry.getKey(), entry.getValue());
        check(SafePreferences.commit(store, transaction), "Could not restore audit preferences");
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(400); waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Could not capture timeline screenshot");
        try {
            File base = getTargetContext().getExternalFilesDir(null);
            check(base != null, "Audit external files directory unavailable");
            File directory = new File(base, "agenda-timeline");
            check(directory.isDirectory() || directory.mkdirs(), "Could not create screenshot directory");
            File output = new File(directory, name);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "Could not encode timeline screenshot");
            }
            status("SCREENSHOT " + output.getAbsolutePath());
        } finally { bitmap.recycle(); }
    }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(150); waitForIdleSync(); }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private void status(String message) { Bundle result = new Bundle(); result.putString("stream", message + "\n"); sendStatus(0, result); }
}
