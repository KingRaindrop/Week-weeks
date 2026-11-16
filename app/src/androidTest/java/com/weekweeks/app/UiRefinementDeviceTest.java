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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual UI flows plus narrow-screen geometry, restricted to the disposable audit package. */
public final class UiRefinementDeviceTest extends Instrumentation {
    private static final String APP_PREFS = "week_weeks";
    private static final String DIAGNOSTICS = "week_weeks_reminder_diagnostics_v1";
    private static final String SCHEDULE = "class_reminder_schedule_encrypted_v1";
    private static final String EVENT_A = "界面审查重叠甲", EVENT_B = "界面审查重叠乙", EVENT_C = "界面审查重叠丙";
    private static final SecureStorage STORAGE = new SecureStorage("week_weeks_courses_key");
    private final List<Activity> activities = new ArrayList<>();
    private MainActivity current;
    private SharedPreferences prefs;
    private long browseDate;
    private String courseCipher, eventCipher, expectedPlan;
    private int checks;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Throwable failure = null;
        Context context = getTargetContext();
        Map<String, ?> beforeApp = null, beforeDiagnostics = null;
        SharedPreferences diagnostics = null;
        try {
            check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing production target");
            check(!BuildConfig.ENABLE_TEST_COURSE, "Use ui-refinement-audit.init.gradle");
            check(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED, "Grant the audit package notification permission first");
            try (InputStream font = context.getAssets().open("fonts/oppo_sans.ttf")) {
                check(font.read() >= 0, "UI audit must package the real font asset");
            }
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
                courseCipher = prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null);
                eventCipher = prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null);
                expectedPlan = planEntries();
                check(new JSONArray(expectedPlan).length() == 4, "Fixture must retain its course and all event reminders");
            });
            verifySettingsReturns();
            verifyLiveTimeSummary();
            verifyActualAgendaActions();
            verifyNarrowPresentation(false);
            verifyNarrowPresentation(true);
            ui(() -> assertSources(null));
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
                        // Rescheduling updates audit metadata; restore the original bytes once more.
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
                ? "UI REFINEMENT DEVICE PASS: " + checks + " checks; audit preferences and reminder plan restored\n"
                : "UI REFINEMENT DEVICE FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void installFixture() throws Throwable {
        long termStart = TimetableRules.weekAnchorMillis(System.currentTimeMillis());
        browseDate = AgendaDates.shift(termStart, 7);
        JSONObject course = new JSONObject().put("id", 99101L).put("day", 0).put("period", 1)
                .put("endPeriod", 1).put("name", "界面审查课程").put("weeks", "1-20")
                .put("room", "审查教室").put("teacher", "").put("color", Course.DEFAULT_COLOR);
        JSONArray events = new JSONArray().put(event(99201L, EVENT_A, 540, 660))
                .put(event(99202L, EVENT_B, 555, 630)).put(event(99203L, EVENT_C, 570, 600));
        String courses = STORAGE.encrypt(new JSONArray().put(course).toString());
        String encodedEvents = STORAGE.encrypt(events.toString());
        ui(() -> {
            ReminderScheduler.clear(getTargetContext());
            restore(prefs, new HashMap<>());
            PreferenceTransaction fixture = AcademicCalendarStore.putDates(new PreferenceTransaction(),
                    termStart, AgendaDates.shift(termStart, 20 * 7 - 1))
                    .put("calendar_total_weeks", 20).put(AppLanguage.KEY, AppLanguage.ZH_HANS)
                    .put(AppFonts.KEY, AppFonts.SYSTEM).put(AppFonts.SIZE_KEY, FontSizeRules.DEFAULT)
                    .put("theme_mode", 1).put("notifications_enabled", true)
                    .put(DisplayMode.PREF_KEY, DisplayMode.COMBINED.storedValue)
                    .put(StoredTimetableSnapshot.COURSES_KEY, courses)
                    .put(StoredTimetableSnapshot.EVENTS_KEY, encodedEvents);
            check(SafePreferences.commit(prefs, fixture), "Could not install isolated UI fixture");
        });
    }

    private JSONObject event(long id, String title, int start, int end) throws Exception {
        Calendar date = Calendar.getInstance(); date.setTimeInMillis(browseDate);
        return new JSONObject().put("id", id).put("year", date.get(Calendar.YEAR))
                .put("month", date.get(Calendar.MONTH) + 1).put("day", date.get(Calendar.DAY_OF_MONTH))
                .put("start", start).put("end", end).put("remind", 10).put("title", title)
                .put("location", "").put("note", "").put("showInTimetable", true);
    }

    private void verifySettingsReturns() throws Throwable {
        openSettings();
        ui(() -> inspectSettings(false));
        verifyReminderToggleCurrentValue();
        Anchor theme = scrollToRow("theme");
        screenshot("settings-light-appearance.png");
        clickSettingsRow("theme");
        ui(() -> { choose(dialog(R.string.main_theme_mode_title), 2); dialog(R.string.main_theme_mode_title)
                .getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); });
        settle();
        ui(() -> {
            assertSettingsAnchor(theme, true);
            check(prefs.getInt("theme_mode", -1) == 1, "Canceled theme was persisted");
        });
        clickSettingsRow("theme");
        ui(() -> choose(dialog(R.string.main_theme_mode_title), 2));
        recreateBy(() -> dialog(R.string.main_theme_mode_title).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        ui(() -> {
            check((Boolean) field(current, "isDarkMode"), "Confirmed dark theme did not apply");
            assertSettingsAnchor(theme, true);
            inspectSettings(true);
            assertSources(null);
        });
        screenshot("settings-dark-appearance.png");

        Anchor mode = scrollToRow("display_mode");
        String[] exactSchedule = {null};
        ui(() -> exactSchedule[0] = prefs.getString(SCHEDULE, null));
        clickSettingsRow("display_mode");
        ui(() -> { choose(dialog(R.string.display_mode_title), DisplayMode.AGENDA.ordinal());
            dialog(R.string.display_mode_title).getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); });
        settle();
        ui(() -> {
            assertSettingsAnchor(mode, true);
            check(field(current, "displayMode") == DisplayMode.COMBINED, "Canceled display mode changed presentation");
            assertSources(exactSchedule[0]);
        });
        clickSettingsRow("display_mode");
        ui(() -> { choose(dialog(R.string.display_mode_title), DisplayMode.AGENDA.ordinal());
            dialog(R.string.display_mode_title).getButton(AlertDialog.BUTTON_POSITIVE).performClick(); });
        settle();
        ui(() -> {
            assertSettingsAnchor(mode, true);
            check(field(current, "displayMode") == DisplayMode.AGENDA, "Confirmed display mode was not applied");
            check(!timetable().isAgenda() && !timetable().isMine(), "Mode choice changed navigation layout");
            assertSources(exactSchedule[0]);
        });

        Anchor font = scrollToRow("font");
        clickSettingsRow("font");
        ui(() -> {
            View root = dialog(R.string.main_font_title).getWindow().getDecorView();
            RadioGroup choices = findType(root, RadioGroup.class);
            SeekBar size = findType(root, SeekBar.class);
            check(choices != null && choices.getChildCount() == 2 && size != null, "Font controls missing");
            choices.check(choices.getChildAt(1).getId());
            size.setProgress(FontSizeRules.toProgress(FontSizeRules.MAX));
            check(AppFonts.SYSTEM.equals(AppFonts.current(current)) && AppFonts.sizePercent(current) == 100,
                    "Font preview wrote preferences before save");
        });
        recreateBy(() -> dialog(R.string.main_font_title).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        ui(() -> {
            assertSettingsAnchor(font, false);
            check(AppFonts.OPPO_SANS.equals(AppFonts.current(current)) && AppFonts.sizePercent(current) == 125,
                    "Font confirmation did not retain OPPO Sans at 125%");
            Typeface regular = AppFonts.typeface(current, Typeface.NORMAL);
            check(!regular.equals(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)), "Real font silently fell back to system");
            TextView title = (TextView) tagged(row("font"), "settings:title");
            check(title.getTypeface() == regular, "Restored setting row did not receive the real font");
            check(text(tagged(row("font"), "settings:value")).contains("125"), "Current font size absent from setting value");
            inspectSettings(true);
            assertSources(null);
        });
        screenshot("settings-font125-restored.png");
        Anchor[] rotation = {null};
        ui(() -> rotation[0] = anchor("font"));
        rotate();
        ui(() -> { assertSettingsAnchor(rotation[0], false); assertSources(null); });
        screenshot("settings-parent-rotated.png");
        portrait();
        ui(() -> {
            check(settings() != null, "Parent settings disappeared when returning to portrait");
            assertSources(null);
        });
        verifySettingsComponentScaling();
        closeSettings();
        status("PASS actual settings: cancel/confirm return, theme and font recreation, anchored parent rotation, values and semantic colors");
    }

    private void verifyReminderToggleCurrentValue() throws Throwable {
        scrollToRow("reminders");
        String[] enabledStatus = {null};
        ui(() -> {
            enabledStatus[0] = text(tagged(row("reminder_status"), "settings:value"));
            check(prefs.getBoolean("notifications_enabled", false), "Reminder toggle fixture was not enabled");
            check(!current.getString(R.string.main_status_reminders_off).equals(enabledStatus[0]),
                    "Enabled reminder status already showed the disabled value");
            check(row("reminders").performClick(), "Reminder row did not handle disable click");
            check(!prefs.getBoolean("notifications_enabled", true), "Reminder row did not disable reminders");
            assertReminderSettingValue(current.getString(R.string.main_status_reminders_off), false);
        });
        settle();
        ui(() -> {
            assertReminderSettingValue(current.getString(R.string.main_status_reminders_off), false);
            check(row("reminders").performClick(), "Reminder row did not handle enable click");
            check(prefs.getBoolean("notifications_enabled", false), "Reminder row did not re-enable reminders");
            assertReminderSettingValue(enabledStatus[0], true);
            assertSources(null);
        });
        settle();
        ui(() -> {
            assertReminderSettingValue(enabledStatus[0], true);
            assertSources(null);
        });
    }

    private void assertReminderSettingValue(String expectedStatus, boolean enabled) throws Exception {
        View statusRow = row("reminder_status");
        check(expectedStatus.equals(text(tagged(statusRow, "settings:value"))),
                "Reminder status current value did not update after toggling");
        check(current.getString(R.string.main_title_subtitle_desc,
                        current.getString(R.string.main_notification_status), expectedStatus)
                        .contentEquals(statusRow.getContentDescription()),
                "Reminder status accessibility description did not update after toggling");
        check(current.getString(enabled ? R.string.main_state_on : R.string.main_state_off)
                        .equals(text(tagged(row("reminders"), "settings:value"))),
                "Reminder toggle current value disagrees with the preference");
    }

    private void inspectSettings(boolean dark) throws Throwable {
        View panel = settings();
        for (String section : new String[]{"display", "timetable", "reminders", "time", "appearance", "data"})
            check(tagged(panel, "settings:section:" + section) instanceof ViewGroup, "Missing settings group " + section);
        for (String key : new String[]{"theme", "display_mode", "font", "accent", "language"}) {
            View setting = row(key);
            check(!text(tagged(setting, "settings:title")).trim().isEmpty(), "Empty setting title " + key);
            check(!text(tagged(setting, "settings:value")).trim().isEmpty(), "Missing current setting value " + key);
            check(setting.isClickable() && setting.isFocusable(), "Setting row is not keyboard/click reachable: " + key);
            assertSettingText((ViewGroup) setting, (TextView) tagged(setting, "settings:title"), key + " title");
            assertSettingText((ViewGroup) setting, (TextView) tagged(setting, "settings:value"), key + " current value");
        }
        List<View> descriptions = taggedAll(panel, "settings:description");
        check(!descriptions.isEmpty(), "Grouped settings lost explanatory text");
        for (View description : descriptions)
            check(!text(description).trim().isEmpty(), "Empty setting explanation");
        ImageView delete = (ImageView) tagged(row("delete"), "settings:icon");
        ImageView accent = (ImageView) tagged(row("accent"), "settings:icon");
        check(delete.getImageTintList() != null && accent.getImageTintList() != null, "Setting icons lost semantic tints");
        int danger = delete.getImageTintList().getDefaultColor(), primary = accent.getImageTintList().getDefaultColor();
        check(danger != primary, "Destructive action reused the theme accent");
        check(danger == UiSemanticPalette.of(dark, primary).error, "Delete action is not using the error role");
        UiSemanticPalette blue = UiSemanticPalette.of(dark, 0xFF3857C7), green = UiSemanticPalette.of(dark, 0xFF28744A);
        check(blue.error == green.error && blue.errorContainer == green.errorContainer
                && blue.onErrorContainer == green.onErrorContainer && blue.accent != green.accent,
                "Error roles changed with a non-destructive theme accent");
    }

    /** Real row implementation with captured real values; this does not override the Activity or system font. */
    private void verifySettingsComponentScaling() throws Throwable {
        List<String[]> samples = new ArrayList<>();
        List<String> explanations = new ArrayList<>();
        List<ViewGroup> outerRows = new ArrayList<>();
        List<SettingsRowContent> contents = new ArrayList<>();
        List<TextView[]> labels = new ArrayList<>();
        List<TextView> footers = new ArrayList<>();
        Dialog[] holder = {null};
        LinearLayout[] body = {null};
        String[] exact = {null};
        try {
            ui(() -> {
                exact[0] = prefs.getString(SCHEDULE, null);
                for (String key : new String[]{"theme", "display_mode", "font", "accent", "language"})
                    samples.add(new String[]{text(tagged(row(key), "settings:title")), text(tagged(row(key), "settings:value"))});
                for (View footer : taggedAll(settings(), "settings:description")) explanations.add(text(footer));

                Configuration wideConfig = new Configuration(current.getResources().getConfiguration());
                wideConfig.fontScale = 1f;
                Context wide = current.createConfigurationContext(wideConfig);
                TextView wideTitle = settingsSampleText(wide, samples.get(0)[0], 15);
                TextView wideValue = settingsSampleText(wide, samples.get(0)[1], 13);
                SettingsRowContent firstMeasure = new SettingsRowContent(wide, wideTitle, wideValue);
                firstMeasure.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dp(200), View.MeasureSpec.AT_MOST));
                firstMeasure.layout(0, 0, firstMeasure.getMeasuredWidth(), firstMeasure.getMeasuredHeight());
                check(firstMeasure.getOrientation() == LinearLayout.HORIZONTAL, "Wide settings row did not start horizontal");
                check((wideValue.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.END,
                        "First wide measurement left the current value aligned to the start");
                check(((LinearLayout.LayoutParams) wideValue.getLayoutParams()).leftMargin >= dp(12),
                        "First wide measurement omitted the title/value spacing");
                check(wideTitle.getMeasuredWidth() > 0 && wideValue.getMeasuredWidth() > 0,
                        "Wide first measurement lost one text column");

                Configuration config = new Configuration(current.getResources().getConfiguration());
                config.fontScale = 2f;
                Context narrow = current.createConfigurationContext(config);
                ScrollView scroll = new ScrollView(narrow);
                body[0] = new LinearLayout(narrow);
                body[0].setOrientation(LinearLayout.VERTICAL);
                body[0].setPadding(dp(14), dp(8), dp(14), dp(8));
                scroll.addView(body[0], new ScrollView.LayoutParams(-1, -2));
                for (String[] sample : samples) {
                    LinearLayout outer = new LinearLayout(narrow);
                    outer.setOrientation(LinearLayout.HORIZONTAL);
                    outer.setPadding(dp(12), dp(13), dp(12), dp(13));
                    outer.setGravity(Gravity.CENTER_VERTICAL);
                    outer.setBackgroundColor(UiSemanticPalette.of(true, 0xFF3857C7).surface);
                    outer.addView(new View(narrow), new LinearLayout.LayoutParams(dp(32), dp(22)));
                    TextView title = settingsSampleText(narrow, sample[0], 15);
                    TextView value = settingsSampleText(narrow, sample[1], 13);
                    SettingsRowContent content = new SettingsRowContent(narrow, title, value);
                    outer.addView(content, new LinearLayout.LayoutParams(0, -2, 1f));
                    outer.addView(new View(narrow), new LinearLayout.LayoutParams(dp(24), dp(20)));
                    body[0].addView(outer, new LinearLayout.LayoutParams(-1, -2));
                    outerRows.add(outer); contents.add(content); labels.add(new TextView[]{title, value});
                }
                for (String explanation : explanations) {
                    TextView footer = settingsSampleText(narrow, explanation, 12);
                    footer.setPadding(dp(10), dp(7), dp(10), dp(8));
                    body[0].addView(footer, new LinearLayout.LayoutParams(-1, -2)); footers.add(footer);
                }
                holder[0] = new Dialog(current);
                holder[0].setContentView(scroll); holder[0].show(); AppFonts.install(holder[0]);
                holder[0].getWindow().setLayout(dp(280), WindowManager.LayoutParams.MATCH_PARENT);
            });
            awaitLayout(body[0]);
            ui(() -> {
                for (int i = 0; i < labels.size(); i++) {
                    check(contents.get(i).getOrientation() == LinearLayout.VERTICAL,
                            "Narrow 200% settings row did not stack title and value");
                    TextView title = labels.get(i)[0], value = labels.get(i)[1];
                    assertSettingText(outerRows.get(i), title, "200% captured setting title " + i);
                    assertSettingText(outerRows.get(i), value, "200% captured setting value " + i);
                    check(boundsIn(outerRows.get(i), title).bottom <= boundsIn(outerRows.get(i), value).top,
                            "Narrow large setting title overlaps its current value");
                }
                for (int i = 0; i < footers.size(); i++) assertSettingText(body[0], footers.get(i), "200% grouped explanation " + i);
                assertSources(exact[0]);
            });
            screenshot("settings-components-font200-narrow.png");
        } finally {
            ui(() -> { if (holder[0] != null) holder[0].dismiss(); });
            settle();
        }
        status("PASS SettingsRowContent first wide measurement and 280dp/200% scoped-context rows using actual setting values; Activity stayed at 125%");
    }

    private TextView settingsSampleText(Context context, String value, float size) {
        TextView text = new TextView(context);
        text.setText(value); text.setTextSize(size); text.setIncludeFontPadding(false);
        text.setTypeface(AppFonts.typeface(context, Typeface.NORMAL));
        text.setTextColor(UiSemanticPalette.of(true, 0xFF3857C7).text);
        return text;
    }

    private void verifyLiveTimeSummary() throws Throwable {
        ui(() -> {
            View add = tagged(timetable(), "agenda:add:0");
            check(add.performClick(), "Actual agenda date add did not open editor");
        });
        settle();
        String[] exact = {null};
        ui(() -> exact[0] = prefs.getString(SCHEDULE, null));
        assertTimeInputs("09:00", "10:30", string("event_time_summary", "09:00", "10:30",
                string("event_time_duration_hours_minutes", 1, 30)), exact[0]);
        assertTimeInputs("09:00", "09:30", string("event_time_summary", "09:00", "09:30",
                string("event_time_duration_minutes", 30)), exact[0]);
        assertTimeInputs("09:00", "10:00", string("event_time_summary", "09:00", "10:00",
                string("event_time_duration_hours", 1)), exact[0]);
        assertTimeInputs("09:00", "", string("event_time_summary_no_end", "09:00"), exact[0]);
        assertTimeInputs("23:59", "", string("event_time_summary_no_end", "23:59"), exact[0]);
        assertTimeInputs("", "10:30", string("event_time_error_start_required"), exact[0]);
        assertTimeInputs("25:00", "10:30", string("event_time_error_start_invalid"), exact[0]);
        assertTimeInputs("09:00", "25:00", string("event_time_error_end_invalid"), exact[0]);
        assertTimeInputs("09:00", "09:00", string("event_time_error_order"), exact[0]);
        assertTimeInputs("09:00", "08:30", string("event_time_error_order"), exact[0]);
        ui(() -> {
            TextView feedback = (TextView) tagged(eventDialog().getWindow().getDecorView(), "event:time-feedback");
            check(feedback.getCurrentTextColor() == UiSemanticPalette.of(true, 0xFF3857C7).onErrorContainer,
                    "Invalid time feedback does not use the semantic error foreground");
            Dialog editor = eventDialog();
            clickText(editor.getWindow().getDecorView(), current.getString(R.string.sheet_add_event));
            check(editor.isShowing(), "Invalid time closed the editor");
            assertSources(exact[0]);
        });
        assertTimeInputs("9", "1030", string("event_time_summary", "09:00", "10:30",
                string("event_time_duration_hours_minutes", 1, 30)), exact[0]);
        ui(() -> {
            TextView feedback = (TextView) tagged(eventDialog().getWindow().getDecorView(), "event:time-feedback");
            check(feedback.getCurrentTextColor() == UiSemanticPalette.of(true, 0xFF3857C7).secondary,
                    "Correcting the time did not restore the neutral feedback foreground");
            check("9".contentEquals(eventInputs()[1].getText()) && "1030".contentEquals(eventInputs()[2].getText()),
                    "Live summary rewrote partially formatted input");
            clickText(eventDialog().getWindow().getDecorView(), current.getString(R.string.sheet_tomorrow));
            check(text(tagged(eventDialog().getWindow().getDecorView(), "event:time-feedback")).contains("09:00"),
                    "Date change lost the live time summary");
            assertSources(exact[0]);
        });
        recreateBy(() -> current.recreate());
        ui(() -> {
            check("9".contentEquals(eventInputs()[1].getText()) && "1030".contentEquals(eventInputs()[2].getText()),
                    "Editor recreation lost raw time drafts");
            check(text(tagged(eventDialog().getWindow().getDecorView(), "event:time-feedback")).equals(
                    string("event_time_summary", "09:00", "10:30", string("event_time_duration_hours_minutes", 1, 30))),
                    "Restored editor did not recompute the duration summary");
            assertSources(null);
        });
        screenshot("event-live-time-summary.png");
        discardEventEditor();
        ui(() -> assertSources(null));
        status("PASS actual event editor: live duration/no-end/errors, raw drafts and date/recreation, no data writes");
    }

    private void assertTimeInputs(String start, String end, String expected, String exactSchedule) throws Throwable {
        ui(() -> {
            Object sheet = field(current, "activeEventEditor");
            PersonalEvent draft = (PersonalEvent) field(sheet, "activeEventDraft");
            int originalStart = draft.startMinutes, originalEnd = draft.endMinutes;
            EditText[] inputs = eventInputs();
            inputs[0].setText("尚未保存的界面审查草稿");
            inputs[1].setText(start); inputs[2].setText(end);
            TextView summary = (TextView) tagged(eventDialog().getWindow().getDecorView(), "event:time-feedback");
            check(summary.getVisibility() == View.VISIBLE && expected.contentEquals(summary.getText()),
                    "Unexpected live summary for '" + start + "'/'" + end + "': " + summary.getText());
            check(draft.startMinutes == originalStart && draft.endMinutes == originalEnd,
                    "Typing in the summary mutated the event draft model");
            check(start.contentEquals(inputs[1].getText()) && end.contentEquals(inputs[2].getText()),
                    "Live validation rewrote the raw time input");
            assertSources(exactSchedule);
        });
        settle();
    }

    private void verifyActualAgendaActions() throws Throwable {
        for (int day = 0; day < 7; day++) {
            final int index = day;
            ui(() -> check(tagged(timetable(), "agenda:add:" + index).performClick(), "Missing actual day add " + index));
            settle();
            ui(() -> {
                PersonalEvent draft = (PersonalEvent) field(field(current, "activeEventEditor"), "activeEventDraft");
                check(draft.dayMillis() == AgendaDates.weekdays(browseDate)[index], "Day add chose the wrong calendar date");
                eventDialog().cancel();
            });
            settle();
        }
        ui(() -> {
            View group = tagged(timetable(), "agenda:group:0:540");
            View badge = tagged(group, "agenda:group_count");
            check("3".equals(text(badge)), "Overlap badge is not the independent numeric count");
            check(clickAncestor(badge), "Overlap badge has no selectable event parent");
        });
        settle();
        ui(() -> {
            ListView choices = dialog(R.string.agenda_timeline_events_title).getListView();
            check(choices.getAdapter().getCount() == 3, "Numeric overlap badge lost events");
            for (String title : new String[]{EVENT_A, EVENT_B, EVENT_C}) {
                boolean found = false;
                for (int i = 0; i < choices.getAdapter().getCount(); i++)
                    found |= String.valueOf(choices.getAdapter().getItem(i)).contains(title);
                check(found, "Numeric overlap chooser is missing " + title);
            }
            choose(choices, 2);
        });
        settle();
        ui(() -> {
            check((Long) field(current, "activeEventDetailId") == 99203L, "Overlap badge could not open the third event");
            Object detail = field(current, "activeEventDetail");
            Dialog dialog = (Dialog) field(detail, "mainDialog");
            dialog.cancel();
            assertSources(null);
        });
        settle();
        screenshot("agenda-refined-dark.png");
        status("PASS real seven date-add controls and numeric badge selecting the third overlapping event");
    }

    private void verifyNarrowPresentation(boolean dark) throws Throwable {
        Dialog[] holder = {null};
        PastelTimetableView[] view = {null};
        long[] addedDate = {Long.MIN_VALUE};
        long[][] picked = {null};
        long[] dates = AgendaDates.weekdays(browseDate);
        String[] exact = {null};
        try {
            ui(() -> {
                exact[0] = prefs.getString(SCHEDULE, null);
                Configuration config = new Configuration(current.getResources().getConfiguration());
                config.fontScale = 2f;
                Context context = current.createConfigurationContext(config);
                PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                        PastelTimetableView.Actions.class.getClassLoader(), new Class<?>[]{PastelTimetableView.Actions.class},
                        (proxy, method, args) -> {
                            if ("onAddEventOnDate".equals(method.getName())) addedDate[0] = (Long) args[0];
                            if ("onEvents".equals(method.getName())) picked[0] = ((long[]) args[0]).clone();
                            return null;
                        });
                view[0] = new PastelTimetableView(context, dark, null, actions);
                view[0].setDisplayMode(DisplayMode.AGENDA);
                view[0].bind(Collections.emptyList(), Arrays.asList("08:00-08:45"), "界面优化检查",
                        false, true, true, null, "", "", false, false);
                view[0].setEvents(Arrays.asList(viewEvent(99501L, "短时安排甲", 0, 540, 541),
                        viewEvent(99502L, "短时安排乙", 0, 541, 542), viewEvent(99503L, "短时安排丙", 0, 542, 543)), null, dates);
                holder[0] = new Dialog(current);
                holder[0].setContentView(view[0]); holder[0].show(); AppFonts.install(holder[0]);
                holder[0].getWindow().setLayout(dp(320), WindowManager.LayoutParams.MATCH_PARENT);
            });
            awaitLayout(view[0]);
            Rect[] previous = {null};
            for (int day = 0; day < 7; day++) {
                final int index = day;
                ui(() -> {
                    ViewGroup grid = (ViewGroup) field(view[0], "grid");
                    View add = tagged(grid, "agenda:add:" + index);
                    Rect area = boundsIn(grid, add);
                    check(area.width() >= dp(24) && area.height() >= dp(44), "Date-add hit area collapsed at 200% font");
                    check(previous[0] == null || previous[0].right <= area.left, "Adjacent date-add hit areas overlap");
                    previous[0] = area;
                    tap(grid, area.centerX(), area.bottom - dp(5));
                });
                // ACTION_UP posts View.performClick; let that actual callback run before observing it.
                settle();
                ui(() -> check(addedDate[0] == dates[index], "Date-add lower edge does not dispatch its own day"));
            }
            ui(() -> {
                ViewGroup grid = (ViewGroup) field(view[0], "grid");
                View group = tagged(grid, "agenda:group:0:540");
                TextView badge = (TextView) tagged(group, "agenda:group_count");
                check("3".contentEquals(badge.getText()), "Short overlap group lost its numeric badge");
                assertTextFits(badge, "Overlap badge at 200%");
                Rect badgeArea = boundsIn(grid, badge);
                tap(grid, badgeArea.centerX(), badgeArea.centerY());
            });
            settle();
            ui(() -> {
                check(Arrays.equals(new long[]{99501L, 99502L, 99503L}, picked[0]), "Badge hit did not select all overlapping IDs");
                View notice = tagged(view[0], "timetable:notice");
                check(!notice.isClickable() && !notice.isFocusable(), "Passive hint presents an interactive target");
                ViewGroup noticeParent = (ViewGroup) notice.getParent();
                check(!noticeParent.isClickable(), "Passive hint container still implies navigation");
                for (int i = 0; i < noticeParent.getChildCount(); i++) {
                    View sibling = noticeParent.getChildAt(i);
                    Object kind = optionalField(sibling, "kind");
                    check(kind == null || !("chevron".equals(kind) || "right".equals(kind) || "arrow".equals(kind)),
                            "Passive hint retains a navigation arrow");
                }
                String modeName = text(tagged(view[0], "timetable:mode_label"));
                check(!modeName.trim().isEmpty(), "Current display mode has no separate label");
                String weekName = text(tagged(view[0], "nav:week")), listName = text(tagged(view[0], "nav:list"));
                check(weekName.contains("周") && listName.contains("列表")
                        && text(tagged(view[0], "nav:mine")).contains("我的"), "Navigation still confuses layout with display mode");
                check(clickAncestor(tagged(view[0], "nav:list")) && view[0].isAgenda(), "List navigation is unreachable");
                check(clickAncestor(tagged(view[0], "nav:week")) && !view[0].isAgenda(), "Week navigation is unreachable");
                for (DisplayMode mode : DisplayMode.values()) {
                    view[0].setDisplayMode(mode);
                    check(weekName.equals(text(tagged(view[0], "nav:week")))
                                    && listName.equals(text(tagged(view[0], "nav:list"))), "Display mode renamed navigation layout");
                    String modeResource = mode == DisplayMode.TIMETABLE ? "ui_refinement_mode_courses"
                            : mode == DisplayMode.AGENDA ? "ui_refinement_mode_agenda" : "ui_refinement_mode_combined";
                    check(string("ui_refinement_content_mode", string(modeResource))
                                    .equals(text(tagged(view[0], "timetable:mode_label"))),
                            "Independent mode label does not describe the active content mode");
                }
                view[0].setDisplayMode(DisplayMode.AGENDA);
                assertSources(exact[0]);
            });
            settle();
            screenshot("narrow-font200-" + (dark ? "dark" : "light") + ".png");

            long[] allIds = new long[120];
            List<PastelTimetableView.EventEntry> many = new ArrayList<>();
            for (int i = 0; i < allIds.length; i++) {
                allIds[i] = 99600L + i;
                many.add(viewEvent(allIds[i], "密集安排" + (i + 1), 0, 540, 541));
            }
            ui(() -> {
                picked[0] = null;
                holder[0].getWindow().setLayout(dp(280), WindowManager.LayoutParams.MATCH_PARENT);
                view[0].setEvents(many, null, dates);
            });
            awaitLayout(view[0]);
            ui(() -> {
                ViewGroup grid = (ViewGroup) field(view[0], "grid");
                View group = tagged(grid, "agenda:group:0:540");
                TextView badge = (TextView) tagged(group, "agenda:group_count");
                check("99+".contentEquals(badge.getText()), "Large overlap count did not use its compact badge");
                assertTextFits(badge, "99+ overlap badge at 280dp/200%");
                String description = String.valueOf(group.getContentDescription());
                check(description.contains("120") && description.contains("密集安排1") && description.contains("密集安排120"),
                        "Compact badge lost its exact count or full event accessibility description");
                Rect area = boundsIn(grid, badge);
                tap(grid, area.centerX(), area.centerY());
            });
            settle();
            ui(() -> {
                check(Arrays.equals(allIds, picked[0]), "99+ badge did not dispatch all 120 event IDs in stable order");
                assertSources(exact[0]);
            });
            screenshot("narrow-font200-count120-" + (dark ? "dark" : "light") + ".png");
        } finally {
            ui(() -> { if (holder[0] != null) holder[0].dismiss(); });
            settle();
        }
        status("PASS " + (dark ? "dark" : "light") + " 320dp/200% seven hit regions, passive hint/navigation; 280dp 99+ badge selects all 120 IDs");
    }

    private static PastelTimetableView.EventEntry viewEvent(long id, String title, int day, int start, int end) {
        PastelTimetableView.EventEntry event = new PastelTimetableView.EventEntry();
        event.id = id; event.title = title; event.day = day; event.startMinutes = start;
        event.endMinutes = end; event.hasEnd = true; event.endMillis = Long.MAX_VALUE;
        event.time = PersonalEvent.formatTime(start) + "–" + PersonalEvent.formatTime(end);
        return event;
    }

    private void openSettings() throws Throwable {
        ui(() -> {
            View settings = findDescription(current.getWindow().getDecorView(), current.getString(R.string.view_timetable_settings));
            check(settings != null && settings.performClick(), "Actual settings entry is unreachable");
        });
        settle();
        ui(() -> check(settings() != null, "Settings overlay did not open"));
    }
    private void closeSettings() throws Throwable {
        ui(() -> current.getOnBackPressedDispatcher().onBackPressed());
        settle();
        ui(() -> check(field(current, "settingsOverlay") == null, "Back did not close the parent settings"));
    }
    private void clickSettingsRow(String key) throws Throwable {
        ui(() -> check(row(key).performClick(), "Setting row did not open " + key));
        settle();
        ui(() -> check(field(current, "settingsOverlay") == null, "Parent settings covered its child dialog"));
    }
    private Anchor scrollToRow(String key) throws Throwable {
        ui(() -> {
            ScrollView scroll = (ScrollView) tagged(settings(), "settings:scroll");
            Rect rect = boundsIn(scroll, row(key));
            scroll.scrollTo(0, Math.max(0, scroll.getScrollY() + rect.top - dp(42)));
        });
        settle();
        Anchor[] value = {null}; ui(() -> value[0] = anchor(key)); return value[0];
    }
    private Anchor anchor(String key) throws Throwable {
        ScrollView scroll = (ScrollView) tagged(settings(), "settings:scroll");
        return new Anchor(key, scroll.getScrollY(), boundsIn(scroll, row(key)).top / current.getResources().getDisplayMetrics().density);
    }
    private void assertSettingsAnchor(Anchor expected, boolean sameSize) throws Throwable {
        View panel = settings();
        check(panel != null && panel.isShown(), "Child did not return to parent settings");
        ScrollView scroll = (ScrollView) tagged(panel, "settings:scroll");
        Rect rect = boundsIn(scroll, row(expected.key));
        check(rect.bottom > 0 && rect.top < scroll.getHeight(), "Returned settings row is outside the viewport: " + expected.key);
        if (sameSize) check(Math.abs(scroll.getScrollY() - expected.scrollY) <= dp(2), "Child return lost original settings scroll");
        else check(Math.abs(rect.top / current.getResources().getDisplayMetrics().density - expected.offsetDp) <= 24,
                "Recreation lost the anchored setting row offset: " + expected.key);
    }
    private static final class Anchor {
        final String key; final int scrollY; final float offsetDp;
        Anchor(String key, int scrollY, float offsetDp) { this.key = key; this.scrollY = scrollY; this.offsetDp = offsetDp; }
    }
    private View settings() throws Exception {
        View value = (View) field(current, "settingsOverlay");
        check(value != null && value.getParent() != null, "Parent settings overlay missing"); return value;
    }
    private View row(String key) throws Exception { return tagged(settings(), "settings:row:" + key); }
    private PastelTimetableView timetable() throws Exception { return (PastelTimetableView) field(current, "pastelTimetable"); }
    private EditText[] eventInputs() throws Exception { return (EditText[]) field(field(current, "activeEventEditor"), "activeEventInputs"); }
    private Dialog eventDialog() throws Exception {
        Object sheet = field(current, "activeEventEditor"); check(sheet != null, "Event editor missing");
        Dialog dialog = (Dialog) field(sheet, "activeEventDialog"); check(dialog != null && dialog.isShowing(), "Event editor not visible");
        return dialog;
    }
    private void discardEventEditor() throws Throwable {
        ui(() -> eventDialog().cancel()); settle();
        ui(() -> {
            Object sheet = field(current, "activeEventEditor");
            Dialog confirmation = (Dialog) field(sheet, "confirmation");
            check(confirmation != null && confirmation.isShowing(), "Dirty draft did not require discard confirmation");
            clickText(confirmation.getWindow().getDecorView(), current.getString(R.string.action_discard_changes));
        });
        settle();
    }
    private AlertDialog dialog(int title) throws Exception {
        for (Object value : (List<?>) field(current, "ownedDialogs")) {
            Dialog dialog = (Dialog) value;
            if (dialog instanceof AlertDialog && dialog.isShowing()
                    && findText(dialog.getWindow().getDecorView(), current.getString(title)) != null) return (AlertDialog) dialog;
        }
        throw new AssertionError("Dialog missing: " + current.getString(title));
    }
    private void choose(AlertDialog dialog, int index) { choose(dialog.getListView(), index); }
    private void choose(ListView list, int index) {
        View child = list.getChildAt(index - list.getFirstVisiblePosition());
        check(child != null && list.performItemClick(child, index, list.getAdapter().getItemId(index)), "Choice row not clickable: " + index);
    }
    private void portrait() throws Throwable {
        if (current.getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT)
            recreateBy(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }
    private void rotate() throws Throwable {
        int requested = current.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        recreateBy(() -> current.setRequestedOrientation(requested));
    }
    private void recreateBy(Checked action) throws Throwable {
        MainActivity old = current;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(action);
            current = (MainActivity) waitForMonitorWithTimeout(monitor, 10000);
            check(current != null && current != old, "UI action did not recreate the Activity");
            activities.add(current); settle();
        } finally { removeMonitor(monitor); }
    }
    private void awaitLayout(View view) throws Throwable {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        do {
            settle();
            ui(() -> {
                ready[0] = view.getWidth() > 0 && !view.isLayoutRequested();
                if (ready[0] && view instanceof PastelTimetableView) {
                    ViewGroup grid = (ViewGroup) field(view, "grid");
                    ready[0] = grid.getWidth() > 0 && grid.getChildCount() > 0 && !grid.isLayoutRequested()
                            && (Integer) field(grid, "builtWidth") == grid.getWidth();
                }
            });
        } while (!ready[0] && SystemClock.uptimeMillis() < deadline);
        check(ready[0], "Fixture failed to lay out");
    }
    private String planEntries() throws Exception {
        String encrypted = prefs.getString(SCHEDULE, null);
        check(encrypted != null, "Reminder plan missing");
        return new JSONObject(new SecureStorage("week_weeks_reminder_schedule_key").decrypt(encrypted))
                .getJSONArray("entries").toString();
    }
    private void assertSources(String exactSchedule) throws Exception {
        check(courseCipher.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null)), "UI refinement rewrote course ciphertext");
        check(eventCipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "UI refinement rewrote event ciphertext");
        check(((List<?>) field(current, "courses")).size() == 1
                        && ((List<?>) field(current, "personalEvents")).size() == 3,
                "Presentation-only interaction mutated the original in-memory sources");
        check(prefs.getBoolean("notifications_enabled", false), "UI refinement changed notification preference");
        check(expectedPlan.equals(planEntries()), "UI refinement changed reminder occurrences");
        if (exactSchedule != null) check(exactSchedule.equals(prefs.getString(SCHEDULE, null)), "Read-only UI interaction rescheduled reminders");
    }
    private String string(String name, Object... args) {
        int id = current.getResources().getIdentifier(name, "string", getTargetContext().getPackageName());
        check(id != 0, "Missing UI resource " + name); return current.getString(id, args);
    }
    private static Rect boundsIn(ViewGroup parent, View child) {
        int[] parentLocation = new int[2], childLocation = new int[2];
        parent.getLocationOnScreen(parentLocation); child.getLocationOnScreen(childLocation);
        int x = childLocation[0] - parentLocation[0], y = childLocation[1] - parentLocation[1];
        return new Rect(x, y, x + child.getWidth(), y + child.getHeight());
    }
    private void tap(View parent, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
        try { check(parent.dispatchTouchEvent(down) && parent.dispatchTouchEvent(up), "Hit area did not consume its touch"); }
        finally { down.recycle(); up.recycle(); }
    }
    private void assertTextFits(TextView view, String label) {
        check(view.getVisibility() == View.VISIBLE && view.getLayout() != null && view.getLayout().getLineCount() > 0,
                label + " has no visible text layout");
        int lines = view.getLayout().getLineCount();
        int last = (view.getMaxLines() > 0 ? Math.min(view.getMaxLines(), lines) : lines) - 1;
        check(view.getLayout().getLineBottom(last) <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom(),
                label + " clips vertically");
        check(view.getPaint().measureText(view.getText().toString()) <= view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight() + 1,
                label + " clips horizontally: text=" + view.getPaint().measureText(view.getText().toString())
                        + "px, width=" + view.getWidth() + "px, padding="
                        + (view.getCompoundPaddingLeft() + view.getCompoundPaddingRight())
                        + "px, textSize=" + view.getTextSize());
    }
    private void assertSettingText(ViewGroup row, TextView label, String description) {
        check(label.getVisibility() == View.VISIBLE && label.getLayout() != null && label.getLayout().getLineCount() > 0,
                "Setting text has no layout: " + description);
        int last = label.getLayout().getLineCount() - 1;
        check(label.getLayout().getLineBottom(last) <= label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom(),
                "Setting text clips vertically: " + description);
        Rect bounds = boundsIn(row, label);
        check(bounds.left >= 0 && bounds.right <= row.getWidth() && bounds.top >= 0 && bounds.bottom <= row.getHeight(),
                "Setting text extends outside its row: " + description);
        for (int line = 0; line <= last; line++)
            check(label.getLayout().getEllipsisCount(line) == 0
                            && label.getLayout().getLineWidth(line) <= label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight() + 1,
                    "Setting text hides its current value: " + description);
    }
    private static View tagged(View root, String tag) {
        View found = root.findViewWithTag(tag); if (found == null) throw new AssertionError("Missing UI tag: " + tag); return found;
    }
    private static List<View> taggedAll(View root, String tag) {
        List<View> result = new ArrayList<>(); collectTagged(root, tag, result); return result;
    }
    private static void collectTagged(View view, String tag, List<View> result) {
        if (tag.equals(view.getTag())) result.add(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collectTagged(((ViewGroup) view).getChildAt(i), tag, result);
    }
    private static String text(View view) {
        if (view instanceof TextView) return ((TextView) view).getText().toString();
        StringBuilder result = new StringBuilder();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result.append(text(((ViewGroup) view).getChildAt(i))).append(' ');
        return result.toString().trim();
    }
    private static <T extends View> T findType(View view, Class<T> kind) {
        if (kind.isInstance(view)) return kind.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T result = findType(((ViewGroup) view).getChildAt(i), kind); if (result != null) return result;
        }
        return null;
    }
    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findText(((ViewGroup) root).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }
    private void clickText(View root, String label) {
        check(clickTextAction(root, label), "Visible action missing: " + label);
    }
    private static boolean clickTextAction(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())
                && view.isClickable() && view.performClick()) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (clickTextAction(((ViewGroup) view).getChildAt(i), label)) return true;
        return false;
    }
    private static boolean clickAncestor(View view) {
        for (; view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null)
            if (view.isClickable()) return view.performClick();
        return false;
    }
    private static Object optionalField(Object owner, String name) {
        try { return field(owner, name); } catch (Exception absent) { return null; }
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void invoke(Object owner, String name) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(owner); } catch (InvocationTargetException error) { throw error.getCause(); }
    }
    private void restore(SharedPreferences store, Map<String, ?> snapshot) {
        PreferenceTransaction transaction = new PreferenceTransaction();
        for (String key : store.getAll().keySet()) if (!snapshot.containsKey(key)) transaction.remove(key);
        for (Map.Entry<String, ?> entry : snapshot.entrySet()) transaction.put(entry.getKey(), entry.getValue());
        check(SafePreferences.commit(store, transaction), "Could not restore audit preference snapshot");
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(400); waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot(); check(bitmap != null, "Screenshot unavailable");
        try {
            File base = getTargetContext().getExternalFilesDir(null); check(base != null, "Audit screenshot directory unavailable");
            File directory = new File(base, "ui-refinement"); check(directory.isDirectory() || directory.mkdirs(), "Could not create screenshot directory");
            File output = new File(directory, name);
            try (FileOutputStream stream = new FileOutputStream(output)) { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "Screenshot encoding failed"); }
            status("SCREENSHOT " + output.getAbsolutePath());
        } finally { bitmap.recycle(); }
    }
    private int dp(float value) { return Math.round(value * current.getResources().getDisplayMetrics().density); }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(200); waitForIdleSync(); }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private void status(String message) { Bundle bundle = new Bundle(); bundle.putString("stream", message + "\n"); sendStatus(0, bundle); }
}
