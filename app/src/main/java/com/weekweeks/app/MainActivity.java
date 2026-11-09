package com.weekweeks.app;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "week_weeks";
    private static final String LEGACY_COURSES_KEY = "courses";
    private static final String COURSES_KEY = "courses_encrypted_v2";
    private static final String THEME_KEY = "theme_mode";
    private static final String ACCENT_KEY = "accent_preset";
    private static final String SHOW_TEACHER_KEY = "show_teacher_name";
    // Shared by both UIs; indices 0–7 and each UI's default remain unchanged.
    private static final int[][] ACCENT_PRESETS = UiAccentPalette.COLORS;
    private static final int DEFAULT_ACCENT_PRESET = BuildConfig.PASTEL_UI ? UiAccentPalette.CORAL_INDEX : 0;
    private static final String REMINDER_CODES_KEY = "class_reminder_codes";
    private static final String NOTIFICATIONS_ENABLED_KEY = "notifications_enabled";
    private static final String PERIOD_TIMES_KEY = "period_times_v1";
    private static final String CALENDAR_START_KEY = "calendar_start_millis";
    private static final String EVENTS_KEY = "personal_events_encrypted_v1";
    private static final String CALENDAR_END_KEY = "calendar_end_millis";
    private static final String CALENDAR_WEEKS_KEY = "calendar_total_weeks";
    private static final String CALENDAR_NAME_KEY = "calendar_name";
    // 首次安装引导：从未保存过校历（且也没有旧版遗留的校历数据）时，强制要求设置开学日/散学日
    private static final String CALENDAR_INITIALIZED_KEY = "calendar_initialized";
    private static final int MAX_FIELD_LENGTH = 60;
    private static final int MAX_COURSES = 200;
    private static final int REQUEST_IMPORT_PDF = 40;
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private static final int MAX_STREAM_BYTES = 2 * 1024 * 1024;
    private static final long PROGRESS_REFRESH_MS = 5000L;
    private static final long COUNTDOWN_REFRESH_MS = 1000L;
    private static final int COUNTDOWN_SECONDS_THRESHOLD = 5 * 60;
    private static final int COUNTDOWN_ANIMATION_MS = 260;
    private static final int CONTENT_SIDE_MARGIN_DP = 28;
    private static final int REQUEST_POST_NOTIFICATIONS = 61;
    private static final int LONG_CLASS_REMINDER_MINUTES = 30;
    private static final int SHORT_CLASS_REMINDER_MINUTES = 10;
    private static final int FLOATING_START_MINUTES = 20;
    private static final int MAX_PERIODS = 20;
    static final String EXTRA_SHOW_REMINDER_POPUP = "show_reminder_popup";
    static final String EXTRA_REMINDER_COURSE_NAME = "reminder_course_name";
    static final String EXTRA_REMINDER_COURSE_TIME = "reminder_course_time";
    static final String EXTRA_REMINDER_COURSE_ROOM = "reminder_course_room";
    static final String EXTRA_REMINDER_LEAD_MINUTES = "reminder_lead_minutes";
    private static final String EXTRA_SEND_REMINDER_TEST_NOTIFICATION = "send_reminder_test_notification";
    private static final int DAY_SWIPE_MIN_DISTANCE_DP = 72;
    private static final double DAY_FIRST_COLUMN_X = 99.08;
    private static final double DAY_COLUMN_WIDTH = 103.85;
    private String[] dayNames;
    private static final String[] DEFAULT_PERIOD_TIMES = {
            "08:00-08:45",
            "08:55-09:40",
            "10:00-10:45",
            "10:55-11:40",
            "14:30-15:15",
            "15:25-16:10",
            "16:20-17:05",
            "17:15-18:00",
            "19:30-20:15",
            "20:25-21:10",
            "21:20-22:05"
    };
    private static final int[] COURSE_COLORS = CoursePalette.appendColors(new int[]{
            Color.rgb(52, 123, 128),
            Color.rgb(68, 111, 166),
            Color.rgb(91, 126, 84),
            Color.rgb(132, 100, 151),
            Color.rgb(174, 105, 92),
            Color.rgb(172, 126, 58),
            Color.rgb(67, 135, 108),
            Color.rgb(89, 109, 158),
            Color.rgb(145, 91, 119),
            Color.rgb(118, 113, 79)
    });
    private String[] courseColorNames;
    private final SecureStorage secureStorage = new SecureStorage("week_weeks_courses_key");
    private final List<Course> courses = new ArrayList<>();
    private final List<PersonalEvent> personalEvents = new ArrayList<>();
    /** Stored events could not be decrypted: never overwrite them. */
    private boolean eventsLocked = false;
    private final List<String> periodTimes = new ArrayList<>();
    private final Map<String, CountdownSnapshot> countdownSnapshots = new HashMap<>();
    private final Map<String, Long> stoppedShakeKeys = new HashMap<>();
    private final Map<String, Long> lastShakeTapMillisByKey = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            refreshTimedUi();
            scheduleMinuteTick();
        }
    };
    private int pendingAddPeriod = 1; // legacy editor default; pastel UI passes the tapped period directly
    private PastelTimetableView pastelTimetable;
    private long pastelBrowseDate; // 0 follows today; otherwise a date in the browsed week.
    private PastelCourseSheet activeEventEditor;
    private long activeEventId;
    private boolean eventFromMine;
    private PastelCourseSheet.MinePage minePage;
    private Bundle pendingEventRestore;
    private PastelCourseSheet activeCourseEditor;
    private long activeCourseId;
    private Bundle pendingCourseRestore;
    private PastelCourseSheet activeEventDetail;
    private long activeEventDetailId;
    private Bundle pendingDetailRestore;
    private interface SettingsDraftCapture { Bundle capture(); }
    private SettingsDraftCapture settingsDraftCapture;
    private SettingsDraftCapture periodRuleDraftCapture;
    private Bundle pendingSettingsRestore;
    private Bundle pendingPeriodRuleRestore;
    private FrameLayout dayTabFrame;
    private View daySelectionSlider;
    private LinearLayout dayTabs;
    private LinearLayout courseList;
    private TextView summaryText;
    private TextView statusChip;
    private ImageButton themeToggle;
    private View settingsOverlay;
    private int selectedDay = 0;
    private int viewingWeek = 0; // 0=跟随真实当前周；非 0=预览该周
    private TextView weekRangeText;
    private ImageButton weekPrevButton;
    private ImageButton weekNextButton;
    private TextView weekLiveBadge;
    private LinearLayout weekNavRow;
    private AlertDialog weekPickerDialog;
    private HorizontalScrollView dayScroll;
    private int lastAutoScrolledDay = -1;
    private boolean storageLocked = false;
    private boolean isDarkMode = false;
    private boolean activityResumed = false;
    private Runnable pendingReminderPopup;
    private final DeferredResult<Runnable> pdfImportDelivery = new DeferredResult<>();
    private Thread pdfImportThread;
    private Uri pendingImportUri;
    private static final String STATE_IMPORT_URI = "state_import_uri";
    private String renderedTemporalSignature = "";
    private boolean animateDaySlider = false;
    private float daySwipeDownX = 0f;
    private float daySwipeDownY = 0f;
    private boolean daySwipeTracking = false;
    private int daySwipeAxis = 0; // 0=undecided, 1=horizontal, 2=vertical
    private int daySwipePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean daySwipeChildCancelled = false;
    private int themeMode = 1; // 1=浅色, 2=深色
    private static final String STATE_SELECTED_DAY = "state_selected_day";
    private static final String STATE_VIEWING_WEEK = "state_viewing_week";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppFonts.withSize(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dayNames = getResources().getStringArray(R.array.main_days_short);
        courseColorNames = CoursePalette.appendNames(getResources().getStringArray(R.array.main_course_color_names));
        applyAppNightMode();
        super.onCreate(savedInstanceState);
        loadThemeMode();
        loadPeriodTimes();
        configureWindow();
        selectedDay = currentSchoolDay();
        loadCourses();
        loadPersonalEvents();
        // Migrate old per-entry colors to one shared color per course name, transactionally.
        List<Course> normalizedColors = copyCourses();
        if (CourseColorRules.synchronize(normalizedColors)) saveCourses(normalizedColors);
        // Keep historical courses: week navigation filters them without deleting data.
        addTemporaryDynamicIslandCourseIfDebug();
        if (savedInstanceState != null) {
            // 主题切换 recreate() / 进程重建后：保留用户当前查看的星期与周次，
            // 避免被 currentSchoolDay() 重置（周六打开色调却跳去周六的问题）
            selectedDay = clamp(
                    savedInstanceState.getInt(STATE_SELECTED_DAY, selectedDay),
                    0,
                    dayNames.length - 1
            );
            viewingWeek = clamp(
                    savedInstanceState.getInt(STATE_VIEWING_WEEK, 0),
                    0,
                    Math.max(0, totalCalendarWeeks())
            );
        }
        if (savedInstanceState != null) {
            pastelBrowseDate = savedInstanceState.getLong("agenda_browse_date", 0L);
            pendingCourseRestore = savedInstanceState.getBundle("course_editor");
            activeCourseId = savedInstanceState.getLong("course_editor_id", 0L);
            activeEventDetailId = savedInstanceState.getLong("event_detail_id", 0L);
            pendingDetailRestore = savedInstanceState.getBundle("event_detail");
            pendingEventRestore = savedInstanceState.getBundle("event_editor");
            activeEventId = savedInstanceState.getLong("event_editor_id", 0L);
            eventFromMine = savedInstanceState.getBoolean("event_from_mine", false);
            pendingSettingsRestore = savedInstanceState.getBundle("settings_draft");
        }
        buildLayout();
        AppFonts.install(getWindow().getDecorView());
        if (pastelTimetable != null && savedInstanceState != null) {
            pastelTimetable.restoreUiState(savedInstanceState.getBoolean("agenda_tab", false),
                    savedInstanceState.getInt("agenda_scroll", 0));
            if (savedInstanceState.getBoolean("mine_visible", false)) pastelTimetable.setMineSelected(true);
        }
        if (pastelTimetable != null) {
            getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() {
                    // 「我的」 is a tab: Back returns to 课表 before leaving the app.
                    if (pastelTimetable != null && pastelTimetable.isMine()) {
                        eventFromMine = false;
                        pastelTimetable.setMineSelected(false);
                        return;
                    }
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            });
        }
        render();
        if (pendingCourseRestore != null) {
            uiHandler.post(() -> {
                Course course = activeCourseId == 0 ? null : findCourseById(activeCourseId);
                if (activeCourseId != 0 && course == null) {
                    pendingCourseRestore = null;
                    Toast.makeText(this, getString(R.string.main_course_gone), Toast.LENGTH_LONG).show();
                } else {
                    showPastelCourseEditor(course, selectedDay, 1);
                }
            });
        } else if (pendingEventRestore != null) {
            uiHandler.post(() -> {
                PersonalEvent event = activeEventId == 0 ? null : findPersonalEvent(activeEventId);
                if (activeEventId != 0 && event == null) {
                    pendingEventRestore = null;
                    activeEventId = 0;
                    Toast.makeText(this, getString(R.string.main_event_gone), Toast.LENGTH_LONG).show();
                } else {
                    showPersonalEventEditor(event);
                }
            });
        } else if (activeEventDetailId != 0) {
            uiHandler.post(() -> {
                PersonalEvent event = findPersonalEvent(activeEventDetailId);
                if (event != null) showPersonalEventDetail(event);
                else {
                    activeEventDetailId = 0;
                    pendingDetailRestore = null;
                    if (eventFromMine) showMineSheet();
                }
            });
        }
        if (pendingSettingsRestore != null) {
            uiHandler.post(() -> {
                if (pendingSettingsRestore == null) return;
                String kind = pendingSettingsRestore.getString("kind", "");
                if ("font".equals(kind)) showFontDialog();
                else if ("periods".equals(kind)) showPeriodSettingsDialog();
                else if ("classic_course".equals(kind)) {
                    long id = pendingSettingsRestore.getLong("id", 0L);
                    Course editing = id == 0 ? null : findCourseById(id);
                    if (id == 0 || editing != null) showCourseDialog(editing);
                    else {
                        pendingSettingsRestore = null;
                        Toast.makeText(this, getString(R.string.main_course_gone), Toast.LENGTH_LONG).show();
                    }
                }
                else pendingSettingsRestore = null;
            });
        }
        maybeShowReminderPopupFromIntent(getIntent());
        ensureNotificationPermission();
        maybeSendDebugReminderTestNotification(getIntent());
        scheduleClassReminderNotifications();
        // Calendar setup belongs to a successful timetable import, not app launch.
        if (savedInstanceState != null) {
            String importUri = savedInstanceState.getString(STATE_IMPORT_URI);
            if (importUri != null) {
                importPdf(Uri.parse(importUri));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (settingsDraftCapture != null) outState.putBundle("settings_draft", settingsDraftCapture.capture());
        else if (pendingSettingsRestore != null) outState.putBundle("settings_draft", pendingSettingsRestore);
        outState.putLong("agenda_browse_date", pastelBrowseDate);
        outState.putBoolean("mine_visible", pastelTimetable != null && pastelTimetable.isMine());
        outState.putBoolean("event_from_mine", eventFromMine);
        if (pastelTimetable != null) {
            outState.putBoolean("agenda_tab", pastelTimetable.isAgenda());
            outState.putInt("agenda_scroll", pastelTimetable.scrollPosition());
        }
        if (activeEventEditor != null) {
            Bundle draft = activeEventEditor.captureEventEditorState();
            if (draft != null) {
                outState.putBundle("event_editor", draft);
                outState.putLong("event_editor_id", activeEventId);
            }
        }
        if (activeCourseEditor != null) {
            Bundle draft = activeCourseEditor.captureCourseEditorState();
            if (draft != null) {
                outState.putBundle("course_editor", draft);
                outState.putLong("course_editor_id", activeCourseId);
            }
        }
        if (activeEventDetail != null && activeEventDetailId != 0) {
            outState.putLong("event_detail_id", activeEventDetailId);
            outState.putBundle("event_detail", activeEventDetail.captureEventDetailState());
        }
        outState.putInt(STATE_SELECTED_DAY, selectedDay);
        outState.putInt(STATE_VIEWING_WEEK, viewingWeek);
        if (pendingImportUri != null) {
            outState.putString(STATE_IMPORT_URI, pendingImportUri.toString());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybeShowReminderPopupFromIntent(intent);
        maybeSendDebugReminderTestNotification(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        pdfImportDelivery.setResumed(true);
        deliverPendingPdfResult();
        countdownSnapshots.clear();
        render();
        scheduleMinuteTick();
        scheduleClassReminderNotifications();
        if (pendingReminderPopup != null) {
            Runnable popup = pendingReminderPopup;
            pendingReminderPopup = null;
            uiHandler.post(popup);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        pdfImportDelivery.setResumed(false);
        minuteHandler.removeCallbacks(minuteTick);
        cancelCourseCardAnimations(courseList);
        pauseTimedViews(courseList);
        resetCourseListAnimation();
        if (daySelectionSlider != null) {
            daySelectionSlider.animate().cancel();
            daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pdfImportDelivery.destroy();
        cancelPdfImport();
        pendingReminderPopup = null;
        uiHandler.removeCallbacksAndMessages(null);
        minuteHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void pauseTimedViews(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        if (view instanceof CountdownRow) {
            ((CountdownRow) view).pauseTicks();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                pauseTimedViews(group.getChildAt(i));
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            daySwipeDownX = event.getX();
            daySwipeDownY = event.getY();
            daySwipeTracking = isDaySwipeStartAllowed(event);
            daySwipeAxis = 0;
            daySwipePointerId = event.getPointerId(0);
            daySwipeChildCancelled = false;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            daySwipeTracking = false;
            daySwipeAxis = 2;
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            int pointerIndex = event.getActionIndex();
            if (pointerIndex >= 0 && event.getPointerId(pointerIndex) == daySwipePointerId) {
                daySwipeTracking = false;
                daySwipeAxis = 2;
            }
        } else if (action == MotionEvent.ACTION_MOVE && daySwipeTracking) {
            int pointerIndex = event.findPointerIndex(daySwipePointerId);
            if (pointerIndex < 0) {
                daySwipeTracking = false;
            } else {
                float distanceX = event.getX(pointerIndex) - daySwipeDownX;
                float distanceY = event.getY(pointerIndex) - daySwipeDownY;
                float absX = Math.abs(distanceX);
                float absY = Math.abs(distanceY);
                if (daySwipeAxis == 0 && Math.max(absX, absY) >= dp(14)) {
                    if (absX >= absY * 1.35f && canSwitchDayBySwipe(distanceX)) {
                        daySwipeAxis = 1;
                        cancelChildTouch(event);
                    } else if (absY > absX) {
                        daySwipeAxis = 2;
                    }
                }
                if (daySwipeAxis == 1) {
                    return true;
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            float distanceX = event.getX() - daySwipeDownX;
            float distanceY = event.getY() - daySwipeDownY;
            if (daySwipeTracking
                    && daySwipeAxis != 2
                    && isDaySwipeGesture(distanceX, distanceY)
                    && canSwitchDayBySwipe(distanceX)) {
                cancelChildTouch(event);
                handleDaySwipe(distanceX, distanceY);
                daySwipeTracking = false;
                return true;
            }
            if (daySwipeAxis == 1) {
                daySwipeTracking = false;
                return true;
            }
            daySwipeTracking = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            daySwipeTracking = false;
            daySwipeAxis = 0;
            daySwipePointerId = MotionEvent.INVALID_POINTER_ID;
            daySwipeChildCancelled = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private void cancelChildTouch(MotionEvent source) {
        if (daySwipeChildCancelled) {
            return;
        }
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        daySwipeChildCancelled = true;
    }

    private boolean isDaySwipeStartAllowed(MotionEvent event) {
        if (settingsOverlay != null && settingsOverlay.getParent() != null) {
            return false;
        }
        return !isTouchInsideDayTabs(event) && !isTouchInsideWeekNav(event);
    }

    private boolean isTouchInsideDayTabs(MotionEvent event) {
        if (dayTabFrame == null || dayTabFrame.getWidth() <= 0 || dayTabFrame.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        dayTabFrame.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0]
                && x <= location[0] + dayTabFrame.getWidth()
                && y >= location[1]
                && y <= location[1] + dayTabFrame.getHeight();
    }

    private boolean isTouchInsideWeekNav(MotionEvent event) {
        if (weekNavRow == null || weekNavRow.getWidth() <= 0 || weekNavRow.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        weekNavRow.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0]
                && x <= location[0] + weekNavRow.getWidth()
                && y >= location[1]
                && y <= location[1] + weekNavRow.getHeight();
    }

    private boolean handleDaySwipe(float distanceX, float distanceY) {
        if (!isDaySwipeGesture(distanceX, distanceY)) {
            return false;
        }
        int direction = distanceX < 0 ? 1 : -1;
        if (pastelTimetable != null) {
            if (!pastelTimetable.canSwipeTab(direction)) return false;
            // Leaving 「我的」 by swipe ends its navigation context, like tapping another tab.
            if (pastelTimetable.isMine()) eventFromMine = false;
            pastelTimetable.swipeTab(direction);
            return true;
        }
        int targetDay = selectedDay + direction;
        if (targetDay < 0 || targetDay >= dayNames.length) {
            return false;
        }
        switchToDay(targetDay, direction);
        return true;
    }

    private boolean canSwitchDayBySwipe(float distanceX) {
        int direction = distanceX < 0 ? 1 : -1;
        if (pastelTimetable != null) return pastelTimetable.canSwipeTab(direction);
        int targetDay = selectedDay + direction;
        return targetDay >= 0 && targetDay < dayNames.length;
    }

    private boolean isDaySwipeGesture(float distanceX, float distanceY) {
        float absX = Math.abs(distanceX);
        float absY = Math.abs(distanceY);
        if (absX < dp(DAY_SWIPE_MIN_DISTANCE_DP) || absX < absY * 1.35f) {
            return false;
        }
        return true;
    }

    private int normalizeDayIndex(int day) {
        int length = dayNames.length;
        return ((day % length) + length) % length;
    }

    private void applyAppNightMode() {
        int savedMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(THEME_KEY, 0);
        int nightMode;
        if (savedMode == 2) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (savedMode == 1) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private void loadThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedMode = prefs.getInt(THEME_KEY, 0);
        if (savedMode == 1 || savedMode == 2) {
            themeMode = savedMode;
        } else {
            themeMode = isSystemDarkMode() ? 2 : 1;
            prefs.edit().putInt(THEME_KEY, themeMode).apply();
        }
        applyThemeMode();
    }

    private boolean isSystemDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyThemeMode() {
        switch (themeMode) {
            case 1: // 强制浅色
                isDarkMode = false;
                break;
            case 2: // 强制深色
                isDarkMode = true;
                break;
            default: // 跟随系统
                isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                break;
        }
    }

    private void toggleTheme() {
        themeMode = isDarkMode ? 1 : 2;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(THEME_KEY, themeMode).apply();
        recreate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importPdf(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            scheduleClassReminderNotifications();
        } else {
            Toast.makeText(this, getString(R.string.main_notification_permission_denied), Toast.LENGTH_LONG).show();
        }
    }

    private void ensureNotificationPermission() {
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            ClassReminderReceiver.ensureChannel(this, manager);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void scheduleClassReminderNotifications() {
        if (storageLocked || !notificationsEnabled()) {
            return;
        }
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            cancelScheduledClassReminders(alarmManager);
        }
        long now = System.currentTimeMillis();
        List<ReminderScheduler.Entry> entries = new ArrayList<>();
        Set<Integer> usedRequestCodes = new HashSet<>();
        for (Course course : courses) {
            if (course.temporary || course.name == null || course.name.trim().isEmpty()) {
                continue;
            }
            long[] triggerTimes = reminderTriggerTimes(course, now);
            if (triggerTimes.length == 0) {
                continue;
            }
            int requestCode = reminderRequestCode(course);
            while (usedRequestCodes.contains(requestCode)) {
                requestCode = requestCode == Integer.MAX_VALUE ? 1 : requestCode + 1;
            }
            usedRequestCodes.add(requestCode);
            entries.add(new ReminderScheduler.Entry(
                    requestCode,
                    course.name,
                    periodRangeTime(course),
                    course.room,
                    reminderLeadMinutes(course),
                    triggerTimes
            ));
        }
        for (PersonalEvent event : personalEvents) {
            long triggerAt = event.triggerAtMillis();
            if (triggerAt <= now) {
                continue;
            }
            int requestCode = eventRequestCode(event);
            while (usedRequestCodes.contains(requestCode)) {
                requestCode = requestCode == Integer.MAX_VALUE ? 1 : requestCode + 1;
            }
            usedRequestCodes.add(requestCode);
            entries.add(new ReminderScheduler.Entry(
                    requestCode,
                    event.title,
                    event.timeLabel(),
                    event.location,
                    event.remindMinutes,
                    new long[]{triggerAt},
                    ReminderScheduler.KIND_EVENT
            ));
        }
        ReminderScheduler.replaceSchedule(this, entries);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(REMINDER_CODES_KEY).apply();
    }

    /** Different mixing from course codes, so an event and a course created in the same millisecond differ. */
    private static int eventRequestCode(PersonalEvent event) {
        int code = (int) ((event.id * 0x9E3779B97F4A7C15L) >>> 33);
        return code == 0 ? 1 : code;
    }

    private void cancelScheduledClassReminders(android.app.AlarmManager alarmManager) {
        cancelScheduledClassReminders(alarmManager,
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(REMINDER_CODES_KEY, "[]"));
    }

    private void cancelScheduledClassReminders(android.app.AlarmManager alarmManager, String encoded) {
        if (encoded == null) {
            return;
        }
        try {
            JSONArray codes = new JSONArray(encoded);
            for (int i = 0; i < codes.length(); i++) {
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                        this,
                        codes.optInt(i),
                        new Intent(this, ClassReminderReceiver.class),
                        android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        } catch (Exception ignored) {
            // Ignore corrupted reminder metadata; the next save will replace it.
        }
    }

    private boolean notificationsEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(NOTIFICATIONS_ENABLED_KEY, true);
    }

    private void applyNotificationToggle(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(NOTIFICATIONS_ENABLED_KEY, enabled)
                .apply();
        if (enabled) {
            ensureNotificationPermission();
            scheduleClassReminderNotifications();
        } else {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                cancelScheduledClassReminders(alarmManager);
            }
            ReminderScheduler.clear(this);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(REMINDER_CODES_KEY).apply();
        }
    }

    private Intent classReminderIntent(Course course) {
        Intent intent = new Intent(this, ClassReminderReceiver.class);
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, course.name);
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_TIME, periodRangeTime(course));
        intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_ROOM, course.room);
        intent.putExtra(ClassReminderReceiver.EXTRA_REMINDER_MINUTES, reminderLeadMinutes(course));
        return intent;
    }

    private int reminderLeadMinutes(Course course) {
        int period = course == null ? 0 : course.period;
        return period == 1 || period == 5 || period == 9
                ? LONG_CLASS_REMINDER_MINUTES
                : SHORT_CLASS_REMINDER_MINUTES;
    }

    private long[] reminderTriggerTimes(Course course, long now) {
        return TimetableRules.reminderTriggerTimes(
                calendarStartMillis(),
                calendarEndMillis(),
                calendarTotalWeeks(),
                course.day,
                courseStartMinutes(course),
                reminderLeadMinutes(course),
                parseWeekSet(course.weeks),
                now
        );
    }

    private int reminderRequestCode(Course course) {
        if (course == null) {
            return 1;
        }
        long id = course.id;
        if (id != 0L) {
            return (int) (id ^ (id >>> 32)) & 0x7fffffff;
        }
        // id 为 0（临时/测试课程等未分配 id 的课程）：组合「日 + 节次 + 跨节数 + 周次 + 名称」，
        // 避免同名课程（不同天/不同节次）碰撞出相同 requestCode 而互相覆盖。
        int hash = 17;
        hash = 31 * hash + course.day;
        hash = 31 * hash + course.period;
        hash = 31 * hash + Math.max(0, course.endPeriod - course.period);
        hash = 31 * hash + (course.weeks == null ? 0 : course.weeks.hashCode());
        hash = 31 * hash + (course.name == null ? 0 : course.name.hashCode());
        int result = hash & 0x7fffffff;
        return result == 0 ? 1 : result;
    }

    private void configureWindow() {
        Window window = getWindow();
        int bgColor = bgColor();
        window.setStatusBarColor(bgColor);
        window.setNavigationBarColor(bgColor);
        if (isDarkMode) {
            window.getDecorView().setSystemUiVisibility(0);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildLayout() {
        if (BuildConfig.PASTEL_UI) {
            pastelTimetable = new PastelTimetableView(this, isDarkMode, pastelAccent(), new PastelTimetableView.Actions() {
                @Override public void onImport() { showImportChooser(); }
                @Override public void onSettings() { showSettingsDialogV2(); }
                @Override public void onToggleTheme() { toggleTheme(); }
                @Override public void onAdd(int day, int period) {
                    showPastelCourseEditor(null, clamp(day, 0, 6), clamp(period, 1, periodCount()));
                }
                @Override public void onCourse(long id) {
                    Course course = findCourseById(id);
                    if (course != null) showPastelCourseDetail(course);
                }
                @Override public void onEditCourse(long id) {
                    Course course = findCourseById(id);
                    if (course != null) showPastelCourseEditor(course, course.day, course.period);
                }
                @Override public void onPreviousWeek() { stepViewingWeek(-1); }
                @Override public void onNextWeek() { stepViewingWeek(1); }
                @Override public void onPickWeek() { showPastelDatePicker(); }
                @Override public void onCurrentWeek() { returnToCurrentWeek(); }
                @Override public void onCountdownFinished() { render(); }
                @Override public void onAddEvent() { eventFromMine = false; showPersonalEventEditor(null); }
                @Override public void onEvent(long id) {
                    PersonalEvent event = findPersonalEvent(id);
                    if (event != null) { eventFromMine = false; showPersonalEventDetail(event); }
                }
                @Override public void onMine() { showMineSheet(); }
            });
            minePage = new PastelCourseSheet(this, isDarkMode, pastelAccent()).minePage(
                    () -> { eventFromMine = true; showPersonalEventEditor(null); },
                    id -> {
                        PersonalEvent event = findPersonalEvent(id);
                        if (event != null) {
                            eventFromMine = true;
                            showPersonalEventDetail(event);
                        }
                    },
                    this::showSettingsDialogV2);
            pastelTimetable.setMinePage(minePage.view);
            setContentView(pastelTimetable);
            return;
        }
        boolean compact = isCompactWidth();
        int sideMargin = contentSideMarginDp();
        int actionButtonSize = 48;
        FrameLayout safeFrame = new FrameLayout(this);
        safeFrame.setBackgroundColor(bgColor());
        safeFrame.setClipChildren(true);
        safeFrame.setClipToPadding(true);
        safeFrame.setOnApplyWindowInsetsListener((view, insets) -> {
            WindowInsets applied = insets;
            int horizontalPadding = dp(compact ? 12 : 20);
            view.setPadding(horizontalPadding, applied.getSystemWindowInsetTop() + dp(16), horizontalPadding, applied.getSystemWindowInsetBottom() + dp(16));
            return insets;
        });

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setClipChildren(true);
        pageScroll.setClipToPadding(true);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        safeFrame.addView(pageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        pageScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        header.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(dp(sideMargin), 0, dp(sideMargin), 0);
        root.addView(header, headerParams);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleBlock, titleBlockParams);

        TextView title = new TextView(this);
        title.setText(getString(R.string.main_title));
        title.setTextColor(primaryTextColor());
        title.setTextSize(compact ? 28 : 32);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setLetterSpacing(0f);
        titleBlock.addView(title);

        summaryText = new TextView(this);
        summaryText.setTextColor(secondaryTextColor());
        summaryText.setTextSize(15);
        summaryText.setTypeface(appTypeface(Typeface.NORMAL));
        summaryText.setAlpha(0.85f);
        summaryText.setSingleLine(true);
        summaryText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                compact ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMargins(0, dp(4), 0, 0);
        titleBlock.addView(summaryText, summaryParams);

        statusChip = new TextView(this);
        statusChip.setTextSize(13);
        statusChip.setTypeface(appTypeface(Typeface.BOLD));
        statusChip.setSingleLine(true);
        statusChip.setEllipsize(TextUtils.TruncateAt.END);
        statusChip.setIncludeFontPadding(false);
        statusChip.setGravity(Gravity.CENTER);
        statusChip.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        chipParams.setMargins(0, dp(8), 0, 0);
        titleBlock.addView(statusChip, chipParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(compact ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = compact
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (compact) {
            actionsParams.setMargins(0, dp(12), 0, 0);
        }
        header.addView(actions, actionsParams);

        // 主题切换按钮
        themeToggle = new ImageButton(this);
        themeToggle.setImageResource(isDarkMode ? R.drawable.ic_sun_outline : R.drawable.ic_moon_outline);
        themeToggle.setImageTintList(ColorStateList.valueOf(accentColor()));
        themeToggle.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        themeToggle.setContentDescription(isDarkMode ? getString(R.string.main_switch_to_light) : getString(R.string.main_switch_to_dark));
        themeToggle.setBackground(interactiveSurfaceBackground(cardColor(), dp(22)));
        themeToggle.setPadding(dp(10), dp(10), dp(10), dp(10));
        themeToggle.setOnClickListener(view -> toggleTheme());
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize));
        themeParams.setMargins(0, 0, dp(10), 0);
        actions.addView(themeToggle, themeParams);

        ImageButton settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings_outline);
        settingsButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        settingsButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        settingsButton.setContentDescription(getString(R.string.main_settings));
        settingsButton.setBackground(interactiveSurfaceBackground(cardColor(), dp(22)));
        settingsButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        settingsButton.setOnClickListener(view -> showSettingsDialogV2());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize));
        settingsParams.setMargins(0, 0, dp(10), 0);
        actions.addView(settingsButton, settingsParams);

        ImageButton addButton = new ImageButton(this);
        addButton.setImageResource(R.drawable.ic_plus);
        addButton.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        addButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        addButton.setContentDescription(getString(R.string.main_add_course));
        addButton.setBackground(interactiveButtonBackground(accentColor(), dp(22)));
        addButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        addButton.setOnClickListener(view -> showCourseDialog(null));
        actions.addView(addButton, new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize)));

        // 周切换导航条：‹ 第N周·日期范围 › [回到本周]
        weekNavRow = new LinearLayout(this);
        weekNavRow.setOrientation(LinearLayout.HORIZONTAL);
        weekNavRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams weekNavParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        weekNavParams.setMargins(dp(sideMargin), dp(compact ? 14 : 18), dp(sideMargin), dp(6));
        root.addView(weekNavRow, weekNavParams);

        weekPrevButton = new ImageButton(this);
        weekPrevButton.setImageResource(R.drawable.ic_chevron_right);
        weekPrevButton.setRotation(180f);
        weekPrevButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        weekPrevButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        weekPrevButton.setContentDescription(getString(R.string.main_prev_week));
        weekPrevButton.setBackground(interactiveSurfaceBackground(cardColor(), dp(12)));
        weekPrevButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        weekPrevButton.setOnClickListener(view -> stepViewingWeek(-1));
        LinearLayout.LayoutParams weekPrevParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        weekPrevParams.setMargins(0, 0, dp(4), 0);
        weekNavRow.addView(weekPrevButton, weekPrevParams);

        weekRangeText = new TextView(this);
        weekRangeText.setTextSize(compact ? 13 : 14);
        weekRangeText.setTypeface(appTypeface(Typeface.BOLD));
        weekRangeText.setTextColor(primaryTextColor());
        weekRangeText.setGravity(Gravity.CENTER);
        weekRangeText.setSingleLine(true);
        weekRangeText.setEllipsize(TextUtils.TruncateAt.END);
        weekRangeText.setIncludeFontPadding(false);
        weekRangeText.setPadding(dp(8), dp(9), dp(8), dp(9));
        weekRangeText.setBackground(interactiveSurfaceBackground(segmentedTrackColor(), dp(14)));
        weekRangeText.setContentDescription(getString(R.string.main_choose_week));
        weekRangeText.setOnClickListener(view -> showWeekPickerDialog());
        weekNavRow.addView(weekRangeText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        weekNextButton = new ImageButton(this);
        weekNextButton.setImageResource(R.drawable.ic_chevron_right);
        weekNextButton.setImageTintList(ColorStateList.valueOf(accentColor()));
        weekNextButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        weekNextButton.setContentDescription(getString(R.string.main_next_week));
        weekNextButton.setBackground(interactiveSurfaceBackground(cardColor(), dp(12)));
        weekNextButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        weekNextButton.setOnClickListener(view -> stepViewingWeek(1));
        LinearLayout.LayoutParams weekNextParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        weekNextParams.setMargins(dp(4), 0, dp(4), 0);
        weekNavRow.addView(weekNextButton, weekNextParams);

        weekLiveBadge = new TextView(this);
        weekLiveBadge.setText(getString(R.string.main_this_week));
        weekLiveBadge.setTextSize(13);
        weekLiveBadge.setTypeface(appTypeface(Typeface.BOLD));
        weekLiveBadge.setTextColor(accentColor());
        weekLiveBadge.setGravity(Gravity.CENTER);
        weekLiveBadge.setIncludeFontPadding(false);
        weekLiveBadge.setContentDescription(getString(R.string.main_back_to_this_week));
        weekLiveBadge.setPadding(dp(11), dp(9), dp(11), dp(9));
        weekLiveBadge.setBackground(interactiveTranslucentBackground(
                withAlpha(accentColor(), isDarkMode ? 64 : 34), dp(14)));
        weekLiveBadge.setOnClickListener(view -> returnToCurrentWeek());
        weekLiveBadge.setVisibility(View.GONE);
        weekNavRow.addView(weekLiveBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        this.dayScroll = dayScroll;
        dayScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams dayScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dayScrollParams.setMargins(dp(sideMargin), dp(compact ? 8 : 10), dp(sideMargin), dp(16));
        root.addView(dayScroll, dayScrollParams);

        dayTabFrame = new FrameLayout(this);
        dayTabFrame.setPadding(dp(4), dp(4), dp(4), dp(4));
        dayTabFrame.setClipChildren(false);
        dayTabFrame.setClipToPadding(false);
        dayTabFrame.setBackground(elevatedCardBackground(segmentedTrackColor(), dp(24)));
        dayScroll.addView(dayTabFrame);

        daySelectionSlider = new View(this);
        daySelectionSlider.setBackground(elevatedButtonBackground(todayColor(), dp(20)));
        daySelectionSlider.setClickable(false);
        dayTabFrame.addView(daySelectionSlider, new FrameLayout.LayoutParams(dp(72), dp(compact ? 48 : 52)));

        dayTabs = new LinearLayout(this);
        dayTabs.setOrientation(LinearLayout.HORIZONTAL);
        dayTabFrame.addView(dayTabs);

        courseList = new LinearLayout(this);
        courseList.setOrientation(LinearLayout.VERTICAL);
        courseList.setClipChildren(false);
        courseList.setClipToPadding(false);
        courseList.setPadding(0, dp(12), 0, dp(18));
        root.addView(courseList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(safeFrame);
    }

    private PastelTimetableView.Entry pastelEntry(Course course) {
        if (course == null) return null;
        PastelTimetableView.Entry entry = new PastelTimetableView.Entry();
        entry.id = course.id;
        entry.day = course.day;
        entry.start = course.period;
        entry.end = Math.max(course.period, course.endPeriod);
        entry.name = course.name;
        entry.room = course.room;
        entry.teacherLabel = CourseDisplayRules.teacherLabel(
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(SHOW_TEACHER_KEY, false),
                course.teacher);
        entry.color = course.color;
        entry.time = periodRangeTime(course);
        entry.period = course.periodLabel();
        return entry;
    }

    private void renderPastelTimetable() {
        long start = pastelDisplayedStart();
        int week = AgendaDates.week(calendarStartMillis(), start);
        boolean outside = week < 1 || week > calendarTotalWeeks();
        boolean preview = outside || start != AgendaDates.weekStart(calendarStartMillis(), System.currentTimeMillis());
        List<PastelTimetableView.Entry> entries = new ArrayList<>();
        for (Course course : courses) {
            if (!outside && !course.temporary && courseOccursInTeachingWeek(course, week)) {
                entries.add(pastelEntry(course));
            }
        }
        Course featured = !preview ? currentCourseInProgress() : null;
        String featureStatus = featured != null ? getString(R.string.main_feature_now) : getString(R.string.main_feature_plan);
        long countdownTarget = featured != null
                ? timeOnDateMillis(startOfTodayMillis(), courseEndMinutes(featured)) : -1;
        boolean countdownUntilEnd = featured != null;
        if (featured == null && !preview) {
            SummaryCourse next = nextUpcomingCourseFromToday();
            if (next != null) {
                featured = next.course;
                featureStatus = getString(R.string.main_feature_next_day, dayNames[next.day]);
                countdownTarget = next.startAtMillis;
            }
        }
        if (preview) featureStatus = getString(R.string.main_feature_preview);
        String message = outside ? getString(R.string.main_pastel_msg_outside) : preview ? getString(R.string.main_pastel_msg_preview) : getString(R.string.main_pastel_msg_default);
        // Events first: they also set the weekday date order that bind() uses for finished courses.
        bindPastelEvents(week);
        pastelTimetable.bind(entries, new ArrayList<>(periodTimes),
                (start == AgendaDates.weekStart(calendarStartMillis(), System.currentTimeMillis()) ? getString(R.string.main_this_week_prefix) : "")
                        + pastelRangeLabel(start, week), preview,
                canBrowsePastel(-1), canBrowsePastel(1), pastelEntry(featured), featureStatus,
                message, storageLocked, outside);
        pastelTimetable.setCountdown(preview ? -1 : countdownTarget, countdownUntilEnd);
        renderedTemporalSignature = temporalSignature();
    }

    private void render() {
        if (pastelTimetable != null) {
            renderPastelTimetable();
            return;
        }
        renderDayTabs();
        renderCourseList();
        renderWeekNav();
        int count = 0;
        int filterWeek = viewingWeek != 0 ? viewingWeek : currentTeachingWeek();
        int today = currentSchoolDay();
        for (Course course : courses) {
            if (viewingWeek != 0 && course.temporary) {
                continue;
            }
            if (course.day == selectedDay && courseOccursInTeachingWeek(course, filterWeek)) {
                count++;
            }
        }
        Course currentCourse = viewingWeek == 0 ? currentCourseInProgress() : null;
        SummaryCourse nextCourse = currentCourse == null && viewingWeek == 0
                ? nextUpcomingCourseFromToday() : null;
        String statusText;
        if (currentCourse != null) {
            statusText = getString(R.string.main_status_now, currentCourse.periodLabel(), currentCourse.name);
        } else if (nextCourse != null) {
            String dayPrefix;
            boolean occursToday = startOfDayMillis(nextCourse.startAtMillis) == startOfTodayMillis();
            if (occursToday) {
                dayPrefix = selectedDay == today ? "" : getString(R.string.main_today) + " ";
            } else {
                dayPrefix = dayNames[nextCourse.day] + " ";
            }
            statusText = getString(R.string.main_status_next, dayPrefix,
                    nextCourse.course.periodLabel(), nextCourse.course.name);
        } else {
            statusText = getString(R.string.main_no_upcoming);
        }
        int realWeek = currentTeachingWeek();
        String weekText;
        if (viewingWeek != 0) {
            weekText = getString(R.string.main_week_prefix, viewingWeek);
        } else if (realWeek >= 1 && realWeek <= calendarTotalWeeks()) {
            weekText = getString(R.string.main_week_prefix, realWeek);
        } else if (startOfTodayMillis() < calendarStartMillis()) {
            weekText = getString(R.string.main_not_started_prefix);
        } else {
            weekText = getString(R.string.main_break_prefix);
        }
        summaryText.setText(getResources().getQuantityString(
                R.plurals.main_summary_courses, count, weekText, dayNames[selectedDay], count));
        if (viewingWeek != 0) {
            updateStatusChip(getString(R.string.main_preview_chip), true);
        } else if (!notificationsEnabled() && (currentCourse != null || nextCourse != null)) {
            updateRemindersOffChip(statusText);
        } else {
            updateStatusChip(currentCourse != null || nextCourse != null ? statusText : null, false);
        }
        renderedTemporalSignature = temporalSignature();
    }

    /** Keeps the next-course text but flags that no reminder will fire for it. */
    private void updateRemindersOffChip(String statusText) {
        if (statusChip == null) {
            return;
        }
        statusChip.setText(getString(R.string.status_reminders_off, statusText));
        statusChip.setBackground(buttonBackground(withAlpha(secondaryTextColor(), isDarkMode ? 44 : 22), dp(100)));
        statusChip.setTextColor(secondaryTextColor());
        statusChip.setContentDescription(getString(R.string.main_reminders_off_desc, statusText));
        statusChip.setOnClickListener(view -> showRemindersDisabledDialog(null));
    }

    private void updateStatusChip(String statusText, boolean previewMode) {
        if (statusChip == null) {
            return;
        }
        statusChip.setContentDescription(null);
        if (previewMode) {
            statusChip.setText(statusText);
            statusChip.setBackground(buttonBackground(tonalContainerColor(), dp(100)));
            statusChip.setTextColor(accentColor());
            statusChip.setOnClickListener(view -> returnToCurrentWeek());
            return;
        }
        boolean hasStatus = statusText != null && !statusText.isEmpty();
        statusChip.setText(hasStatus ? statusText : getString(R.string.main_no_upcoming));
        int chipBg;
        int chipFg;
        if (hasStatus) {
            chipBg = accentContainerColor();
            chipFg = accentColor();
        } else {
            chipBg = withAlpha(secondaryTextColor(), isDarkMode ? 44 : 22);
            chipFg = secondaryTextColor();
        }
        statusChip.setBackground(buttonBackground(chipBg, dp(100)));
        statusChip.setTextColor(chipFg);
        statusChip.setOnClickListener(null);
    }

    private int totalCalendarWeeks() {
        return Math.max(1, calendarTotalWeeks());
    }

    private int displayedViewingWeek() {
        if (viewingWeek != 0) {
            return clamp(viewingWeek, 1, totalCalendarWeeks());
        }
        int real = currentTeachingWeek();
        if (real < 1) {
            return 1;
        }
        return Math.min(real, totalCalendarWeeks());
    }

    private long pastelDisplayedStart() {
        return AgendaDates.weekStart(calendarStartMillis(), pastelBrowseDate == 0 ? System.currentTimeMillis() : pastelBrowseDate);
    }

    private boolean canBrowsePastel(int delta) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(AgendaDates.shift(pastelDisplayedStart(), delta * 7));
        return date.get(Calendar.YEAR) >= 2000 && date.get(Calendar.YEAR) <= 2100;
    }

    private String pastelRangeLabel(long start, int week) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy.M.d", Locale.CHINA);
        return (week >= 1 && week <= calendarTotalWeeks() ? getString(R.string.main_week_n, week) : getString(R.string.main_outside_term))
                + "\n" + format.format(new java.util.Date(start)) + "–"
                + new SimpleDateFormat("M.d", Locale.CHINA).format(new java.util.Date(AgendaDates.shift(start, 6)));
    }

    /** Locale of the current resource configuration (follows the in-app language override). */
    @SuppressWarnings("deprecation")
    private Locale uiLocale() {
        return getResources().getConfiguration().locale;
    }

    private void browsePastelDate(long date) {
        long start = AgendaDates.weekStart(calendarStartMillis(), date);
        pastelBrowseDate = start == AgendaDates.weekStart(calendarStartMillis(), System.currentTimeMillis()) ? 0 : date;
        int week = AgendaDates.week(calendarStartMillis(), date);
        viewingWeek = pastelBrowseDate == 0 || week < 1 || week > calendarTotalWeeks() ? 0 : week;
        render();
    }

    private void showPastelDatePicker() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(pastelBrowseDate == 0 ? System.currentTimeMillis() : pastelBrowseDate);
        android.app.DatePickerDialog picker = new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(year, month, day);
            browsePastelDate(chosen.getTimeInMillis());
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        Calendar bound = Calendar.getInstance();
        bound.clear(); bound.set(2000, 0, 1);
        picker.getDatePicker().setMinDate(bound.getTimeInMillis());
        bound.set(2100, 11, 31);
        picker.getDatePicker().setMaxDate(bound.getTimeInMillis());
        picker.setTitle(getString(R.string.main_pick_date_title));
        picker.show();
        AppFonts.install(picker);
    }

    private void stepViewingWeek(int delta) {
        if (pastelTimetable != null) {
            if (canBrowsePastel(delta)) browsePastelDate(AgendaDates.shift(pastelDisplayedStart(), delta * 7));
            return;
        }
        setViewingWeek(displayedViewingWeek() + delta);
    }

    private void setViewingWeek(int week) {
        int total = totalCalendarWeeks();
        if (week < 1 || week > total) {
            return;
        }
        int from = displayedViewingWeek();
        int real = currentTeachingWeek();
        viewingWeek = (real >= 1 && real <= total && real == week) ? 0 : week;
        if (week == from) {
            render();
            return;
        }
        render();
        animateDaySwitch(Integer.compare(week, from));
    }

    private void returnToCurrentWeek() {
        if (pastelTimetable != null) {
            browsePastelDate(System.currentTimeMillis());
            return;
        }
        if (viewingWeek == 0) {
            return;
        }
        int from = displayedViewingWeek();
        viewingWeek = 0;
        pastelBrowseDate = 0;
        render();
        animateDaySwitch(Integer.compare(displayedViewingWeek(), from));
    }

    private String weekRangeLabel(int week) {
        long start = AgendaDates.shift(TimetableRules.weekAnchorMillis(calendarStartMillis()), (week - 1) * 7);
        long end = AgendaDates.shift(start, 6);
        SimpleDateFormat format = new SimpleDateFormat("M.d", Locale.CHINA);
        return getString(R.string.main_week_range, week, format.format(new java.util.Date(start)),
                format.format(new java.util.Date(end)));
    }

    private void renderWeekNav() {
        if (weekRangeText == null) {
            return;
        }
        boolean previewing = viewingWeek != 0;
        int week = displayedViewingWeek();
        int total = totalCalendarWeeks();
        weekRangeText.setText(weekRangeLabel(week));
        weekRangeText.setTextColor(previewing ? accentColor() : primaryTextColor());
        weekRangeText.setBackground(interactiveSurfaceBackground(
                previewing ? tonalContainerColor() : segmentedTrackColor(), dp(14)));
        boolean canPrev = week > 1;
        boolean canNext = week < total;
        weekPrevButton.setEnabled(canPrev);
        weekPrevButton.setAlpha(canPrev ? 1f : 0.35f);
        weekNextButton.setEnabled(canNext);
        weekNextButton.setAlpha(canNext ? 1f : 0.35f);
        weekLiveBadge.setVisibility(previewing ? View.VISIBLE : View.GONE);
    }

    private void showWeekPickerDialog() {
        if (weekPickerDialog != null && weekPickerDialog.isShowing()) {
            return;
        }
        int total = totalCalendarWeeks();
        int selected = displayedViewingWeek();
        int realWeek = currentTeachingWeek();
        boolean realValid = realWeek >= 1 && realWeek <= total;

        ScrollView pickerScroll = new ScrollView(this);
        pickerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.VERTICAL);
        picker.setPadding(dp(12), dp(4), dp(12), dp(8));
        pickerScroll.addView(picker);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final int columns = 5;
        for (int start = 1; start <= total; start += columns) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            int end = Math.min(start + columns, total + 1);
            for (int week = start; week < end; week++) {
                final int targetWeek = week;
                boolean isRealWeek = realValid && week == realWeek;
                String number = String.valueOf(week);
                TextView cell = new TextView(this);
                SpannableString cellText = new SpannableString(
                        isRealWeek ? number + "\n" + getString(R.string.main_today) : number);
                if (isRealWeek) {
                    cellText.setSpan(new RelativeSizeSpan(0.62f), number.length(), cellText.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                cell.setText(cellText);
                cell.setTextSize(15);
                cell.setTypeface(appTypeface(Typeface.BOLD));
                cell.setIncludeFontPadding(false);
                cell.setGravity(Gravity.CENTER);
                cell.setContentDescription(getString(R.string.main_week_n, week) + (isRealWeek ? getString(R.string.main_this_week_suffix) : ""));
                boolean isSelectedCell = week == selected;
                cell.setTextColor(isSelectedCell ? Color.WHITE : primaryTextColor());
                cell.setBackground(isSelectedCell
                        ? interactiveButtonBackground(accentColor(), dp(14))
                        : interactiveTranslucentBackground(tonalContainerColor(), dp(14)));
                cell.setOnClickListener(view -> {
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                    setViewingWeek(targetWeek);
                });
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, dp(46), 1f
                );
                cellParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(cell, cellParams);
            }
            // 末行用占位 View 补齐列数，保持网格对齐
            for (int pad = end; pad < start + columns; pad++) {
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        0, dp(46), 1f
                );
                spacerParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(new View(this), spacerParams);
            }
            picker.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_choose_week))
                .setView(pickerScroll)
                .setNegativeButton(getString(R.string.action_close), null)
                .create();
        dialogRef[0] = dialog;
        weekPickerDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (weekPickerDialog == dialog) {
                weekPickerDialog = null;
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void refreshTimedUi() {
        String currentSignature = temporalSignature();
        if (!TextUtils.equals(renderedTemporalSignature, currentSignature)) {
            render();
            return;
        }
        if (pastelTimetable != null) pastelTimetable.tick();
        invalidateCourseProgressViews(courseList);
    }

    private String temporalSignature() {
        StringBuilder signature = new StringBuilder();
        if (pastelTimetable != null) signature.append(pastelDisplayedStart()).append(':');
        signature.append(displayedViewingWeek())
                .append(':')
                .append(viewingWeek)
                .append(':')
                .append(currentSchoolDay())
                .append(':')
                .append(selectedDay);
        for (Course course : courses) {
            signature.append('|')
                    .append(course.id)
                    .append('=')
                    .append(courseTemporalState(course).ordinal());
        }
        return signature.toString();
    }

    private void invalidateCourseProgressViews(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof CourseProgressView) {
            view.invalidate();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateCourseProgressViews(group.getChildAt(i));
            }
        }
    }

    private void scheduleMinuteTick() {
        minuteHandler.removeCallbacks(minuteTick);
        long delay = PROGRESS_REFRESH_MS - (System.currentTimeMillis() % PROGRESS_REFRESH_MS) + 120L;
        minuteHandler.postDelayed(minuteTick, delay);
    }

    private void addTemporaryDynamicIslandCourseIfDebug() {
        if (!BuildConfig.ENABLE_TEST_COURSE || storageLocked) {
            return;
        }
        for (Course course : courses) {
            if (course.temporary) {
                return;
            }
        }
        int period = currentPeriod();
        if (period < 1) {
            period = nearestDisplayPeriod();
        }
        int start = Math.min(currentMinutes() + 5, 23 * 60 + 58);
        int end = Math.min(start + 45, 23 * 60 + 59);
        Course testCourse = new Course();
        testCourse.id = Long.MIN_VALUE;
        testCourse.day = currentSchoolDay();
        testCourse.period = period;
        testCourse.endPeriod = period;
        testCourse.name = getString(R.string.main_test_course_name);
        testCourse.weeks = getString(R.string.main_test_course_weeks);
        testCourse.room = getString(R.string.main_test_course_room);
        testCourse.teacher = getString(R.string.main_test_course_teacher);
        testCourse.color = Color.rgb(28, 126, 214);
        testCourse.temporary = true;
        testCourse.customStartMinutes = start;
        testCourse.customEndMinutes = end;
        courses.add(testCourse);
        selectedDay = testCourse.day;
    }

    private void renderDayTabs() {
        dayTabs.removeAllViews();
        boolean compact = isCompactWidth();
        int today = currentSchoolDay();
        for (int i = 0; i < dayNames.length; i++) {
            final int day = i;
            LinearLayout tabContainer = new LinearLayout(this);
            tabContainer.setOrientation(LinearLayout.VERTICAL);
            tabContainer.setGravity(Gravity.CENTER);
            tabContainer.setPadding(dp(4), dp(compact ? 7 : 8), dp(4), dp(compact ? 7 : 8));

            // 状态：今天用深蓝色，选中用青绿色
            int textColor;
            boolean isToday = day == today;
            boolean isSelected = day == selectedDay;

            if (isSelected) {
                textColor = accentColor();
            } else if (isToday) {
                textColor = todayColor();
            } else {
                textColor = secondaryTextColor();
            }

            tabContainer.setBackground(buttonBackground(Color.TRANSPARENT, dp(18)));
            tabContainer.setSelected(isSelected);
            tabContainer.setContentDescription(dayNames[i]
                    + (isToday ? getString(R.string.main_today_suffix) : "")
                    + (isSelected ? getString(R.string.main_selected_suffix) : ""));
            tabContainer.setOnClickListener(view -> switchToDay(day));

            TextView dayText = new TextView(this);
            dayText.setText(dayNames[i]);
            dayText.setTextSize(compact ? 15 : 16);
            dayText.setGravity(Gravity.CENTER);
            dayText.setTypeface(appTypeface(Typeface.BOLD));
            dayText.setTextColor(textColor);
            tabContainer.addView(dayText);

            // 今天添加小圆点指示器，颜色跟随文字状态
            View todayMarker = new View(this);
            int markerColor = isSelected ? accentColor() : todayColor();
            todayMarker.setBackground(buttonBackground(isToday ? markerColor : Color.TRANSPARENT, dp(3)));
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(5), dp(5));
            markerParams.setMargins(0, dp(4), 0, 0);
            tabContainer.addView(todayMarker, markerParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(compact ? 62 : 70), dp(compact ? 48 : 52));
            params.setMargins(0, 0, dp(2), 0);
            dayTabs.addView(tabContainer, params);
        }
        boolean shouldAnimateSlider = animateDaySlider;
        animateDaySlider = false;
        dayTabs.post(() -> {
            updateDaySelectionSlider(shouldAnimateSlider);
            if (selectedDay != lastAutoScrolledDay) {
                scrollSelectedDayIntoView();
                lastAutoScrolledDay = selectedDay;
            }
        });
    }

    private void scrollSelectedDayIntoView() {
        if (dayScroll == null || dayTabs == null) {
            return;
        }
        if (selectedDay < 0 || selectedDay >= dayTabs.getChildCount()) {
            return;
        }
        View selectedTab = dayTabs.getChildAt(selectedDay);
        if (selectedTab.getWidth() <= 0) {
            return;
        }
        // 让选中日期居中显示：计算选中标签相对整个标签条的偏移
        int tabLeft = selectedTab.getLeft();
        int tabWidth = selectedTab.getWidth();
        int scrollWidth = dayScroll.getWidth();
        if (scrollWidth <= 0) {
            return;
        }
        int targetScroll = tabLeft - (scrollWidth - tabWidth) / 2;
        dayScroll.smoothScrollTo(Math.max(0, targetScroll), 0);
    }

    private void switchToDay(int day) {
        day = normalizeDayIndex(day);
        if (day == selectedDay) {
            return;
        }
        int direction = daySwitchDirection(day);
        switchToDay(day, direction);
    }

    private void switchToDay(int day, int direction) {
        day = normalizeDayIndex(day);
        if (day == selectedDay) {
            return;
        }
        resetCourseListAnimation();
        animateDaySlider = true;
        selectedDay = day;
        render();
        animateDaySwitch(direction);
    }

    private int daySwitchDirection(int day) {
        return day >= selectedDay ? 1 : -1;
    }

    private void resetCourseListAnimation() {
        if (courseList == null) {
            return;
        }
        courseList.animate().cancel();
        courseList.setAlpha(1f);
        courseList.setTranslationX(0f);
        courseList.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void updateDaySelectionSlider(boolean animate) {
        if (daySelectionSlider == null || dayTabs == null || selectedDay < 0 || selectedDay >= dayTabs.getChildCount()) {
            return;
        }
        View selectedTab = dayTabs.getChildAt(selectedDay);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) daySelectionSlider.getLayoutParams();
        params.width = selectedTab.getWidth();
        params.height = selectedTab.getHeight();
        daySelectionSlider.setLayoutParams(params);
        daySelectionSlider.setBackground(elevatedCardBackground(tonalContainerColor(), dp(20)));

        float target = selectedTab.getLeft();
        daySelectionSlider.animate().cancel();
        if (animate && animationsEnabled()) {
            daySelectionSlider.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            daySelectionSlider.animate()
                    .translationX(target)
                    .setDuration(280L)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                    .withEndAction(() -> daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null))
                    .start();
        } else {
            daySelectionSlider.setTranslationX(target);
            daySelectionSlider.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void animateDaySwitch(int direction) {
        if (courseList == null) return;
        if (!animationsEnabled()) {
            resetCourseListAnimation();
            return;
        }
        float offset = dp(18) * (direction >= 0 ? 1 : -1);
        courseList.animate().cancel();
        courseList.setAlpha(1f);
        courseList.setTranslationX(offset);
        courseList.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        courseList.animate()
                .translationX(0f)
                .setDuration(260L)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    courseList.setAlpha(1f);
                    courseList.setTranslationX(0f);
                    courseList.setLayerType(View.LAYER_TYPE_NONE, null);
                })
                .start();
    }

    private void renderCourseList() {
        cancelCourseCardAnimations(courseList);
        courseList.removeAllViews();
        List<Course> dayCourses = new ArrayList<>();
        if (storageLocked) {
            TextView warning = new TextView(this);
            warning.setText(getString(R.string.main_storage_locked_warning));
            warning.setTextSize(15);
            warning.setTextColor(dangerColor());
            warning.setGravity(Gravity.CENTER);
            warning.setBackground(elevatedCardBackground(dangerContainerColor(), dp(16)));
            warning.setPadding(dp(24), dp(32), dp(24), dp(32));
            warning.setLineSpacing(dp(4), 1.0f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            courseList.addView(warning, params);
            return;
        }

        int currentWeek = viewingWeek != 0 ? viewingWeek : currentTeachingWeek();
        for (Course course : courses) {
            if (viewingWeek != 0 && course.temporary) {
                continue;
            }
            if (course.day == selectedDay && courseOccursInTeachingWeek(course, currentWeek)) {
                dayCourses.add(course);
            }
        }
        Collections.sort(dayCourses, (left, right) -> {
            int result = Integer.compare(left.temporary ? 0 : 1, right.temporary ? 0 : 1);
            if (result == 0) result = Integer.compare(left.period, right.period);
            if (result == 0) result = Integer.compare(left.endPeriod, right.endPeriod);
            if (result == 0) result = left.name.compareTo(right.name);
            return result;
        });

        if (dayCourses.isEmpty()) {
            MaterialCardView emptyState = new MaterialCardView(this);
            emptyState.setCardBackgroundColor(cardColor());
            emptyState.setCardElevation(dp(1));
            emptyState.setRadius(dp(24));
            emptyState.setStrokeColor(borderColor());
            emptyState.setStrokeWidth(dp(1));
            emptyState.setContentPadding(dp(24), dp(48), dp(24), dp(48));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            emptyState.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout emptyIconSurface = new FrameLayout(this);
            emptyIconSurface.setBackground(buttonBackground(accentContainerColor(), dp(36)));
            content.addView(emptyIconSurface, new LinearLayout.LayoutParams(dp(72), dp(72)));

            ImageView emptyIcon = new ImageView(this);
            emptyIcon.setImageResource(R.drawable.ic_calendar_outline);
            emptyIcon.setImageTintList(ColorStateList.valueOf(accentColor()));
            emptyIcon.setScaleType(ImageView.ScaleType.CENTER);
            emptyIcon.setContentDescription(getString(R.string.main_no_courses));
            emptyIconSurface.addView(emptyIcon, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText(getString(R.string.main_no_courses));
            emptyTitle.setTextSize(18);
            emptyTitle.setTypeface(appTypeface(Typeface.BOLD));
            emptyTitle.setTextColor(primaryTextColor());
            emptyTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, dp(20), 0, 0);
            content.addView(emptyTitle, titleParams);

            TextView emptyDate = new TextView(this);
            emptyDate.setText(selectedDayDateLabel());
            emptyDate.setTextSize(14);
            emptyDate.setTypeface(appTypeface(Typeface.NORMAL));
            emptyDate.setTextColor(secondaryTextColor());
            emptyDate.setGravity(Gravity.CENTER);
            emptyDate.setSingleLine(true);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dateParams.setMargins(0, dp(6), 0, dp(24));
            content.addView(emptyDate, dateParams);

            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);

            TextView addCta = new TextView(this);
            addCta.setText(getString(R.string.main_add_course_cta));
            addCta.setTextSize(15);
            addCta.setTypeface(appTypeface(Typeface.BOLD));
            addCta.setTextColor(Color.WHITE);
            addCta.setGravity(Gravity.CENTER);
            addCta.setBackground(interactiveButtonBackground(accentColor(), dp(14)));
            addCta.setPadding(dp(22), dp(12), dp(22), dp(12));
            addCta.setOnClickListener(v -> showCourseDialog(null));

            TextView importCta = new TextView(this);
            importCta.setText(getString(R.string.import_pdf_cta));
            importCta.setTextSize(15);
            importCta.setTypeface(appTypeface(Typeface.BOLD));
            importCta.setTextColor(accentColor());
            importCta.setGravity(Gravity.CENTER);
            importCta.setBackground(interactiveTranslucentBackground(
                    accentContainerColor(), dp(14)));
            importCta.setPadding(dp(22), dp(12), dp(22), dp(12));
            importCta.setOnClickListener(v -> openPdfPicker());

            LinearLayout.LayoutParams addCtaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            addCtaParams.setMargins(0, 0, dp(10), 0);
            buttonRow.addView(addCta, addCtaParams);
            buttonRow.addView(importCta);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, dp(20), 0, 0);
            content.addView(buttonRow, rowParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            courseList.addView(emptyState, params);
            return;
        }

        for (Course course : dayCourses) {
            courseList.addView(createCourseCard(course));
        }
    }

    private View createCourseCard(Course course) {
        TimetableRules.TemporalState temporalState = courseTemporalState(course);
        boolean active = temporalState == TimetableRules.TemporalState.REMINDER_WINDOW
                || temporalState == TimetableRules.TemporalState.IN_PROGRESS;
        boolean completed = temporalState == TimetableRules.TemporalState.COMPLETED;
        boolean compact = isCompactWidth();
        int minHeightDp = active ? (compact ? 142 : 150) : (compact ? 92 : 100);
        BoundedMaterialCardView shell = new BoundedMaterialCardView(this);
        shell.setMinimumHeight(dp(minHeightDp));
        // 仅进行中卡片锁定最大高度（稳定倒计时/进度动画）；
        // 普通与灰化卡片高度随内容自适应，两行课程名 + 时间胶囊 + 信息行不再被裁切，
        // 系统大字号、竖屏窄屏同样安全。
        shell.setMaxHeightPx(active ? dp(compact ? 158 : 168) : 0);
        shell.setRadius(dp(active ? 28 : 24));
        shell.setCardElevation(dp(active ? 5 : 1));
        shell.setCardBackgroundColor(active
                ? (CoursePalette.isMuted(course.color) ? CoursePalette.mutedSurface(course.color, true) : course.color)
                : mixColor(cardColor(), course.color, isDarkMode ? 0.08f : 0.04f));
        shell.setStrokeWidth(dp(1));
        shell.setStrokeColor(active
                ? Color.argb(90, 255, 255, 255)
                : mixColor(borderColor(), course.color, isDarkMode ? 0.20f : 0.12f));
        shell.setClipChildren(true);
        shell.setClipToPadding(true);
        shell.setClipToOutline(true);
        shell.setClickable(false);

        if (active) {
            shell.addView(createCurrentCourseProgressLayer(course), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        card.setPadding(
                dp(active ? (compact ? 16 : 20) : (compact ? 14 : 16)),
                dp(active ? (compact ? 20 : 22) : (compact ? 14 : 16)),
                dp(active ? (compact ? 16 : 20) : (compact ? 14 : 16)),
                dp(active ? (compact ? 20 : 22) : (compact ? 14 : 16))
        );
        shell.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
        ));

        // 左侧彩色节次徽章
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(compact ? 10 : 12), dp(8), dp(compact ? 10 : 12), dp(8));
        badge.setBackground(buttonBackground(active ? Color.argb(64, 255, 255, 255) : course.color, dp(active ? 20 : 16)));

        TextView periodNum = new TextView(this);
        String periodNumText;
        if (course.endPeriod > course.period) {
            periodNumText = String.format(Locale.CHINA, "%d-%d", course.period, course.endPeriod);
        } else {
            periodNumText = String.valueOf(course.period);
        }
        periodNum.setText(periodNumText);
        int badgeInk = !active && CoursePalette.isMuted(course.color)
                ? CoursePalette.readableInk(course.color) : Color.WHITE;
        periodNum.setTextColor(badgeInk);
        periodNum.setTypeface(appTypeface(Typeface.BOLD));
        float periodNumSize = active ? (compact ? 21 : 22) : (compact ? 19 : 20);
        if (periodNumText.length() >= 5) {
            periodNumSize = active ? (compact ? 14 : 15) : (compact ? 13 : 14);
        } else if (periodNumText.length() >= 3) {
            periodNumSize = active ? (compact ? 16 : 17) : (compact ? 15 : 16);
        }
        periodNum.setTextSize(periodNumSize);
        periodNum.setLetterSpacing(0f);
        periodNum.setGravity(Gravity.CENTER);
        periodNum.setSingleLine(true);
        periodNum.setIncludeFontPadding(false);
        badge.addView(periodNum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView periodLabel = new TextView(this);
        periodLabel.setText(getString(R.string.main_period_badge_label));
        periodLabel.setTextColor(badgeInk);
        periodLabel.setTextSize(12);
        periodLabel.setTypeface(appTypeface(Typeface.NORMAL));
        periodLabel.setAlpha(active ? 0.95f : 0.9f);
        periodLabel.setGravity(Gravity.CENTER);
        periodLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(2), 0, 0);
        badge.addView(periodLabel, labelParams);

        card.addView(badge, new LinearLayout.LayoutParams(dp(active ? 64 : 56), ViewGroup.LayoutParams.WRAP_CONTENT));

        // 中间课程信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(true);
        info.setClipToPadding(true);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(dp(compact ? 12 : 16), 0, dp(compact ? 8 : 12), 0);
        card.addView(info, infoParams);

        TextView name = new TextView(this);
        name.setText(course.name);
        name.setTextColor(active ? Color.WHITE : primaryTextColor());
        name.setTextSize(active ? (compact ? 20 : 21) : (compact ? 17 : 18));
        name.setTypeface(appTypeface(Typeface.BOLD));
        name.setLetterSpacing(0f);
        name.setMaxLines(active ? 1 : 2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);

        TextView time = new TextView(this);
        time.setText(periodRangeTime(course));
        time.setTextColor(active ? Color.WHITE : accentColor());
        time.setTextSize(12);
        time.setTypeface(appTypeface(Typeface.BOLD));
        time.setSingleLine(true);
        time.setEllipsize(TextUtils.TruncateAt.END);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        time.setPadding(dp(9), dp(4), dp(9), dp(4));
        time.setBackground(buttonBackground(
                active ? Color.argb(40, 255, 255, 255) : tonalContainerColor(), dp(9)));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, dp(6), 0, 0);
        info.addView(time, timeParams);

        if (active) {
            View remaining = createRemainingCountdownView(course, compact);
            LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            remainingParams.setMargins(0, dp(6), 0, 0);
            info.addView(remaining, remainingParams);
        }

        String detailText = courseDetailText(course);
        if (!detailText.equals(periodRangeTime(course))) {
            TextView detail = new TextView(this);
            String secondary = course.secondaryText();
            detail.setText(secondary);
            detail.setTextColor(active ? Color.WHITE : secondaryTextColor());
            detail.setTextSize(active ? (compact ? 14 : 15) : 14);
            detail.setTypeface(appTypeface(Typeface.NORMAL));
            detail.setAlpha(active ? 0.88f : 0.85f);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            detailParams.setMargins(0, dp(6), 0, 0);
            info.addView(detail, detailParams);
        }

        // 右侧编辑按钮
        ImageButton edit = new ImageButton(this);
        edit.setImageResource(R.drawable.ic_more_horizontal);
        edit.setImageTintList(ColorStateList.valueOf(active ? Color.argb(225, 255, 255, 255) : secondaryTextColor()));
        edit.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        edit.setBackground(interactiveSurfaceBackground(active
                ? Color.argb(34, 255, 255, 255)
                : withAlpha(secondaryTextColor(), isDarkMode ? 22 : 12), dp(18)));
        edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        edit.setContentDescription(getString(R.string.main_edit_course));
        edit.setOnClickListener(view -> showCourseDialog(course));
        card.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(contentSideMarginDp()), 0, dp(contentSideMarginDp()), dp(active ? 18 : 12));
        shell.setLayoutParams(params);
        if (completed) {
            shell.setForeground(completedCourseFilterBackground(dp(active ? 28 : 18)));
        }
        boolean approaching = temporalState == TimetableRules.TemporalState.REMINDER_WINDOW;
        boolean shakeEnabled = approaching && animationsEnabled();
        if (shakeEnabled) {
            attachShakeStopDoubleTap(shell, course);
        }
        if (shakeEnabled && !isShakeStopped(course)) {
            applyUpcomingCourseShake(shell, course);
        }
        return shell;
    }

    private View createCurrentCourseProgressLayer(Course course) {
        return new CourseProgressView(course);
    }

    private View createRemainingCountdownView(Course course, boolean compact) {
        return new CountdownRow(course, compact);
    }

    private CountdownState remainingCountdownState(Course course) {
        int start = courseStartSeconds(course);
        int end = courseEndSeconds(course);
        int now = currentSeconds();
        if (start < 0 || end < 0) {
            return CountdownState.plain(getString(R.string.main_countdown_end_unknown));
        }
        if (now < start) {
            return countdownStateForRemaining(true, start - now);
        }
        if (now >= end) {
            return CountdownState.plain(getString(R.string.main_class_over));
        }
        return countdownStateForRemaining(false, end - now);
    }

    private CountdownState countdownStateForRemaining(boolean starts, int remainingSeconds) {
        String prefix = starts
                ? getString(R.string.main_countdown_start_prefix)
                : getString(R.string.main_countdown_end_prefix);
        if (remainingSeconds <= 0) {
            return CountdownState.plain(starts ? getString(R.string.main_starting_soon) : getString(R.string.main_class_over));
        }
        if (remainingSeconds < COUNTDOWN_SECONDS_THRESHOLD) {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            String numberPrefix = minutes > 0 ? getString(R.string.main_countdown_prefix_minutes, prefix, minutes) : prefix;
            return CountdownState.seconds(
                    numberPrefix,
                    String.valueOf(seconds),
                    getString(R.string.main_countdown_seconds_suffix),
                    starts ? "start" : "end",
                    remainingSeconds
            );
        }

        int totalMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return CountdownState.plain(getString(R.string.main_countdown_hm, prefix, hours, minutes));
        }
        if (hours > 0) {
            return CountdownState.plain(getString(R.string.main_countdown_h, prefix, hours));
        }
        return CountdownState.plain(getString(R.string.main_countdown_m, prefix, totalMinutes));
    }

    private TextView createCountdownTextView(boolean compact) {
        TextView text = new TextView(this);
        text.setTextColor(Color.argb(245, 255, 255, 255));
        text.setTextSize(compact ? 14 : 15);
        text.setTypeface(appTypeface(Typeface.BOLD));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private String countdownKey(Course course, String phase) {
        long id = course.id != 0L
                ? course.id
                : ((long) (course.name == null ? 0 : course.name.hashCode()) << 32)
                ^ (course.day * 131L + course.period * 17L + course.endPeriod);
        return id + ":" + phase + ":" + course.day + ":" + course.period + ":" + course.endPeriod;
    }

    private CountdownSnapshot recentCountdownSnapshot(String key) {
        CountdownSnapshot snapshot = countdownSnapshots.get(key);
        if (snapshot == null) {
            return null;
        }
        long age = android.os.SystemClock.uptimeMillis() - snapshot.updatedAtMillis;
        return age <= PROGRESS_REFRESH_MS + COUNTDOWN_REFRESH_MS * 2L ? snapshot : null;
    }

    private void rememberCountdownSnapshot(String key, String secondsText) {
        if (countdownSnapshots.size() > MAX_COURSES * 4) {
            countdownSnapshots.clear();
        }
        countdownSnapshots.put(key, new CountdownSnapshot(secondsText, android.os.SystemClock.uptimeMillis()));
    }

    private String remainingUntilClassEndsText(Course course) {
        int start = courseStartMinutes(course);
        int end = courseEndMinutes(course);
        int now = currentMinutes();
        if (start < 0 || end < 0) {
            return getString(R.string.main_countdown_end_unknown);
        }
        if (now < start) {
            return remainingUntilClassStartsText(start - now);
        }
        if (now >= end) {
            return getString(R.string.main_class_over);
        }
        int remaining = end - now;
        if (remaining <= 0) {
            return getString(R.string.main_countdown_end_under_minute);
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return getString(R.string.main_countdown_hm, getString(R.string.main_countdown_end_prefix), hours, minutes);
        }
        if (hours > 0) {
            return getString(R.string.main_countdown_h, getString(R.string.main_countdown_end_prefix), hours);
        }
        return getString(R.string.main_countdown_m, getString(R.string.main_countdown_end_prefix), minutes);
    }

    private String remainingUntilClassStartsText(int remaining) {
        if (remaining <= 0) {
            return getString(R.string.main_starting_soon);
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return getString(R.string.main_countdown_hm, getString(R.string.main_countdown_start_prefix), hours, minutes);
        }
        if (hours > 0) {
            return getString(R.string.main_countdown_h, getString(R.string.main_countdown_start_prefix), hours);
        }
        return getString(R.string.main_countdown_m, getString(R.string.main_countdown_start_prefix), minutes);
    }

    private boolean isCourseApproachingStart(Course course) {
        return courseTemporalState(course) == TimetableRules.TemporalState.REMINDER_WINDOW;
    }

    private TimetableRules.TemporalState courseTemporalState(Course course) {
        if (viewingWeek != 0) {
            // 预览模式：不做实时状态，只按日期比较（过去灰化 / 未来正常）
            int previewWeek = clamp(viewingWeek, 1, calendarTotalWeeks());
            if (!courseOccursInTeachingWeek(course, previewWeek)) {
                return TimetableRules.TemporalState.UNAVAILABLE;
            }
            return courseDateMillisForWeek(course.day, previewWeek) < startOfTodayMillis()
                    ? TimetableRules.TemporalState.COMPLETED
                    : TimetableRules.TemporalState.UPCOMING;
        }
        int currentWeek = currentTeachingWeek();
        boolean occursThisWeek = course.temporary
                || (!course.invalidWeeks && courseOccursInTeachingWeek(course, currentWeek));
        int dateComparison;
        if (course.temporary) {
            dateComparison = Integer.compare(course.day, currentSchoolDay());
        } else if (occursThisWeek) {
            dateComparison = Long.compare(
                    courseDateMillisForWeek(course.day, currentWeek),
                    startOfTodayMillis()
            );
        } else {
            dateComparison = 0;
        }
        return TimetableRules.temporalStateForDateComparison(
                occursThisWeek,
                dateComparison,
                courseStartSeconds(course),
                courseEndSeconds(course),
                currentSeconds(),
                upcomingShakeStartMinutes(course) * 60
        );
    }

    private void attachShakeStopDoubleTap(View target, Course course) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        target.setContentDescription(getString(R.string.main_shake_stop_desc, course.name));
        target.setOnClickListener(view -> stopCourseShake(view, course));
        target.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
                downY[0] = event.getY();
                return true;
            }
            if (action != MotionEvent.ACTION_UP) {
                return true;
            }
            if (Math.abs(event.getX() - downX[0]) > dp(10) || Math.abs(event.getY() - downY[0]) > dp(10)) {
                return true;
            }
            String key = courseInteractionKey(course);
            long now = android.os.SystemClock.uptimeMillis();
            Long lastTap = lastShakeTapMillisByKey.get(key);
            if (lastTap != null
                    && now - lastTap <= android.view.ViewConfiguration.getDoubleTapTimeout()) {
                lastShakeTapMillisByKey.remove(key);
                view.performClick();
                return true;
            }
            if (lastShakeTapMillisByKey.size() > MAX_COURSES * 2) {
                lastShakeTapMillisByKey.clear();
            }
            lastShakeTapMillisByKey.put(key, now);
            return true;
        });
        target.setClickable(true);
    }

    private void stopCourseShake(View target, Course course) {
        if (stoppedShakeKeys.size() > MAX_COURSES * 2) {
            stoppedShakeKeys.clear();
        }
        stoppedShakeKeys.put(courseInteractionKey(course), android.os.SystemClock.uptimeMillis());
        cancelCourseCardAnimations(target);
        Toast.makeText(this, getString(R.string.main_shake_stopped), Toast.LENGTH_SHORT).show();
        target.post(this::render);
    }

    private boolean isShakeStopped(Course course) {
        if (!isCourseApproachingStart(course)) {
            clearShakeStop(course);
            return false;
        }
        return stoppedShakeKeys.containsKey(courseInteractionKey(course));
    }

    private void clearShakeStop(Course course) {
        String key = courseInteractionKey(course);
        stoppedShakeKeys.remove(key);
        lastShakeTapMillisByKey.remove(key);
    }

    private String courseInteractionKey(Course course) {
        Calendar calendar = Calendar.getInstance();
        String identity = course.id != 0L
                ? String.valueOf(course.id)
                : String.valueOf(course.name);
        return calendar.get(Calendar.YEAR)
                + ":" + calendar.get(Calendar.DAY_OF_YEAR)
                + ":" + identity
                + ":" + course.day
                + ":" + course.period
                + ":" + course.endPeriod
                + ":" + course.customStartMinutes
                + ":" + course.customEndMinutes;
    }

    private void applyUpcomingCourseShake(View target, Course course) {
        if (!animationsEnabled()) {
            return;
        }
        int start = courseStartMinutes(course);
        int remaining = Math.max(0, start - currentMinutes());
        int window = Math.max(1, upcomingShakeStartMinutes(course));
        float rawIntensity = 1f - Math.min(window, remaining) / (float) window;
        float intensity = rawIntensity * rawIntensity;
        float density = getResources().getDisplayMetrics().density;
        float xAmplitude = (0.35f + 5.9f * intensity) * density;
        float yAmplitude = (0.18f + 3.6f * intensity) * density;
        long duration = Math.max(980L, (long) (2800L - 1400L * intensity));
        int repeatCount = BuildConfig.ENABLE_TEST_COURSE && course.temporary
                ? 24
                : 1 + Math.round(2f * intensity);
        java.util.Random random = new java.util.Random(
                System.nanoTime() ^ (course.name == null ? 0 : course.name.hashCode())
        );
        float[][] path = new float[2][7];
        fillRandomFloatingPath(path, random, xAmplitude, yAmplitude);
        android.animation.ValueAnimator shake = android.animation.ValueAnimator.ofFloat(0f, 1f);
        shake.setDuration(duration);
        shake.setRepeatCount(repeatCount);
        shake.setRepeatMode(android.animation.ValueAnimator.RESTART);
        shake.setStartDelay(Math.abs(course.name == null ? 0 : course.name.hashCode()) % 180);
        shake.setInterpolator(new android.view.animation.LinearInterpolator());
        shake.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            int segmentCount = path[0].length - 1;
            int segment = Math.min(segmentCount - 1, (int) (fraction * segmentCount));
            float local = fraction * segmentCount - segment;
            float eased = local * local * (3f - 2f * local);
            target.setTranslationX(lerp(path[0][segment], path[0][segment + 1], eased));
            target.setTranslationY(lerp(path[1][segment], path[1][segment + 1], eased));
        });
        shake.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                fillRandomFloatingPath(path, random, xAmplitude, yAmplitude);
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                target.setTranslationX(0f);
                target.setTranslationY(0f);
                if (target.getTag() == animation) {
                    target.setTag(null);
                }
                target.setLayerType(View.LAYER_TYPE_NONE, null);
                if (!cancelled && target.isAttachedToWindow()) {
                    target.postDelayed(() -> {
                        if (activityResumed
                                && target.isAttachedToWindow()
                                && target.getTag() == null
                                && isCourseApproachingStart(course)
                                && !isShakeStopped(course)) {
                            applyUpcomingCourseShake(target, course);
                        }
                    }, 1400L);
                }
            }
        });
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        target.setTag(shake);
        shake.start();
    }

    private int upcomingShakeStartMinutes(Course course) {
        return Math.min(FLOATING_START_MINUTES, reminderLeadMinutes(course));
    }

    private void fillRandomFloatingPath(float[][] path, java.util.Random random, float xAmplitude, float yAmplitude) {
        path[0][0] = 0f;
        path[1][0] = 0f;
        for (int i = 1; i < path[0].length - 1; i++) {
            float scale = 0.45f + random.nextFloat() * 0.55f;
            path[0][i] = (random.nextFloat() * 2f - 1f) * xAmplitude * scale;
            path[1][i] = (random.nextFloat() * 2f - 1f) * yAmplitude * scale;
        }
        path[0][path[0].length - 1] = 0f;
        path[1][path[1].length - 1] = 0f;
    }

    private float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }

    private void cancelCourseCardAnimations(View view) {
        if (view == null) {
            return;
        }
        Object tag = view.getTag();
        if (tag instanceof android.animation.Animator) {
            ((android.animation.Animator) tag).cancel();
            view.setTag(null);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                cancelCourseCardAnimations(group.getChildAt(i));
            }
        }
    }

    private float courseProgress(Course course) {
        int start = courseStartSeconds(course);
        int end = courseEndSeconds(course);
        int now = currentSeconds();
        if (start < 0 || end <= start) {
            return 0f;
        }
        if (now < start) {
            return 0f;
        }
        if (now > end) {
            return 1f;
        }
        return (now - start) / (float) (end - start);
    }

    private int currentSeconds() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 3600
                + calendar.get(Calendar.MINUTE) * 60
                + calendar.get(Calendar.SECOND);
    }

    private int currentMinutes() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private int courseStartSeconds(Course course) {
        int minutes = courseStartMinutes(course);
        return minutes < 0 ? -1 : minutes * 60;
    }

    private int courseEndSeconds(Course course) {
        int minutes = courseEndMinutes(course);
        return minutes < 0 ? -1 : minutes * 60;
    }

    private int courseStartMinutes(Course course) {
        if (course.customStartMinutes >= 0) {
            return course.customStartMinutes;
        }
        return periodStartMinutes(course.period);
    }

    private int courseEndMinutes(Course course) {
        if (course.customEndMinutes >= 0) {
            return course.customEndMinutes;
        }
        return periodEndMinutes(course.endPeriod);
    }

    private int periodCount() {
        if (periodTimes.isEmpty()) {
            periodTimes.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
        }
        return periodTimes.size();
    }

    private int currentPeriod() {
        int now = currentMinutes();
        for (int period = 1; period <= periodCount(); period++) {
            int start = periodStartMinutes(period);
            int end = periodEndMinutes(period);
            if (start >= 0 && end >= 0 && now >= start && now <= end) {
                return period;
            }
        }
        return -1;
    }

    private int nearestDisplayPeriod() {
        int now = currentMinutes();
        for (int period = 1; period <= periodCount(); period++) {
            int end = periodEndMinutes(period);
            if (end >= now) {
                return period;
            }
        }
        return periodCount();
    }

    private int periodStartMinutes(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return -1;
        }
        String[] parts = periodTimes.get(index).split("-");
        return parts.length == 2 ? clockMinutes(parts[0]) : -1;
    }

    private int periodEndMinutes(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return -1;
        }
        String[] parts = periodTimes.get(index).split("-");
        return parts.length == 2 ? clockMinutes(parts[1]) : -1;
    }

    private int clockMinutes(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return -1;
        }
        int hour = parseInt(parts[0], -1);
        int minute = parseInt(parts[1], -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private Course findCourseById(long id) {
        for (Course course : courses) {
            if (course.id == id) return course;
        }
        return null;
    }

    private void showPastelCourseDetail(Course course) {
        PastelCourseSheet.Detail detail = new PastelCourseSheet.Detail();
        detail.name = course.name;
        detail.dayLabel = dayNames[clamp(course.day, 0, 6)];
        detail.periodLabel = course.periodLabel();
        detail.time = periodRangeTime(course);
        detail.weeks = course.weeks;
        detail.room = course.room;
        detail.teacher = course.teacher;
        detail.color = course.color;
        final long id = course.id;
        new PastelCourseSheet(this, isDarkMode, pastelAccent()).showDetail(detail, () -> {
            Course latest = findCourseById(id);
            if (latest != null) showPastelCourseEditor(latest, latest.day, latest.period);
        });
    }

    /** Pastel editor; validation and persistence match {@link #showCourseDialog(Course)}. */
    private void showPastelCourseEditor(Course editing, int day, int period) {
        int displayedWeek = AgendaDates.week(calendarStartMillis(), pastelDisplayedStart());
        if (editing == null && pendingCourseRestore == null
                && (displayedWeek < 1 || displayedWeek > calendarTotalWeeks())) {
            AppFonts.install(new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_outside_term_title))
                    .setMessage(getString(R.string.main_outside_term_message))
                    .setPositiveButton(getString(R.string.main_add_personal_event), (dialog, which) -> {
                        eventFromMine = false;
                        showPersonalEventEditor(null);
                    })
                    .setNeutralButton(getString(R.string.main_add_term_course), (dialog, which) -> openPastelCourseEditor(null, day, period))
                    .setNegativeButton(getString(R.string.action_cancel), null).show());
            return;
        }
        openPastelCourseEditor(editing, day, period);
    }

    private void openPastelCourseEditor(Course editing, int day, int period) {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_cannot_save), Toast.LENGTH_LONG).show();
            return;
        }
        final boolean isEditing = editing != null;
        final long id = isEditing ? editing.id : System.currentTimeMillis();
        PastelCourseSheet.Draft draft = new PastelCourseSheet.Draft();
        if (isEditing) {
            draft.day = editing.day;
            draft.start = editing.period;
            draft.end = Math.max(editing.period, editing.endPeriod);
            draft.name = editing.name;
            draft.weeks = editing.weeks;
            draft.room = editing.room;
            draft.teacher = editing.teacher;
            draft.color = safeColor(editing.color);
        } else {
            draft.day = day;
            draft.start = period;
            draft.end = period;
            draft.color = chooseCourseColorForDay(day, id);
        }
        PastelCourseSheet sheet = new PastelCourseSheet(this, isDarkMode, pastelAccent());
        activeCourseEditor = sheet;
        activeCourseId = isEditing ? id : 0;
        Bundle restored = pendingCourseRestore;
        pendingCourseRestore = null;
        sheet.showEditor(isEditing, draft, new ArrayList<>(periodTimes),
                COURSE_COLORS, courseColorNames, calendarTotalWeeks(), currentTeachingWeek(),
                d -> savePastelDraft(editing, id, d),
                isEditing ? () -> deletePastelCourse(id) : null, restored, () -> {
                    if (activeCourseEditor == sheet) {
                        activeCourseEditor = null;
                        activeCourseId = 0;
                    }
                });
    }

    private String savePastelDraft(Course editing, long id, PastelCourseSheet.Draft d) {
        if (storageLocked) return getString(R.string.main_locked_cannot_save);
        String name = cleanField(d.name);
        if (name.isEmpty()) return getString(R.string.main_enter_course_name);
        if (editing == null && courses.size() >= MAX_COURSES) return getString(R.string.main_course_limit);
        Course draft = editing != null ? editing.copy() : new Course();
        draft.id = id;
        draft.day = clamp(d.day, 0, 6);
        draft.period = clamp(d.start, 1, periodCount());
        draft.endPeriod = clamp(d.end, draft.period, periodCount());
        draft.name = name;
        draft.weeks = cleanField(d.weeks);
        TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(draft.weeks);
        if (!weekResult.valid) return weekResult.error;
        draft.invalidWeeks = false;
        draft.room = cleanField(d.room);
        draft.teacher = cleanField(d.teacher);
        draft.color = safeColor(d.color);
        // Same rule as the classic editor: only a changed schedule needs a conflict check.
        if (isScheduleChanged(draft, editing)) {
            Course conflict = findCourseConflict(draft);
            if (conflict != null) return courseConflictMessage(conflict);
        }
        int visibleWeek = AgendaDates.week(calendarStartMillis(), pastelDisplayedStart());
        if (!courseOccursInTeachingWeek(draft, visibleWeek)) {
            visibleWeek = 0;
            for (int week = 1; week <= calendarTotalWeeks(); week++) {
                if (courseOccursInTeachingWeek(draft, week)) {
                    visibleWeek = week;
                    break;
                }
            }
        }
        if (visibleWeek == 0 && (editing == null || isScheduleChanged(draft, editing))) {
            return getString(R.string.main_no_class_dates);
        }
        List<Course> candidate = copyCourses();
        upsertCourse(candidate, draft, d.colorManuallySelected);
        if (!saveCourses(candidate)) return getString(R.string.main_save_failed_retry);
        selectedDay = draft.day;
        if (visibleWeek > 0) browsePastelDate(courseDateMillisForWeek(draft.day, visibleWeek));
        else render();
        Toast.makeText(this, editing != null ? getString(R.string.main_changes_saved) : getString(R.string.main_added_to_timetable), Toast.LENGTH_SHORT).show();
        return null;
    }

    private boolean deletePastelCourse(long id) {
        List<Course> candidate = copyCourses();
        for (int i = candidate.size() - 1; i >= 0; i--) {
            if (candidate.get(i).id == id) candidate.remove(i);
        }
        if (!saveCourses(candidate)) return false;
        render();
        Toast.makeText(this, getString(R.string.main_course_deleted), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void showCourseDialog(Course editing) {
        final Bundle restored = pendingSettingsRestore != null
                && "classic_course".equals(pendingSettingsRestore.getString("kind"))
                ? pendingSettingsRestore : null;
        if (restored != null) pendingSettingsRestore = null;
        boolean isEditing = editing != null;
        Course draft = isEditing ? editing.copy() : new Course();
        if (!isEditing) {
            draft.id = System.currentTimeMillis();
            draft.day = selectedDay;
            draft.period = BuildConfig.PASTEL_UI ? pendingAddPeriod : 1;
            draft.endPeriod = draft.period;
            draft.color = chooseCourseColorForDay(draft.day, draft.id);
        }
        if (restored != null) {
            if (!isEditing) draft.id = restored.getLong("draft_id", draft.id);
            draft.day = clamp(restored.getInt("day", draft.day), 0, dayNames.length - 1);
            draft.period = clamp(restored.getInt("period", draft.period), 1, periodCount());
            draft.endPeriod = clamp(restored.getInt("end_period", draft.endPeriod), draft.period, periodCount());
            draft.name = restored.getString("name", draft.name);
            draft.weeks = restored.getString("weeks", draft.weeks);
            draft.room = restored.getString("room", draft.room);
            draft.teacher = restored.getString("teacher", draft.teacher);
            draft.color = restored.getInt("color", draft.color);
        }

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.setClipToPadding(false);
        formScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(14));
        formScroll.addView(form);

        Spinner daySpinner = spinner(dayNames);
        daySpinner.setSelection(draft.day);
        form.addView(label(getString(R.string.main_label_day)));
        form.addView(daySpinner, fieldParams());

        String[] periods = new String[periodCount()];
        for (int i = 0; i < periods.length; i++) {
            periods[i] = getString(R.string.main_period_with_time, i + 1, periodTime(i + 1));
        }
        Spinner periodSpinner = spinner(periods);
        periodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.period - 1)));
        form.addView(label(getString(R.string.main_label_start_period)));
        form.addView(periodSpinner, fieldParams());

        Spinner endPeriodSpinner = spinner(periods);
        endPeriodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.endPeriod - 1)));
        form.addView(label(getString(R.string.main_label_end_period)));
        form.addView(endPeriodSpinner, fieldParams());

        EditText nameInput = input(getString(R.string.main_hint_course_name), draft.name);
        form.addView(label(getString(R.string.main_label_course)));
        form.addView(nameInput, fieldParams());

        EditText weeksInput = input(getString(R.string.main_hint_weeks), draft.weeks);
        form.addView(label(getString(R.string.main_label_weeks)));
        form.addView(weeksInput, fieldParams());

        Button temporaryWeeksButton = new Button(this);
        temporaryWeeksButton.setText(getString(R.string.main_temporary_weeks_button));
        temporaryWeeksButton.setAllCaps(false);
        temporaryWeeksButton.setTextColor(accentColor());
        temporaryWeeksButton.setTextSize(14);
        temporaryWeeksButton.setTypeface(appTypeface(Typeface.BOLD));
        temporaryWeeksButton.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        temporaryWeeksButton.setPadding(dp(12), 0, dp(12), 0);
        temporaryWeeksButton.setOnClickListener(view -> showTemporaryWeeksDialog(weeksInput));
        LinearLayout.LayoutParams temporaryWeeksParams = fieldParams();
        temporaryWeeksParams.setMargins(0, 0, 0, dp(10));
        form.addView(temporaryWeeksButton, temporaryWeeksParams);

        EditText roomInput = input(getString(R.string.main_hint_room), draft.room);
        form.addView(label(getString(R.string.main_label_room)));
        form.addView(roomInput, fieldParams());

        EditText teacherInput = input(getString(R.string.main_label_teacher), draft.teacher);
        form.addView(label(getString(R.string.main_label_teacher)));
        form.addView(teacherInput, fieldParams());

        final int[] selectedColor = {safeColor(draft.color)};
        final boolean[] colorManuallySelected = {restored != null && restored.getBoolean("manual_color", false)};
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(label(getString(R.string.main_label_color)));
        colorScroll.addView(colorRow);
        form.addView(colorScroll, fieldParams());
        renderColorChoices(colorRow, selectedColor, () -> colorManuallySelected[0] = true);

        daySpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.day = position;
            if (!isEditing && !colorManuallySelected[0]) {
                selectedColor[0] = chooseCourseColorForDay(draft.day, draft.id);
                renderColorChoices(colorRow, selectedColor, () -> colorManuallySelected[0] = true);
            }
        }));
        periodSpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.period = position + 1;
            if (draft.endPeriod < draft.period) {
                draft.endPeriod = draft.period;
                endPeriodSpinner.setSelection(draft.endPeriod - 1);
            }
        }));
        endPeriodSpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.endPeriod = position + 1;
            if (draft.endPeriod < draft.period) {
                draft.endPeriod = draft.period;
                endPeriodSpinner.setSelection(draft.endPeriod - 1);
            }
        }));

        String[] restoredInitialFields = restored == null ? null : restored.getStringArray("initial_fields");
        final String[] initialFields = restoredInitialFields != null && restoredInitialFields.length == 4
                ? restoredInitialFields : new String[] {
                    nameInput.getText().toString(), weeksInput.getText().toString(),
                    roomInput.getText().toString(), teacherInput.getText().toString()
                };
        final int initialDay = restored == null ? daySpinner.getSelectedItemPosition()
                : restored.getInt("initial_day", daySpinner.getSelectedItemPosition());
        final int initialPeriod = restored == null ? periodSpinner.getSelectedItemPosition()
                : restored.getInt("initial_period", periodSpinner.getSelectedItemPosition());
        final int initialEndPeriod = restored == null ? endPeriodSpinner.getSelectedItemPosition()
                : restored.getInt("initial_end_period", endPeriodSpinner.getSelectedItemPosition());
        final int initialColor = restored == null ? selectedColor[0] : restored.getInt("initial_color", selectedColor[0]);
        MaterialAlertDialogBuilder editorStyle = new MaterialAlertDialogBuilder(this);
        AlertDialog dialog = new DirtyConfirmDialog(editorStyle.getContext(), () ->
                !initialFields[0].equals(nameInput.getText().toString())
                        || !initialFields[1].equals(weeksInput.getText().toString())
                        || !initialFields[2].equals(roomInput.getText().toString())
                        || !initialFields[3].equals(teacherInput.getText().toString())
                        || initialDay != daySpinner.getSelectedItemPosition()
                        || initialPeriod != periodSpinner.getSelectedItemPosition()
                        || initialEndPeriod != endPeriodSpinner.getSelectedItemPosition()
                        || initialColor != selectedColor[0]);
        dialog.setTitle(isEditing ? getString(R.string.main_edit_course) : getString(R.string.main_add_course));
        dialog.setView(formScroll);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.action_cancel), (d, which) -> { });
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.action_save), (d, which) -> { });

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> dialog.cancel());
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                if (storageLocked) {
                    Toast.makeText(this, getString(R.string.main_locked_cannot_save), Toast.LENGTH_LONG).show();
                    return;
                }
                String name = cleanField(nameInput.getText().toString());
                if (name.isEmpty()) {
                    Toast.makeText(this, getString(R.string.main_course_name_required), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isEditing && courses.size() >= MAX_COURSES) {
                    Toast.makeText(this, getString(R.string.main_course_limit), Toast.LENGTH_SHORT).show();
                    return;
                }
                draft.name = name;
                draft.weeks = cleanField(weeksInput.getText().toString());
                TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(draft.weeks);
                if (!weekResult.valid) {
                    weeksInput.requestFocus();
                    Toast.makeText(this, weekResult.error, Toast.LENGTH_LONG).show();
                    return;
                }
                draft.invalidWeeks = false;
                draft.room = cleanField(roomInput.getText().toString());
                draft.teacher = cleanField(teacherInput.getText().toString());
                draft.color = selectedColor[0];
                // 只有真正改动了时间（星期/节次/周次）才需要冲突检查；
                // 仅修改地点、老师、名称、颜色时，即使同时段存在其他记录
                // （如同一门课拆分成多条、或历史导入产生的重复数据），也不应阻止保存。
                if (isScheduleChanged(draft, editing)) {
                    Course conflict = findCourseConflict(draft);
                    if (conflict != null) {
                        Toast.makeText(this, courseConflictMessage(conflict), Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                List<Course> candidate = copyCourses();
                upsertCourse(candidate, draft, colorManuallySelected[0]);
                if (!saveCourses(candidate)) {
                    return; // Keep the draft and dialog available for retry.
                }
                selectedDay = draft.day;
                render();
                dialog.dismiss();
            });

            if (isEditing) {
                Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (delete != null) {
                    delete.setTextColor(dangerColor());
                    delete.setOnClickListener(view -> {
                        AlertDialog confirmation = new MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.main_delete_course_title))
                                .setMessage(getString(R.string.main_delete_course_message, editing.name))
                                .setNegativeButton(getString(R.string.action_cancel), null)
                                .setPositiveButton(getString(R.string.action_delete), null)
                                .create();
                        confirmation.setOnShowListener(shown -> {
                            Button confirmDelete = confirmation.getButton(AlertDialog.BUTTON_POSITIVE);
                            confirmDelete.setTextColor(dangerColor());
                            confirmDelete.setOnClickListener(button -> {
                                List<Course> candidate = copyCourses();
                                for (int i = candidate.size() - 1; i >= 0; i--) {
                                    if (candidate.get(i).id == editing.id) candidate.remove(i);
                                }
                                if (!saveCourses(candidate)) return;
                                confirmation.dismiss();
                                render();
                                dialog.dismiss();
                            });
                        });
                        confirmation.show();
                        AppFonts.install(confirmation);
                    });
                }
            }
        });

        if (isEditing) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_delete), (d, which) -> { });
        }

        trackSettingsDraft(dialog, () -> {
            Bundle state = new Bundle();
            state.putString("kind", "classic_course");
            state.putLong("id", isEditing ? editing.id : 0L);
            state.putLong("draft_id", draft.id);
            state.putString("name", nameInput.getText().toString());
            state.putString("weeks", weeksInput.getText().toString());
            state.putString("room", roomInput.getText().toString());
            state.putString("teacher", teacherInput.getText().toString());
            state.putInt("day", daySpinner.getSelectedItemPosition());
            state.putInt("period", periodSpinner.getSelectedItemPosition() + 1);
            state.putInt("end_period", endPeriodSpinner.getSelectedItemPosition() + 1);
            state.putInt("color", selectedColor[0]);
            state.putBoolean("manual_color", colorManuallySelected[0]);
            state.putStringArray("initial_fields", initialFields.clone());
            state.putInt("initial_day", initialDay);
            state.putInt("initial_period", initialPeriod);
            state.putInt("initial_end_period", initialEndPeriod);
            state.putInt("initial_color", initialColor);
            state.putInt("scroll", formScroll.getScrollY());
            return state;
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(editorStyle.getBackground());
        styleDialogWindow(dialog);
        if (restored != null) formScroll.post(() -> formScroll.scrollTo(0, Math.max(0, restored.getInt("scroll", 0))));
    }

    private void showTemporaryWeeksDialog(EditText weeksInput) {
        int currentWeek = currentTeachingWeek();
        int totalWeeks = calendarTotalWeeks();
        if (currentWeek <= 0 || currentWeek > totalWeeks) {
            Toast.makeText(this, getString(R.string.main_temp_weeks_outside), Toast.LENGTH_LONG).show();
            return;
        }

        EditText durationInput = new EditText(this);
        durationInput.setSingleLine(true);
        durationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durationInput.setText("1");
        durationInput.setSelectAllOnFocus(true);
        durationInput.setTextColor(primaryTextColor());
        durationInput.setHintTextColor(secondaryTextColor());
        durationInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        durationInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        durationInput.setBackground(elevatedCardBackground(cardColor(), dp(12)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_temp_weeks_title))
                .setMessage(getString(R.string.main_temp_weeks_message, currentWeek))
                .setView(durationInput)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_ok), null)
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                int duration = parseInt(durationInput.getText().toString(), 0);
                if (duration <= 0) {
                    Toast.makeText(this, getString(R.string.main_weeks_positive), Toast.LENGTH_SHORT).show();
                    return;
                }
                int endWeek = Math.min(totalWeeks, currentWeek + duration - 1);
                String weeksText = currentWeek == endWeek ? currentWeek + "周" : currentWeek + "-" + endWeek + "周";
                weeksInput.setText(weeksText);
                weeksInput.setSelection(weeksInput.getText().length());
                if (endWeek < currentWeek + duration - 1) {
                    Toast.makeText(this, getString(R.string.main_weeks_truncated, weeksText), Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showSettingsDialogV2() {
        if (settingsOverlay != null && settingsOverlay.getParent() != null) {
            settingsOverlay.requestFocus();
            return;
        }
        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) {
            return;
        }
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(isDarkMode ? Color.argb(116, 0, 0, 0) : Color.argb(82, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        final Runnable[] dismissSettings = new Runnable[1];
        dismissSettings[0] = () -> {
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            if (settingsOverlay == overlay) {
                settingsOverlay = null;
            }
        };
        overlay.setOnClickListener(view -> dismissSettings[0].run());
        overlay.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                dismissSettings[0].run();
                return true;
            }
            return false;
        });

        LinearLayout glassPanel = new LinearLayout(this);
        glassPanel.setOrientation(LinearLayout.VERTICAL);
        glassPanel.setPadding(dp(20), dp(18), dp(20), dp(18));
        glassPanel.setBackground(glassPanelBackground(dp(26)));
        glassPanel.setClickable(true);
        glassPanel.setFocusable(true);
        glassPanel.setOnClickListener(view -> {
        });
        glassPanel.setElevation(dp(24));
        glassPanel.setTranslationZ(dp(3));
        glassPanel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(2), 0, 0, dp(10));

        TextView title = new TextView(this);
        title.setText(getString(R.string.main_settings));
        title.setTextSize(22);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setTextColor(primaryTextColor());
        title.setIncludeFontPadding(false);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText(getString(R.string.action_close));
        close.setTextSize(13);
        close.setTypeface(appTypeface(Typeface.BOLD));
        close.setTextColor(accentColor());
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(dp(58));
        close.setMinHeight(dp(38));
        close.setPadding(dp(12), 0, dp(12), 0);
        close.setBackground(interactiveGlassSurfaceBackground(glassControlColor(), dp(18)));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(view -> dismissSettings[0].run());
        titleRow.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        glassPanel.addView(titleRow);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(2), dp(4), dp(2), 0);
        scroller.addView(panel);

        panel.addView(settingsSectionLabel(getString(R.string.main_section_timetable)));
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, getString(R.string.main_import_pdf), getString(R.string.main_import_pdf_summary), accentColor(), view -> {
            dismissSettings[0].run();
            openPdfPicker();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, getString(R.string.main_paste_import), getString(R.string.main_paste_import_summary), accentColor(), view -> {
            dismissSettings[0].run();
            showPasteImport();
        }), settingsRowParams());

        if (BuildConfig.ENABLE_TEST_COURSE) {
            panel.addView(settingsMenuRow(R.drawable.ic_bell, getString(R.string.main_test_reminder), getString(R.string.main_test_reminder_summary), accentColor(), view -> {
                dismissSettings[0].run();
                sendDebugReminderTestNotification();
            }), settingsRowParams());
        }
        panel.addView(settingsMenuRow(
                R.drawable.ic_bell,
                getString(R.string.main_notification_status),
                notificationStatusText(),
                accentColor(),
                view -> {
                    dismissSettings[0].run();
                    openReminderSystemSettings();
                }
        ), settingsRowParams());

        panel.addView(settingsToggleRow(
                R.drawable.ic_bell,
                getString(R.string.main_reminders),
                getString(R.string.main_reminders_summary),
                accentColor(),
                notificationsEnabled(),
                (buttonView, isChecked) -> applyNotificationToggle(isChecked)
        ), settingsRowParams());

        panel.addView(settingsSectionLabel(getString(R.string.main_section_time)));
        panel.addView(settingsMenuRow(R.drawable.ic_clock_outline, getString(R.string.main_period_settings), getString(R.string.main_period_settings_summary), accentColor(), view -> {
            dismissSettings[0].run();
            showPeriodSettingsDialog();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_calendar_outline, getString(R.string.main_calendar_settings), getString(R.string.main_calendar_settings_summary), accentColor(), view -> {
            dismissSettings[0].run();
            showCalendarImportDialog(false);
        }), settingsRowParams());

        panel.addView(settingsSectionLabel(getString(R.string.main_section_appearance)));
        if (BuildConfig.PASTEL_UI) {
            panel.addView(settingsToggleRow(
                    R.drawable.ic_file_text,
                    getString(R.string.main_show_teacher),
                    getString(R.string.main_show_teacher_summary),
                    accentColor(),
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(SHOW_TEACHER_KEY, false),
                    (buttonView, isChecked) -> {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putBoolean(SHOW_TEACHER_KEY, isChecked).apply();
                        render();
                    }
            ), settingsRowParams());
        }
        panel.addView(settingsMenuRow(R.drawable.ic_palette_outline, getString(R.string.main_randomize_colors), getString(R.string.main_randomize_colors_summary), accentColor(), view -> {
            dismissSettings[0].run();
            confirmRandomizeCourseColors();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_palette_outline, getString(R.string.main_accent_title), getString(R.string.main_accent_summary), accentColor(), view -> {
            showAccentColorDialog();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, getString(R.string.main_font_title), getString(R.string.font_settings_summary, AppFonts.currentName(this), AppFonts.sizePercent(this)), accentColor(), view -> {
            dismissSettings[0].run();
            showFontDialog();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, getString(R.string.language_title),
                getString(R.string.language_summary, AppLanguage.currentName(this)), accentColor(), view -> {
            dismissSettings[0].run();
            showLanguageDialog();
        }), settingsRowParams());

        panel.addView(settingsSectionLabel(getString(R.string.main_section_data)));
        panel.addView(settingsMenuRow(R.drawable.ic_trash_outline, getString(R.string.main_delete_config), getString(R.string.main_delete_config_summary), dangerColor(), view -> {
            dismissSettings[0].run();
            confirmDeleteCurrentConfiguration();
        }), settingsRowParams());

        glassPanel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(isCompactWidth() ? 28 : 56);
        FrameLayout.LayoutParams glassParams = new FrameLayout.LayoutParams(
                Math.min(availableWidth, dp(430)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        overlay.addView(glassPanel, glassParams);
        contentRoot.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        settingsOverlay = overlay;
        overlay.requestFocus();
    }

    private void confirmRandomizeCourseColors() {
        boolean hasCourses = false;
        for (Course course : courses) {
            if (!course.temporary && !TimetableRules.normalizedCourseText(course.name).isEmpty()) {
                hasCourses = true;
                break;
            }
        }
        if (!hasCourses) {
            Toast.makeText(this, getString(R.string.main_no_courses_to_color), Toast.LENGTH_LONG).show();
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_randomize_colors))
                .setMessage(getString(R.string.main_randomize_message))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.main_randomize_button), (d, which) -> {
                    List<Course> candidate = new ArrayList<>();
                    for (Course course : courses) candidate.add(course.copy());
                    int count = CourseColorRules.randomize(candidate, CoursePalette.RANDOM_COLORS, new java.util.Random());
                    if (!saveCourses(candidate)) return;
                    render();
                    Toast.makeText(this, getResources().getQuantityString(R.plurals.main_randomized_count, count, count), Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void trackSettingsDraft(AlertDialog dialog, SettingsDraftCapture capture) {
        settingsDraftCapture = capture;
        dialog.setOnDismissListener(ignored -> {
            if (settingsDraftCapture == capture) settingsDraftCapture = null;
        });
    }

    private void showFontDialog() {
        Bundle restored = pendingSettingsRestore;
        pendingSettingsRestore = null;
        String current = restored != null ? restored.getString("font", AppFonts.current(this)) : AppFonts.current(this);
        int checked = 0;
        for (int i = 0; i < AppFonts.IDS.length; i++) if (AppFonts.IDS[i].equals(current)) checked = i;
        final int[] selected = {checked};
        final int currentPercent = AppFonts.sizePercent(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(4), dp(20), dp(12));
        RadioGroup fonts = new RadioGroup(this);
        fonts.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < AppFonts.IDS.length; i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId());
            choice.setText(AppFonts.label(this, i));
            choice.setTextColor(primaryTextColor());
            choice.setMinHeight(dp(48));
            fonts.addView(choice, new RadioGroup.LayoutParams(-1, -2));
            if (i == checked) fonts.check(choice.getId());
        }
        // Install the choice listener below, once the preview updater is available.
        panel.addView(fonts);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(primaryTextColor());
        sizeLabel.setTextSize(15);
        sizeLabel.setPadding(0, dp(14), 0, dp(4));
        panel.addView(sizeLabel);
        TextView hint = new TextView(this);
        hint.setText(R.string.font_size_hint);
        hint.setTextSize(12);
        hint.setTextColor(secondaryTextColor());
        panel.addView(hint);
        SeekBar slider = new SeekBar(this);
        slider.setMax((FontSizeRules.MAX - FontSizeRules.MIN) / FontSizeRules.STEP);
        slider.setProgress(FontSizeRules.toProgress(restored == null ? currentPercent : restored.getInt("percent", currentPercent)));
        slider.setMinimumHeight(dp(48));
        panel.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView preview = new TextView(this);
        preview.setText(R.string.font_size_preview);
        preview.setTextColor(primaryTextColor());
        preview.setPadding(dp(12), dp(12), dp(12), dp(12));
        preview.setBackground(buttonBackground(glassControlColor(), dp(12)));
        panel.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        Runnable updatePreview = () -> {
            int percent = FontSizeRules.fromProgress(slider.getProgress());
            String label = getString(R.string.font_size_value, percent);
            sizeLabel.setText(label);
            slider.setContentDescription(label);
            // Resources already include the saved app multiplier: replace, never compound it.
            preview.setTextSize(16f * percent / currentPercent);
            AppFonts.previewTypeface(preview, AppFonts.IDS[selected[0]]);
        };
        fonts.setOnCheckedChangeListener((group, id) -> {
            int index = group.indexOfChild(group.findViewById(id));
            if (index < 0 || index >= AppFonts.IDS.length) return;
            selected[0] = index;
            updatePreview.run();
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updatePreview.run(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        updatePreview.run();
        ScrollView scroller = new ScrollView(this);
        scroller.addView(panel);
        // Bound the content so confirmation/cancel stay reachable in landscape or large text.
        int height = Math.min(dp(410), Math.max(dp(100), getResources().getDisplayMetrics().heightPixels - dp(220)));
        FrameLayout viewport = new FrameLayout(this);
        viewport.addView(scroller, new FrameLayout.LayoutParams(-1, height));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_font_title))
                .setView(viewport)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setNeutralButton(getString(R.string.main_reset_font_size), null)
                .setPositiveButton(getString(R.string.action_ok), null)
                .create();
        dialog.show();
        styleDialogWindow(dialog);
        trackSettingsDraft(dialog, () -> {
            Bundle state = new Bundle();
            state.putString("kind", "font");
            state.putString("font", AppFonts.IDS[selected[0]]);
            state.putInt("percent", FontSizeRules.fromProgress(slider.getProgress()));
            return state;
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                slider.setProgress(FontSizeRules.toProgress(FontSizeRules.DEFAULT)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = AppFonts.IDS[selected[0]];
            int percent = FontSizeRules.fromProgress(slider.getProgress());
            if (id.equals(AppFonts.current(this)) && percent == currentPercent) {
                dialog.dismiss();
                return;
            }
            if (!AppFonts.save(this, id, percent)) {
                Toast.makeText(this, getString(R.string.main_font_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            recreate();
        });
    }

    private void showLanguageDialog() {
        String current = AppLanguage.current(this);
        int checked = 0;
        for (int i = 0; i < AppLanguage.IDS.length; i++) if (AppLanguage.IDS[i].equals(current)) checked = i;
        final int[] selected = {checked};
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(AppLanguage.names(this), checked, (d, which) -> selected[0] = which)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.show();
        styleDialogWindow(dialog);
        AppFonts.install(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = AppLanguage.IDS[selected[0]];
            if (id.equals(AppLanguage.current(this))) {
                dialog.dismiss();
                return;
            }
            if (!AppLanguage.save(this, id)) {
                Toast.makeText(this, R.string.language_save_failed, Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            recreate();
        });
    }

    private void showAccentColorDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(10), dp(4), dp(6));
        int[] selected = new int[]{accentPresetIndex()};
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        int viewportHeight = Math.min(dp(420), Math.max(dp(100),
                getResources().getDisplayMetrics().heightPixels - dp(260)));
        FrameLayout viewport = new FrameLayout(this);
        scroller.addView(panel);
        viewport.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, viewportHeight));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_accent_title))
                .setView(viewport)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_ok), null)
                .create();
        renderAccentChoices(panel, selected, dialog);
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (confirm != null) {
                confirm.setOnClickListener(view -> {
                    int index = selected[0];
                    if (index != accentPresetIndex()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(ACCENT_KEY, index).apply();
                        dialog.dismiss();
                        recreate();
                    } else {
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void renderAccentChoices(LinearLayout panel, int[] selected, AlertDialog dialog) {
        panel.removeAllViews();
        final int columns = getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale > 1.25f ? 3 : 4;
        TextView hint = new TextView(this);
        hint.setText(R.string.accent_palette_hint);
        hint.setTextSize(12);
        hint.setTextColor(secondaryTextColor());
        hint.setPadding(dp(8), 0, dp(8), dp(8));
        panel.addView(hint);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(grid, gridParams);

        for (int i = 0; i < ACCENT_PRESETS.length; i++) {
            final int index = i;
            int color = ACCENT_PRESETS[i][isDarkMode ? 1 : 0];
            boolean isSelected = index == selected[0];

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClickable(true);
            cell.setFocusable(true);
            android.graphics.drawable.StateListDrawable cellBg = new android.graphics.drawable.StateListDrawable();
            cellBg.addState(new int[]{android.R.attr.state_pressed},
                    buttonBackground(withAlpha(accentColor(), isDarkMode ? 44 : 26), dp(14)));
            cellBg.addState(new int[]{}, buttonBackground(Color.TRANSPARENT, dp(14)));
            cell.setBackground(cellBg);
            cell.setContentDescription(UiAccentPalette.name(index)
                    + (isSelected ? getString(R.string.main_selected_short_suffix) : "")
                    + getString(R.string.main_tap_to_select_suffix));
            cell.setOnClickListener(view -> {
                selected[0] = index;
                renderAccentChoices(panel, selected, dialog);
            });

            TextView swatch = new TextView(this);
            swatch.setText(isSelected ? "\u2713" : "");
            swatch.setTextColor(CoursePalette.readableInk(color));
            swatch.setTextSize(18);
            swatch.setTypeface(appTypeface(Typeface.BOLD));
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(swatchStateListBackground(color, isSelected));
            swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(swatch, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView name = new TextView(this);
            name.setText(UiAccentPalette.name(index));
            name.setTextSize(12);
            name.setMinLines(2);
            name.setMaxLines(2);
            name.setTextColor(isSelected ? accentColor() : secondaryTextColor());
            name.setTypeface(appTypeface(isSelected ? Typeface.BOLD : Typeface.NORMAL));
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            nameParams.topMargin = dp(5);
            cell.addView(name, nameParams);

            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns, GridLayout.FILL, 1f)
            );
            cellParams.width = 0;
            cellParams.setMargins(dp(4), dp(8), dp(4), dp(8));
            grid.addView(cell, cellParams);
        }
    }

    private TextView settingsSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(appTypeface(Typeface.BOLD));
        label.setLetterSpacing(0.08f);
        label.setTextColor(secondaryTextColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(6), dp(12), dp(6), dp(5));
        return label;
    }

    private LinearLayout settingsMenuRow(int iconResId, String titleText, String subtitleText, int tintColor, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(interactiveGlassSurfaceBackground(glassRowColor(), dp(18)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(getString(R.string.main_title_subtitle_desc, titleText, subtitleText));
        row.setOnClickListener(listener);

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(buttonBackground(glassIconTintColor(tintColor), dp(14)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setImageTintList(ColorStateList.valueOf(tintColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconSurface.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconSurface, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(primaryTextColor());
        title.setTextSize(15);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(secondaryTextColor());
        subtitle.setTextSize(12);
        subtitle.setTypeface(appTypeface(Typeface.NORMAL));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(3), 0, 0);
        texts.addView(subtitle, subtitleParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(secondaryTextColor()));
        arrow.setAlpha(0.72f);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        return row;
    }

    private LinearLayout settingsToggleRow(int iconResId, String titleText, String subtitleText, int tintColor, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(interactiveGlassSurfaceBackground(glassRowColor(), dp(18)));

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(buttonBackground(glassIconTintColor(tintColor), dp(14)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setImageTintList(ColorStateList.valueOf(tintColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconSurface.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMargins(0, 0, dp(12), 0);
        row.addView(iconSurface, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(primaryTextColor());
        title.setTextSize(15);
        title.setTypeface(appTypeface(Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(secondaryTextColor());
        subtitle.setTextSize(12);
        subtitle.setTypeface(appTypeface(Typeface.NORMAL));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(3), 0, 0);
        texts.addView(subtitle, subtitleParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(titleText);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            row.setContentDescription(getString(R.string.main_toggle_desc, titleText,
                    getString(isChecked ? R.string.main_state_on : R.string.main_state_off)));
            if (listener != null) listener.onCheckedChanged(buttonView, isChecked);
        });
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 整行可点击切换，而不只是右侧的小开关
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(getString(R.string.main_toggle_desc, titleText,
                getString(checked ? R.string.main_state_on : R.string.main_state_off)));
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));

        return row;
    }

    private LinearLayout.LayoutParams settingsRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private int dangerColor() {
        return isDarkMode ? Color.rgb(244, 128, 112) : Color.rgb(177, 67, 55);
    }

    private int dangerContainerColor() {
        return isDarkMode ? Color.rgb(60, 20, 20) : Color.rgb(254, 242, 242);
    }

    private int softTintColor(int color) {
        return mixColor(color, cardColor(), isDarkMode ? 0.78f : 0.86f);
    }

    private int glassPanelTopColor() {
        return isDarkMode ? Color.argb(228, 31, 36, 47) : Color.argb(226, 255, 255, 255);
    }

    private int glassPanelBottomColor() {
        return isDarkMode ? Color.argb(216, 21, 26, 35) : Color.argb(206, 245, 250, 255);
    }

    private int glassRowColor() {
        return isDarkMode ? Color.argb(56, 255, 255, 255) : Color.argb(126, 255, 255, 255);
    }

    private int glassControlColor() {
        return isDarkMode ? Color.argb(68, 255, 255, 255) : Color.argb(146, 255, 255, 255);
    }

    private int glassBorderColor() {
        return isDarkMode ? Color.argb(72, 255, 255, 255) : Color.argb(164, 255, 255, 255);
    }

    private int glassOuterEdgeColor() {
        return isDarkMode ? Color.argb(98, 255, 255, 255) : Color.argb(190, 255, 255, 255);
    }

    private int glassIconTintColor(int color) {
        return Color.argb(isDarkMode ? 72 : 50, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int mixColor(int from, int to, float amount) {
        float safeAmount = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) * (1f - safeAmount) + Color.red(to) * safeAmount);
        int green = Math.round(Color.green(from) * (1f - safeAmount) + Color.green(to) * safeAmount);
        int blue = Math.round(Color.blue(from) * (1f - safeAmount) + Color.blue(to) * safeAmount);
        return Color.rgb(red, green, blue);
    }

    private void styleDialogWindow(AlertDialog dialog) {
        AppFonts.install(dialog);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(accentColor());
            positive.setTypeface(appTypeface(Typeface.BOLD));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(positive == null ? accentColor() : secondaryTextColor());
            negative.setTypeface(appTypeface(Typeface.BOLD));
        }
    }

    private void showSettingsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_settings))
                .setView(panel)
                .setNegativeButton(getString(R.string.action_close), null)
                .create();

        if (BuildConfig.ENABLE_TEST_COURSE) {
            Button testReminder = settingsActionButton(getString(R.string.main_test_reminder), accentColor());
            testReminder.setOnClickListener(view -> {
                dialog.dismiss();
                sendDebugReminderTestNotification();
            });
            panel.addView(testReminder, fieldParams());
        }

        Button importPdf = settingsActionButton(getString(R.string.main_import_pdf), accentColor());
        importPdf.setOnClickListener(view -> {
            dialog.dismiss();
            openPdfPicker();
        });
        panel.addView(importPdf, fieldParams());

        Button pasteImport = settingsActionButton(getString(R.string.main_paste_import), accentColor());
        pasteImport.setOnClickListener(view -> {
            dialog.dismiss();
            showPasteImport();
        });
        panel.addView(pasteImport, fieldParams());

        Button periodSettings = settingsActionButton(getString(R.string.main_period_settings), accentColor());
        periodSettings.setOnClickListener(view -> {
            dialog.dismiss();
            showPeriodSettingsDialog();
        });
        panel.addView(periodSettings, fieldParams());

        Button calendar = settingsActionButton(getString(R.string.main_calendar_settings), accentColor());
        calendar.setOnClickListener(view -> {
            dialog.dismiss();
            showCalendarImportDialog(false);
        });
        panel.addView(calendar, fieldParams());

        View divider = new View(this);
        divider.setBackgroundColor(borderColor());
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.setMargins(0, dp(12), 0, dp(12));
        panel.addView(divider, dividerParams);

        Button deleteConfig = settingsActionButton(getString(R.string.main_delete_config), dangerColor());
        deleteConfig.setOnClickListener(view -> {
            dialog.dismiss();
            confirmDeleteCurrentConfiguration();
        });
        panel.addView(deleteConfig, fieldParams());

        dialog.show();
    }

    private void showPeriodSettingsDialog() {
        periodCount();
        Bundle restored = pendingSettingsRestore;
        pendingSettingsRestore = null;
        ArrayList<String> restoredRules = restored == null ? null : restored.getStringArrayList("rules");
        List<String> draft = restoredRules == null ? new ArrayList<>(periodTimes) : new ArrayList<>(restoredRules);
        pendingPeriodRuleRestore = restored == null ? null : restored.getBundle("rule_editor");

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        TextView hint = label(getString(R.string.main_period_rules_hint));
        panel.addView(hint);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        );
        scrollParams.weight = 1f;
        panel.addView(scrollView, scrollParams);

        Button addRule = settingsActionButton(getString(R.string.main_add_period_rule), accentColor());
        LinearLayout.LayoutParams addRuleParams = fieldParams();
        addRuleParams.topMargin = dp(12);
        panel.addView(addRule, addRuleParams);

        Button resetDefault = settingsActionButton(getString(R.string.main_reset_periods, DEFAULT_PERIOD_TIMES.length), secondaryTextColor());
        panel.addView(resetDefault, fieldParams());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_period_settings))
                .setView(panel)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_save), null)
                .create();

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> renderPeriodRuleList(list, draft, refresh[0]);
        refresh[0].run();

        addRule.setOnClickListener(view -> {
            if (draft.size() >= MAX_PERIODS) {
                Toast.makeText(this, getString(R.string.main_max_periods, MAX_PERIODS), Toast.LENGTH_SHORT).show();
                return;
            }
            showPeriodRuleDialog(draft, -1, refresh[0]);
        });
        resetDefault.setOnClickListener(view -> {
            draft.clear();
            draft.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
            refresh[0].run();
        });

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String error = validatePeriodRules(draft);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return;
                }
                int maxUsedPeriod = maxUsedPeriod();
                if (draft.size() < maxUsedPeriod) {
                    Toast.makeText(this, getString(R.string.main_period_in_use, maxUsedPeriod), Toast.LENGTH_LONG).show();
                    return;
                }
                if (savePeriodTimes(draft)) {
                    render();
                    scheduleClassReminderNotifications();
                    dialog.dismiss();
                }
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
        trackSettingsDraft(dialog, () -> {
            Bundle state = new Bundle();
            state.putString("kind", "periods");
            state.putStringArrayList("rules", new ArrayList<>(draft));
            if (periodRuleDraftCapture != null) state.putBundle("rule_editor", periodRuleDraftCapture.capture());
            else if (pendingPeriodRuleRestore != null) state.putBundle("rule_editor", pendingPeriodRuleRestore);
            return state;
        });
        if (pendingPeriodRuleRestore != null) {
            int index = pendingPeriodRuleRestore.getInt("index", -1);
            if (index >= -1 && index < draft.size()) showPeriodRuleDialog(draft, index, refresh[0]);
            else pendingPeriodRuleRestore = null;
        }
    }

    private void maybeShowReminderPopupFromIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SHOW_REMINDER_POPUP, false)) {
            return;
        }
        if (!ClassReminderReceiver.isTrustedPopup(this, intent)) {
            // Forged or stale launch: open the timetable normally, never show caller-supplied text.
            intent.removeExtra(EXTRA_SHOW_REMINDER_POPUP);
            return;
        }
        intent.removeExtra(ClassReminderReceiver.EXTRA_POPUP_TOKEN);
        String name = intent.getStringExtra(EXTRA_REMINDER_COURSE_NAME);
        String time = intent.getStringExtra(EXTRA_REMINDER_COURSE_TIME);
        String room = intent.getStringExtra(EXTRA_REMINDER_COURSE_ROOM);
        int minutes = intent.getIntExtra(EXTRA_REMINDER_LEAD_MINUTES, LONG_CLASS_REMINDER_MINUTES);
        boolean event = ReminderScheduler.KIND_EVENT.equals(intent.getStringExtra(ClassReminderReceiver.EXTRA_KIND));
        intent.removeExtra(EXTRA_SHOW_REMINDER_POPUP);
        long startAt = intent.getLongExtra(ClassReminderReceiver.EXTRA_START_AT, -1L);
        long endAt = intent.getLongExtra(ClassReminderReceiver.EXTRA_END_AT, -1L);
        Runnable popup = () -> showClassReminderPopup(name, time, room, minutes, event, startAt, endAt);
        uiHandler.postDelayed(
                () -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (activityResumed) {
                        popup.run();
                    } else {
                        pendingReminderPopup = popup;
                    }
                },
                350L
        );
    }

    private void maybeSendDebugReminderTestNotification(Intent intent) {
        if (!BuildConfig.ENABLE_TEST_COURSE
                || intent == null
                || !intent.getBooleanExtra(EXTRA_SEND_REMINDER_TEST_NOTIFICATION, false)) {
            return;
        }
        intent.removeExtra(EXTRA_SEND_REMINDER_TEST_NOTIFICATION);
        uiHandler.postDelayed(
                this::sendDebugReminderTestNotification,
                650L
        );
    }

    private void showClassReminderPopup(String name, String time, String room, int minutes, boolean event) {
        showClassReminderPopup(name, time, room, minutes, event, -1L, -1L);
    }

    private void showClassReminderPopup(String name, String time, String room, int minutes, boolean event, long startAt, long endAt) {
        String courseName = TextUtils.isEmpty(name) ? (event ? getString(R.string.main_event) : getString(R.string.main_next_class)) : name.trim();
        StringBuilder message = new StringBuilder(ReminderTiming.statusText(event, startAt, endAt, System.currentTimeMillis()));
        if (!TextUtils.isEmpty(time)) {
            message.append("\n").append(getString(R.string.main_popup_time, time.trim()));
        }
        if (!TextUtils.isEmpty(room)) {
            message.append("\n").append(getString(R.string.main_popup_location, room.trim()));
        }
        AppFonts.install(new MaterialAlertDialogBuilder(this)
                .setTitle(courseName)
                .setMessage(message.toString())
                .setPositiveButton(getString(R.string.main_got_it), null)
                .show());
    }

    private void renderPeriodRuleList(LinearLayout list, List<String> draft, Runnable refresh) {
        list.removeAllViews();
        for (int i = 0; i < draft.size(); i++) {
            final int index = i;
            Button row = settingsActionButton(getString(R.string.main_period_with_time, i + 1, draft.get(i)), primaryTextColor());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnClickListener(view -> showPeriodRuleDialog(draft, index, refresh));
            list.addView(row, fieldParams());
        }
    }

    private void showPeriodRuleDialog(List<String> draft, int index, Runnable refresh) {
        boolean editing = index >= 0;
        Bundle restored = pendingPeriodRuleRestore;
        pendingPeriodRuleRestore = null;
        String value = editing ? draft.get(index) : suggestedNextPeriodTime(draft);
        String[] parts = value.split("-");

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("HH:mm", parts.length == 2 ? parts[0] : "");
        EditText endInput = input("HH:mm", parts.length == 2 ? parts[1] : "");
        if (restored != null) {
            startInput.setText(restored.getString("start", startInput.getText().toString()));
            endInput.setText(restored.getString("end", endInput.getText().toString()));
        }
        form.addView(label(getString(R.string.main_label_start_time)));
        form.addView(startInput, fieldParams());
        form.addView(label(getString(R.string.main_label_end_time)));
        form.addView(endInput, fieldParams());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(editing ? getString(R.string.main_period_n, index + 1) : getString(R.string.main_add_period_rule))
                .setView(form)
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_save), null);
        if (editing && draft.size() > 1) {
            builder.setNeutralButton(getString(R.string.action_delete), null);
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                String range = periodRangeFromInputs(startInput, endInput);
                if (range == null) {
                    return;
                }
                if (editing) {
                    draft.set(index, range);
                } else {
                    draft.add(range);
                }
                refresh.run();
                dialog.dismiss();
            });
            Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (delete != null) {
                delete.setOnClickListener(view -> {
                    int highestUsed = 0;
                    for (Course course : courses) if (!course.temporary) highestUsed = Math.max(highestUsed, course.endPeriod);
                    if (!PeriodEditRules.canDelete(index, draft.size(), highestUsed)) {
                        Toast.makeText(this, getString(R.string.main_period_delete_blocked), Toast.LENGTH_LONG).show();
                        return;
                    }
                    draft.remove(index);
                    refresh.run();
                    dialog.dismiss();
                });
            }
        });
        SettingsDraftCapture capture = () -> {
            Bundle state = new Bundle();
            state.putInt("index", index);
            state.putString("start", startInput.getText().toString());
            state.putString("end", endInput.getText().toString());
            return state;
        };
        periodRuleDraftCapture = capture;
        dialog.setOnDismissListener(ignored -> {
            if (periodRuleDraftCapture == capture) periodRuleDraftCapture = null;
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private String periodRangeFromInputs(EditText startInput, EditText endInput) {
        String start = normalizeClock(startInput.getText().toString());
        String end = normalizeClock(endInput.getText().toString());
        if (start == null || end == null) {
            Toast.makeText(this, getString(R.string.main_time_format), Toast.LENGTH_SHORT).show();
            return null;
        }
        if (clockMinutes(end) <= clockMinutes(start)) {
            Toast.makeText(this, getString(R.string.main_end_after_start), Toast.LENGTH_SHORT).show();
            return null;
        }
        return start + "-" + end;
    }

    private String normalizeClock(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        Matcher matcher = Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int hour = parseInt(matcher.group(1), -1);
        int minute = parseInt(matcher.group(2), -1);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private String suggestedNextPeriodTime(List<String> draft) {
        if (draft.isEmpty()) {
            return DEFAULT_PERIOD_TIMES[0];
        }
        String[] parts = draft.get(draft.size() - 1).split("-");
        int start = parts.length == 2 ? clockMinutes(parts[1]) + 10 : 8 * 60;
        if (start < 0 || start >= 23 * 60) {
            start = 8 * 60;
        }
        int end = Math.min(start + 45, 23 * 60 + 59);
        return clockText(start) + "-" + clockText(end);
    }

    private String clockText(int minutes) {
        int safe = clamp(minutes, 0, 23 * 60 + 59);
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60);
    }

    private String validatePeriodRules(List<String> rules) {
        if (rules.isEmpty()) {
            return getString(R.string.main_min_one_period);
        }
        if (rules.size() > MAX_PERIODS) {
            return getString(R.string.main_max_periods, MAX_PERIODS);
        }
        int previousEnd = -1;
        for (int i = 0; i < rules.size(); i++) {
            String[] parts = rules.get(i).split("-");
            if (parts.length != 2) {
                return getString(R.string.main_period_format_invalid, i + 1);
            }
            int start = clockMinutes(parts[0]);
            int end = clockMinutes(parts[1]);
            if (start < 0 || end <= start) {
                return getString(R.string.main_period_time_invalid, i + 1);
            }
            if (previousEnd > start) {
                return getString(R.string.main_periods_order);
            }
            previousEnd = end;
        }
        return null;
    }

    private int maxUsedPeriod() {
        int max = 1;
        for (Course course : courses) {
            if (!course.temporary) {
                max = Math.max(max, course.endPeriod);
            }
        }
        return max;
    }

    private void loadPeriodTimes() {
        periodTimes.clear();
        String encoded = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PERIOD_TIMES_KEY, null);
        if (encoded != null) {
            try {
                JSONArray array = new JSONArray(encoded);
                List<String> loaded = new ArrayList<>();
                for (int i = 0; i < array.length() && loaded.size() < MAX_PERIODS; i++) {
                    String value = array.optString(i, "").trim();
                    if (!value.isEmpty()) {
                        loaded.add(value);
                    }
                }
                if (validatePeriodRules(loaded) == null) {
                    periodTimes.addAll(loaded);
                }
            } catch (Exception ignored) {
                periodTimes.clear();
            }
        }
        if (periodTimes.isEmpty()) {
            periodTimes.addAll(Arrays.asList(DEFAULT_PERIOD_TIMES));
        }
    }

    private boolean savePeriodTimes(List<String> rules) {
        JSONArray array = new JSONArray();
        for (String rule : rules) {
            array.put(rule);
        }
        boolean committed = SafePreferences.commit(getSharedPreferences(PREFS_NAME, MODE_PRIVATE),
                new PreferenceTransaction().put(PERIOD_TIMES_KEY, array.toString()));
        if (!committed) {
            Toast.makeText(this, getString(R.string.main_period_save_failed), Toast.LENGTH_LONG).show();
            return false;
        }
        periodTimes.clear();
        periodTimes.addAll(rules);
        Toast.makeText(this, getString(R.string.main_period_saved), Toast.LENGTH_SHORT).show();
        return true;
    }

    private void sendDebugReminderTestNotification() {
        if (!BuildConfig.ENABLE_TEST_COURSE) {
            return;
        }
        if (!notificationsEnabled()) {
            showRemindersDisabledDialog(this::sendDebugReminderTestNotification);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ensureNotificationPermission();
            Toast.makeText(this, getString(R.string.main_allow_notifications_first), Toast.LENGTH_SHORT).show();
            return;
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            Toast.makeText(this, getString(R.string.main_app_notifications_off), Toast.LENGTH_LONG).show();
            openNotificationSettings();
            return;
        }
        Course course = new Course();
        int period = currentPeriod();
        if (period < 1) {
            period = nearestDisplayPeriod();
        }
        course.name = getString(R.string.main_test_reminder_course);
        course.period = period;
        course.endPeriod = period;
        course.room = getString(R.string.main_test_reminder_room);
        int minutes = reminderLeadMinutes(course);
        if (!ClassReminderReceiver.sendDebugNotification(this, classReminderIntent(course))) {
            Toast.makeText(this, getString(R.string.main_test_send_failed), Toast.LENGTH_LONG).show();
            return;
        }
        showClassReminderPopup(course.name, periodRangeTime(course), course.room, minutes, false);
        Toast.makeText(this, getString(R.string.main_test_sent), Toast.LENGTH_SHORT).show();
    }

    private String notificationStatusText() {
        if (!notificationsEnabled()) {
            return getString(R.string.main_status_reminders_off);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return getString(R.string.main_status_not_granted);
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return getString(R.string.main_status_service_unavailable);
        }
        if (android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            return getString(R.string.main_status_blocked);
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
            if (channel != null && channel.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) {
                return getString(R.string.main_status_channel_off);
            }
        }
        ReminderScheduler.Status status = ReminderScheduler.status(this);
        if (!status.submissionOk) {
            return getString(R.string.main_status_submit_failed);
        }
        if (status.pendingCourses <= 0 || status.nextTriggerAt <= 0L) {
            return getString(R.string.main_status_none_pending);
        }
        String nextTime = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new java.util.Date(status.nextTriggerAt));
        String precision = status.exactAlarmAllowed ? getString(R.string.main_precision_exact) : getString(R.string.main_precision_inexact);
        return getString(R.string.main_status_scheduled, status.pendingCourses, nextTime, precision);
    }

    private void openReminderSystemSettings() {
        if (!notificationsEnabled()) {
            // The in-app switch is off; system settings cannot fix that.
            showRemindersDisabledDialog(null);
            return;
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean notificationsEnabled = manager != null
                && (android.os.Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled());
        boolean permissionGranted = android.os.Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean channelEnabled = true;
        if (manager != null && android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
            channelEnabled = channel == null
                    || channel.getImportance() != android.app.NotificationManager.IMPORTANCE_NONE;
        }
        ReminderScheduler.Status status = ReminderScheduler.status(this);
        if (permissionGranted && notificationsEnabled && channelEnabled
                && status.pendingCourses > 0
                && !status.exactAlarmAllowed
                && openExactAlarmSettings()) {
            return;
        }
        openNotificationSettings();
    }

    private void showRemindersDisabledDialog(Runnable afterEnable) {
        AppFonts.install(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_reminders_off_title))
                .setMessage(getString(R.string.main_reminders_off_message))
                .setPositiveButton(getString(R.string.main_turn_on_reminders), (dialog, which) -> {
                    applyNotificationToggle(true);
                    Toast.makeText(this, getString(R.string.main_reminders_on_toast), Toast.LENGTH_SHORT).show();
                    if (afterEnable != null) {
                        afterEnable.run();
                    }
                })
                .setNegativeButton(getString(R.string.main_not_now), null)
                .show());
    }

    private boolean openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
            ));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ClassReminderReceiver.CHANNEL_ID);
        } else {
            intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("app_package", getPackageName())
                    .putExtra("app_uid", getApplicationInfo().uid);
        }
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            Intent details = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(details);
        }
    }

    private Button settingsActionButton(String text, int textColor) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(appTypeface(Typeface.BOLD));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(50));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(14));
        button.setBackgroundColor(cardColor());
        button.setStrokeColor(ColorStateList.valueOf(borderColor()));
        button.setStrokeWidth(dp(1));
        button.setRippleColor(ColorStateList.valueOf(withAlpha(accentColor(), isDarkMode ? 72 : 36)));
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private void confirmDeleteCurrentConfiguration() {
        AppFonts.install(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.main_delete_config))
                .setMessage(getString(R.string.main_delete_config_message))
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_delete), (dialog, which) -> deleteCurrentConfiguration())
                .show());
    }

    private void deleteCurrentConfiguration() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String legacyReminderCodes = prefs.getString(REMINDER_CODES_KEY, null);
        boolean committed = SafePreferences.commit(prefs, new PreferenceTransaction()
                .remove(COURSES_KEY)
                .remove(LEGACY_COURSES_KEY)
                .remove(PERIOD_TIMES_KEY)
                .remove(CALENDAR_START_KEY)
                .remove(CALENDAR_END_KEY)
                .remove(CALENDAR_WEEKS_KEY)
                .remove(CALENDAR_NAME_KEY)
                .remove(CALENDAR_INITIALIZED_KEY)
                .remove(REMINDER_CODES_KEY));
        if (!committed) {
            // Nothing was published: courses, calendar and reminders stay as they were.
            Toast.makeText(this, getString(R.string.main_delete_config_failed), Toast.LENGTH_LONG).show();
            return;
        }
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            cancelScheduledClassReminders(alarmManager, legacyReminderCodes);
        }
        ReminderScheduler.clear(this);
        courses.clear();
        storageLocked = false;
        loadPeriodTimes();
        selectedDay = currentSchoolDay();
        viewingWeek = 0;
        pastelBrowseDate = 0;
        addTemporaryDynamicIslandCourseIfDebug();
        // Personal events are not part of the timetable configuration; keep their reminders.
        scheduleClassReminderNotifications();
        render();
        Toast.makeText(this, getString(R.string.main_config_deleted), Toast.LENGTH_LONG).show();
    }

    private void showImportChooser() {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_cannot_import), Toast.LENGTH_LONG).show();
            return;
        }
        new PastelCourseSheet(this, isDarkMode, pastelAccent()).showImportChooser(this::openPdfPicker, this::showPasteImport);
    }

    /** Plain-text import for timetables whose PDF layout the parser cannot read. */
    private void showPasteImport() {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_cannot_import), Toast.LENGTH_LONG).show();
            return;
        }
        new PastelCourseSheet(this, isDarkMode, pastelAccent()).showPasteImport(
                TextTimetableParser.FORMAT_HINT, TextTimetableParser.EXAMPLE, text -> {
                    TextTimetableParser.Result result = TextTimetableParser.parse(text, MAX_PERIODS, MAX_COURSES);
                    if (result.courses.isEmpty()) {
                        return joinImportErrors(result.errors);
                    }
                    if (!result.errors.isEmpty()) {
                        // Partial input is never imported silently: the user fixes the listed lines first.
                        return getResources().getQuantityString(R.plurals.main_paste_lines_need_fix,
                                result.errors.size(), result.errors.size(), joinImportErrors(result.errors));
                    }
                    parsedAndShowCourses(result.courses, getString(R.string.main_pasted_text_source));
                    return null;
                });
    }

    private String joinImportErrors(List<String> errors) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < errors.size() && i < 5; i++) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(errors.get(i));
        }
        if (errors.size() > 5) builder.append('\n').append(getString(R.string.main_more_errors, errors.size() - 5));
        return builder.toString();
    }

    private void openPdfPicker() {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_cannot_import), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_PDF);
    }

    private void maybePromptCalendarSetup() {
        boolean initialized = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(CALENDAR_INITIALIZED_KEY, containsLegacyCalendar());
        if (!initialized) {
            showCalendarImportDialog(true);
        }
    }

    private boolean containsLegacyCalendar() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.contains(CALENDAR_START_KEY) && prefs.contains(CALENDAR_END_KEY);
    }

    private void showCalendarImportDialog(boolean firstRun) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("YYYY-MM-DD", firstRun ? "" : formatDate(calendarStartMillis()));
        EditText endInput = input("YYYY-MM-DD", firstRun ? "" : formatDate(calendarEndMillis()));
        form.addView(label(getString(R.string.main_label_term_start)));
        form.addView(startInput, fieldParams());
        form.addView(label(getString(R.string.main_label_term_end)));
        form.addView(endInput, fieldParams());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(firstRun ? getString(R.string.main_welcome_calendar_title) : getString(R.string.main_calendar_settings))
                .setMessage(firstRun
                        ? getString(R.string.main_welcome_calendar_message)
                        : getString(R.string.main_calendar_message))
                .setView(form)
                .setPositiveButton(getString(R.string.action_save), null);
        if (!firstRun) {
            builder.setNegativeButton(getString(R.string.action_cancel), null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!firstRun);
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                Long start = parseDateMillis(startInput.getText().toString());
                Long end = parseDateMillis(endInput.getText().toString());
                if (start == null || end == null) {
                    Toast.makeText(this, getString(R.string.main_date_format), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (end < start) {
                    Toast.makeText(this, getString(R.string.main_end_before_start), Toast.LENGTH_SHORT).show();
                    return;
                }
                saveAcademicCalendar(getString(R.string.main_calendar_manual_name), start, end);
                dialog.dismiss();
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void saveAcademicCalendar(String name, long startMillis, long endMillis) {
        int totalWeeks = Math.max(1, AgendaDates.week(startMillis, endMillis));
        boolean committed = SafePreferences.commit(getSharedPreferences(PREFS_NAME, MODE_PRIVATE),
                new PreferenceTransaction()
                        .put(CALENDAR_START_KEY, startMillis)
                        .put(CALENDAR_END_KEY, endMillis)
                        .put(CALENDAR_WEEKS_KEY, totalWeeks)
                        .put(CALENDAR_NAME_KEY, name)
                        .put(CALENDAR_INITIALIZED_KEY, true));
        if (!committed) {
            Toast.makeText(this, getString(R.string.main_calendar_save_failed), Toast.LENGTH_LONG).show();
            return;
        }
        viewingWeek = 0;
        pastelBrowseDate = 0;
        render();
        scheduleClassReminderNotifications();
        Toast.makeText(this, getResources().getQuantityString(R.plurals.main_calendar_saved, totalWeeks, totalWeeks), Toast.LENGTH_LONG).show();
    }

    private void importPdf(Uri uri) {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_cannot_import), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, getString(R.string.main_parsing_pdf), Toast.LENGTH_SHORT).show();
        // Parse the full supported range first; otherwise courses after the configured
        // last period disappear silently before the user can review the import.
        final int maxPeriods = MAX_PERIODS;
        cancelPdfImport();
        pendingImportUri = uri;
        final long token = pdfImportDelivery.begin();
        Thread worker = new Thread(() -> {
            Runnable outcome;
            try {
                byte[] pdfBytes = readUriBytes(uri, MAX_PDF_BYTES);
                PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                        pdfBytes, maxPeriods, MAX_STREAM_BYTES, MAX_COURSES);
                if (BuildConfig.DEBUG) {
                    // The parse log contains course names, rooms and teachers; never log it in release.
                    android.util.Log.d("PDFImport", result.debugLog);
                }
                outcome = () -> parsedAndShowCourses(result.courses);
            } catch (ImportException error) {
                outcome = () -> Toast.makeText(this, getString(R.string.main_import_error, error.getMessage()), Toast.LENGTH_LONG).show();
            } catch (SecurityException error) {
                outcome = () -> Toast.makeText(this, getString(R.string.main_pdf_permission_lost), Toast.LENGTH_LONG).show();
            } catch (Exception error) {
                outcome = () -> Toast.makeText(this, getString(R.string.main_pdf_import_failed, error.getClass().getSimpleName()), Toast.LENGTH_LONG).show();
            }
            if (pdfImportDelivery.offer(token, outcome)) {
                runOnUiThread(this::deliverPendingPdfResult);
            }
        }, "pdf-import");
        pdfImportThread = worker;
        worker.start();
    }

    /** Runs on the UI thread; shows the latest import result only while the activity is resumed. */
    private void deliverPendingPdfResult() {
        Runnable result = pdfImportDelivery.take();
        if (result == null || isFinishing() || isDestroyed()) {
            return;
        }
        pendingImportUri = null;
        pdfImportThread = null;
        result.run();
    }

    private void cancelPdfImport() {
        pdfImportDelivery.cancel();
        if (pdfImportThread != null) {
            pdfImportThread.interrupt();
            pdfImportThread = null;
        }
        pendingImportUri = null;
    }


    private void parsedAndShowCourses(List<PdfCourseParser.ParsedCourse> parsedItems) {
        parsedAndShowCourses(parsedItems, "PDF");
    }

    private void parsedAndShowCourses(List<PdfCourseParser.ParsedCourse> parsedItems, String sourceLabel) {
        int requiredPeriods = 0;
        for (PdfCourseParser.ParsedCourse item : parsedItems) {
            requiredPeriods = Math.max(requiredPeriods, item.endPeriod);
        }
        if (requiredPeriods > periodCount()) {
            AppFonts.install(new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.main_need_more_periods_title))
                    .setMessage(getString(R.string.main_need_more_periods_message,
                            sourceLabel, parsedItems.size(), requiredPeriods, periodCount()))
                    .setPositiveButton(getString(R.string.main_set_periods), (dialog, which) -> showPeriodSettingsDialog())
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show());
            return;
        }
        List<Course> parsedCourses = new ArrayList<>();
        for (PdfCourseParser.ParsedCourse item : parsedItems) {
            Course course = new Course();
            course.day = item.day;
            course.period = item.period;
            course.endPeriod = item.endPeriod;
            course.name = item.name;
            course.weeks = item.weeks;
            course.room = item.room;
            course.teacher = item.teacher;
            Integer sharedColor = CourseColorRules.findColor(courses, course.name);
            if (sharedColor == null) sharedColor = CourseColorRules.findColor(parsedCourses, course.name);
            course.color = sharedColor != null ? sharedColor : previewCourseColorForDay(parsedCourses, course.day);
            parsedCourses.add(course);
        }

        if (parsedCourses.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_no_courses_found, sourceLabel), Toast.LENGTH_LONG).show();
            return;
        }

        // 显示解析结果预览 + 调试信息
        StringBuilder preview = new StringBuilder();
        preview.append(getString(R.string.main_preview_stats)).append('\n');
        preview.append(getString(R.string.main_preview_parsed, parsedCourses.size())).append("\n\n");

        preview.append(getString(R.string.main_preview_days)).append('\n');
        int[] perDay = new int[7];
        for (Course c : parsedCourses) {
            perDay[c.day]++;
        }
        for (int i = 0; i < 7; i++) {
            if (perDay[i] > 0) {
                preview.append(getString(R.string.main_preview_per_day, dayNames[i], perDay[i]));
            }
        }
        preview.append("\n\n");

        preview.append(getString(R.string.main_preview_periods)).append('\n');
        int[] perPeriod = new int[periodCount() + 1];
        for (Course c : parsedCourses) {
            for (int p = c.period; p <= c.endPeriod; p++) {
                if (p >= 1 && p <= periodCount()) perPeriod[p]++;
            }
        }
        for (int i = 1; i <= periodCount(); i++) {
            if (perPeriod[i] > 0) {
                preview.append(getString(R.string.main_preview_per_period, i, perPeriod[i]));
                if (i % 4 == 0) preview.append("\n");
            }
        }
        preview.append("\n\n");

        preview.append(getString(R.string.main_preview_courses)).append('\n');
        for (int i = 0; i < Math.min(5, parsedCourses.size()); i++) {
            Course c = parsedCourses.get(i);
            preview.append(getString(R.string.main_preview_item,
                i+1, c.name, dayNames[c.day], c.period, c.endPeriod, c.weeks));
        }
        if (parsedCourses.size() > 5) {
            preview.append(getString(R.string.main_preview_more, parsedCourses.size() - 5));
        }

        AppFonts.install(new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.main_import_result_title))
            .setMessage(preview.toString())
            .setPositiveButton(getString(R.string.main_confirm_import), (dialog, which) -> {
                int imported = 0;
                int updated = 0;
                int skipped = 0;
                long baseId = System.currentTimeMillis();
                StringBuilder skipLog = new StringBuilder();
                List<Course> candidate = copyCourses();

                for (Course parsed : parsedCourses) {
                    if (candidate.size() >= MAX_COURSES) {
                        skipped++;
                        skipLog.append(getString(R.string.main_skip_limit, parsed.name)).append('\n');
                        continue;
                    }
                    // 同名、同星期、同节次、同周次视为同一门课：
                    // 老师永久换教室等变化时原地更新已有记录，
                    // 避免同一时段产生两条重复课程，把后续手动编辑卡死在冲突检查上。
                    Course existing = findImportedCourseMatch(candidate, parsed);
                    if (existing != null) {
                        if (sameCourseText(existing.room, parsed.room)
                                && sameCourseText(existing.teacher, parsed.teacher)) {
                            skipped++;
                            skipLog.append(getString(R.string.main_skip_exists, parsed.name)).append('\n');
                        } else {
                            existing.room = parsed.room;
                            existing.teacher = parsed.teacher;
                            updated++;
                        }
                        continue;
                    }
                    parsed.id = baseId + imported + updated + skipped;
                    Integer sharedColor = CourseColorRules.findColor(candidate, parsed.name);
                    parsed.color = sharedColor != null ? sharedColor
                            : chooseCourseColorForDay(candidate, parsed.day, parsed.id);
                    candidate.add(parsed);
                    imported++;
                }

                final boolean importedChanges = imported > 0 || updated > 0;
                if (importedChanges) {
                    if (!saveCourses(candidate)) {
                        return;
                    }
                    selectedDay = parsedCourses.get(0).day;
                    viewingWeek = 0;
                    pastelBrowseDate = 0;
                    render();
                }

                String result = getString(R.string.main_import_result_imported, imported)
                        + (updated > 0 ? getString(R.string.main_import_result_updated, updated) : "")
                        + getString(R.string.main_import_result_skipped, skipped);
                if (skipped > 0 && skipLog.length() > 0) {
                    AppFonts.install(new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(result)
                        .setMessage(getString(R.string.main_skip_reasons, skipLog.toString()))
                        .setPositiveButton(getString(R.string.action_ok), null)
                        .setOnDismissListener(dismissed -> {
                            if (importedChanges) maybePromptCalendarSetup();
                        })
                        .show());
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                    if (importedChanges) maybePromptCalendarSetup();
                }
            })
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show());
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws IOException, ImportException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new ImportException(getString(R.string.main_pdf_unreadable));
            }
            return readWithLimit(inputStream, maxBytes, getString(R.string.main_pdf_too_large));
        }
    }

    private byte[] readWithLimit(InputStream inputStream, int maxBytes, String limitMessage) throws IOException, ImportException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) {
                throw new ImportException(limitMessage);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }






























    private void renderColorChoices(LinearLayout row, int[] selectedColor) {
        renderColorChoices(row, selectedColor, null);
    }

    private void renderColorChoices(LinearLayout row, int[] selectedColor, Runnable onSelect) {
        row.removeAllViews();
        for (int colorIndex = 0; colorIndex < COURSE_COLORS.length; colorIndex++) {
            int color = COURSE_COLORS[colorIndex];
            TextView swatch = new TextView(this);
            boolean selected = color == selectedColor[0];
            swatch.setText(selected ? "\u2713" : "");
            swatch.setTextColor(CoursePalette.readableInk(color));
            swatch.setTextSize(18);
            swatch.setTypeface(appTypeface(Typeface.BOLD));
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(swatchStateListBackground(color, selected));
            swatch.setContentDescription(courseColorNames[colorIndex] + (selected ? getString(R.string.main_selected_short_suffix) : ""));
            swatch.setOnClickListener(view -> {
                selectedColor[0] = color;
                if (onSelect != null) {
                    onSelect.run();
                }
                renderColorChoices(row, selectedColor, onSelect);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
            params.setMargins(0, 0, dp(7), 0);
            row.addView(swatch, params);
        }
    }

    private int chooseCourseColorForDay(int day, long ignoredCourseId) {
        return chooseCourseColorForDay(courses, day, ignoredCourseId);
    }

    private int chooseCourseColorForDay(List<Course> source, int day, long ignoredCourseId) {
        List<Integer> usedColors = new ArrayList<>();
        for (Course course : source) {
            if (course.temporary || course.id == ignoredCourseId || course.day != day) {
                continue;
            }
            usedColors.add(safeColor(course.color));
        }
        return bestCourseColor(usedColors);
    }

    private int previewCourseColorForDay(List<Course> parsed, int day) {
        List<Integer> usedColors = new ArrayList<>();
        for (Course course : parsed) {
            if (course.day == day) {
                usedColors.add(safeColor(course.color));
            }
        }
        return bestCourseColor(usedColors);
    }

    private int bestCourseColor(List<Integer> usedColors) {
        if (usedColors.isEmpty()) {
            return COURSE_COLORS[0];
        }
        int bestColor = COURSE_COLORS[0];
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0; i < COURSE_COLORS.length; i++) {
            int candidate = COURSE_COLORS[i];
            int exactCount = 0;
            float minDistance = Float.MAX_VALUE;
            for (int used : usedColors) {
                if (used == candidate) {
                    exactCount++;
                }
                minDistance = Math.min(minDistance, courseColorDistance(candidate, used));
            }
            float score = minDistance - exactCount * 220f - i * 0.01f;
            if (score > bestScore) {
                bestScore = score;
                bestColor = candidate;
            }
        }
        return bestColor;
    }

    private float courseColorDistance(int left, int right) {
        float[] leftHsv = new float[3];
        float[] rightHsv = new float[3];
        Color.colorToHSV(left, leftHsv);
        Color.colorToHSV(right, rightHsv);
        float hueDelta = Math.abs(leftHsv[0] - rightHsv[0]);
        hueDelta = Math.min(hueDelta, 360f - hueDelta) / 180f;
        float saturationDelta = Math.abs(leftHsv[1] - rightHsv[1]);
        float valueDelta = Math.abs(leftHsv[2] - rightHsv[2]);
        return hueDelta * 120f + saturationDelta * 38f + valueDelta * 26f;
    }

    private List<Course> copyCourses() {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            result.add(course.copy());
        }
        return result;
    }

    private void upsertCourse(List<Course> candidate, Course draft, boolean colorManuallySelected) {
        Course stored = draft.copy();
        // Adding/renaming without picking a swatch inherits the target name's existing color.
        Integer shared = CourseColorRules.findColor(candidate, stored.name);
        if (!colorManuallySelected && shared != null) stored.color = shared;
        boolean replaced = false;
        for (int i = 0; i < candidate.size(); i++) {
            if (candidate.get(i).id == stored.id) {
                candidate.set(i, stored);
                replaced = true;
                break;
            }
        }
        if (!replaced) candidate.add(stored);
        // Apply on the candidate copies only: failed storage must not recolor live courses.
        CourseColorRules.applyColor(candidate, stored.name, stored.color);
    }

    private Course findCourseConflict(Course draft) {
        for (Course course : courses) {
            if (course.temporary || course.id == draft.id || course.day != draft.day) {
                continue;
            }
            if (periodsOverlap(course, draft) && weeksOverlap(course.weeks, draft.weeks)) {
                return course;
            }
        }
        return null;
    }

    private boolean isScheduleChanged(Course draft, Course editing) {
        if (editing == null) {
            return true;
        }
        if (draft.day != editing.day
                || draft.period != editing.period
                || draft.endPeriod != editing.endPeriod) {
            return true;
        }
        String draftWeeks = draft.weeks == null ? "" : draft.weeks;
        String editingWeeks = editing.weeks == null ? "" : editing.weeks;
        if (draftWeeks.equals(editingWeeks)) {
            return false;
        }
        // 文本不同但语义相同（如"1-8周"与"1-8"）也视为未改动
        return !TimetableRules.sameWeekExpression(draftWeeks, editingWeeks);
    }

    private String courseConflictMessage(Course conflict) {
        String weeks = conflict.weeks == null ? "" : conflict.weeks.trim();
        StringBuilder builder = new StringBuilder(getString(R.string.main_conflict_with, conflict.name))
                .append(dayNames[conflict.day])
                .append(' ')
                .append(conflict.periodLabel());
        if (!weeks.isEmpty()) {
            builder.append(' ').append(weeks);
        }
        return builder.toString();
    }

    private Course findImportedCourseMatch(List<Course> source, Course draft) {
        for (Course course : source) {
            if (course.day == draft.day
                    && course.period == draft.period
                    && course.endPeriod == draft.endPeriod
                    && sameCourseText(course.name, draft.name)
                    && TimetableRules.sameWeekExpression(course.weeks, draft.weeks)) {
                return course;
            }
        }
        return null;
    }

    private boolean sameCourseText(String left, String right) {
        return TimetableRules.normalizedCourseText(cleanField(left == null ? "" : left))
                .equals(TimetableRules.normalizedCourseText(cleanField(right == null ? "" : right)));
    }

    private boolean periodsOverlap(Course left, Course right) {
        return left.period <= right.endPeriod && right.period <= left.endPeriod;
    }

    private boolean weeksOverlap(String left, String right) {
        return TimetableRules.weeksOverlap(left, right);
    }

    private boolean[] parseWeekSet(String value) {
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(value);
        if (!result.valid) {
            return new boolean[TimetableRules.MAX_WEEK + 1];
        }
        return result.explicit ? result.weeks : null;
    }

    private boolean courseOccursInTeachingWeek(Course course, int week) {
        if (course.temporary) {
            return true;
        }
        if (week <= 0 || week > calendarTotalWeeks()) {
            return false;
        }
        long courseDate = courseDateMillisForWeek(course.day, week);
        if (courseDate < calendarStartMillis() || courseDate > calendarEndMillis()) {
            return false;
        }
        if (course.invalidWeeks) {
            return true;
        }
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(course.weeks);
        if (!result.valid) {
            return false;
        }
        return !result.explicit || (week < result.weeks.length && result.weeks[week]);
    }

    private int currentTeachingWeek() {
        long start = calendarStartMillis();
        long today = startOfTodayMillis();
        if (today > calendarEndMillis()) {
            return calendarTotalWeeks() + 1;
        }
        // 开学日所在的周一到周日都算第1周；更早的日子返回0（未开学）
        return Math.max(0, AgendaDates.week(start, today));
    }

    private int calendarTotalWeeks() {
        long start = calendarStartMillis();
        long end = calendarEndMillis();
        if (end < start) {
            return 20;
        }
        return Math.max(1, AgendaDates.week(start, end));
    }

    private long calendarStartMillis() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(CALENDAR_START_KEY, dateMillis(2026, Calendar.MARCH, 2));
    }

    private long calendarEndMillis() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(CALENDAR_END_KEY, dateMillis(2026, Calendar.JULY, 19));
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long dateMillis(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(millis));
    }

    private Long parseDateMillis(String value) {
        if (value == null) {
            return null;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            format.setLenient(false);
            java.util.Date date = format.parse(value.trim());
            return date == null ? null : startOfDayMillis(date.getTime());
        } catch (Exception ignored) {
            return null;
        }
    }

    private long startOfDayMillis(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long courseDateMillisForWeek(int schoolDay, int teachingWeek) {
        return TimetableRules.courseDateMillisForWeek(
                calendarStartMillis(),
                teachingWeek,
                schoolDay
        );
    }

    private long timeOnDateMillis(long dateMillis, int minutes) {
        if (minutes < 0) {
            return Long.MAX_VALUE;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private SummaryCourse nextUpcomingCourseFromToday() {
        long now = System.currentTimeMillis();
        Course next = null;
        long nextStartAt = Long.MAX_VALUE;
        int totalWeeks = Math.min(calendarTotalWeeks(), TimetableRules.MAX_WEEK);
        for (Course course : courses) {
            if (course.invalidWeeks) {
                continue;
            }
            if (course.temporary) {
                if (course.day != currentSchoolDay()) {
                    continue;
                }
                long startAt = timeOnDateMillis(startOfTodayMillis(), courseStartMinutes(course));
                if (startAt > now && startAt < nextStartAt) {
                    next = course;
                    nextStartAt = startAt;
                }
                continue;
            }
            for (int week = 1; week <= totalWeeks; week++) {
                if (!courseOccursInTeachingWeek(course, week)) {
                    continue;
                }
                long startAt = timeOnDateMillis(
                        courseDateMillisForWeek(course.day, week),
                        courseStartMinutes(course)
                );
                if (startAt > now && startAt < nextStartAt) {
                    next = course;
                    nextStartAt = startAt;
                }
            }
        }
        return next == null ? null : new SummaryCourse(next, next.day, nextStartAt);
    }

    private Course currentCourseInProgress() {
        Course current = null;
        for (Course course : courses) {
            if (courseTemporalState(course) != TimetableRules.TemporalState.IN_PROGRESS) {
                continue;
            }
            if (current == null || compareCourseTime(course, current) < 0) {
                current = course;
            }
        }
        return current;
    }

    private int compareCourseTime(Course left, Course right) {
        int leftStart = courseStartSeconds(left);
        int rightStart = courseStartSeconds(right);
        if (leftStart != rightStart) {
            return Integer.compare(leftStart, rightStart);
        }
        int leftEnd = courseEndSeconds(left);
        int rightEnd = courseEndSeconds(right);
        if (leftEnd != rightEnd) {
            return Integer.compare(leftEnd, rightEnd);
        }
        String leftName = left.name == null ? "" : left.name;
        String rightName = right.name == null ? "" : right.name;
        return leftName.compareTo(rightName);
    }

    private String courseDetailText(Course course) {
        String secondary = course.secondaryText();
        if (secondary == null || secondary.isEmpty()) {
            return periodRangeTime(course);
        }
        return periodRangeTime(course) + " · " + secondary;
    }

    private String periodRangeTime(Course course) {
        if (course.customStartMinutes >= 0 && course.customEndMinutes >= 0) {
            return clockText(course.customStartMinutes) + "-" + clockText(course.customEndMinutes);
        }
        int startIndex = clamp(course.period, 1, periodCount()) - 1;
        int endIndex = clamp(course.endPeriod, 1, periodCount()) - 1;
        String[] startParts = periodTimes.get(startIndex).split("-");
        String[] endParts = periodTimes.get(endIndex).split("-");
        if (startParts.length == 2 && endParts.length == 2) {
            return startParts[0] + "-" + endParts[1];
        }
        return periodTime(course.period);
    }

    private String periodTime(int period) {
        int index = period - 1;
        if (index < 0 || index >= periodCount()) {
            return getString(R.string.main_time_not_set);
        }
        return periodTimes.get(index);
    }

    private int currentSchoolDay() {
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SUNDAY ? 6 : dayOfWeek - Calendar.MONDAY;
    }

    private String selectedDayDateLabel() {
        Calendar calendar = Calendar.getInstance();
        if (viewingWeek != 0) {
            calendar.setTimeInMillis(courseDateMillisForWeek(selectedDay, clamp(viewingWeek, 1, calendarTotalWeeks())));
        } else {
            int currentWeek = currentTeachingWeek();
            if (currentWeek >= 1 && currentWeek <= calendarTotalWeeks()) {
                calendar.setTimeInMillis(courseDateMillisForWeek(selectedDay, currentWeek));
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, selectedDay - currentSchoolDay());
            }
        }
        return new SimpleDateFormat("MMMM d", Locale.ENGLISH).format(calendar.getTime());
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSpinnerText(view, false);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSpinnerText(view, true);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(52));
        spinner.setPopupBackgroundDrawable(elevatedCardBackground(cardColor(), dp(14)));
        return spinner;
    }

    private void styleSpinnerText(View view, boolean dropdown) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView text = (TextView) view;
        text.setTextColor(primaryTextColor());
        text.setTextSize(dropdown ? 15 : 16);
        text.setTypeface(appTypeface(Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMinHeight(dp(dropdown ? 46 : 52));
        text.setPadding(dp(14), 0, dp(dropdown ? 14 : 42), 0);
        text.setBackground(dropdown
                ? buttonBackground(cardColor(), 0)
                : elevatedCardBackground(cardColor(), dp(14)));
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTypeface(appTypeface(Typeface.BOLD));
        label.setLetterSpacing(0.05f);
        label.setTextColor(secondaryTextColor());
        label.setPadding(0, dp(10), 0, dp(5));
        return label;
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setSingleLine(true);
        editText.setTextSize(16);
        editText.setTextColor(primaryTextColor());
        editText.setHintTextColor(secondaryTextColor());
        editText.setPadding(dp(14), dp(12), dp(14), dp(12));
        editText.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_FIELD_LENGTH)});
        return editText;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(4));
        return params;
    }

    private android.graphics.drawable.Drawable interactiveSurfaceBackground(int color, int radius) {
        return rippleBackground(color, withAlpha(accentColor(), isDarkMode ? 72 : 36), radius, borderColor());
    }

    private android.graphics.drawable.Drawable interactiveButtonBackground(int color, int radius) {
        return rippleBackground(color, Color.argb(64, 255, 255, 255), radius, Color.TRANSPARENT);
    }

    private android.graphics.drawable.Drawable interactiveGlassSurfaceBackground(int color, int radius) {
        return rippleBackground(color, withAlpha(accentColor(), isDarkMode ? 72 : 36), radius, glassBorderColor());
    }

    private android.graphics.drawable.Drawable rippleBackground(int contentColor, int rippleColor, int radius, int strokeColor) {
        GradientDrawable content = roundedSurface(contentColor, radius, strokeColor);
        GradientDrawable mask = roundedSurface(Color.WHITE, radius, Color.TRANSPARENT);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                content,
                mask
        );
    }

    private GradientDrawable roundedSurface(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable glassPanelBackground(int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{glassPanelTopColor(), glassPanelBottomColor()}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(2), glassOuterEdgeColor());
        return drawable;
    }

    private GradientDrawable buttonBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable colorSwatchBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(selected ? 3 : 1), selected ? Color.WHITE : Color.argb(70, 255, 255, 255));
        return drawable;
    }

    private android.graphics.drawable.StateListDrawable swatchStateListBackground(int color, boolean selected) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, colorSwatchBackground(pressedButtonColor(color), selected));
        states.addState(new int[]{}, colorSwatchBackground(color, selected));
        return states;
    }

    private android.graphics.drawable.StateListDrawable interactiveTranslucentBackground(int color, int radius) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int alpha = Color.alpha(color);
        int pressed = Color.argb(Math.min(255, alpha + 32),
                Math.round(Color.red(color) * 0.88f),
                Math.round(Color.green(color) * 0.88f),
                Math.round(Color.blue(color) * 0.88f));
        states.addState(new int[]{android.R.attr.state_pressed}, buttonBackground(pressed, radius));
        states.addState(new int[]{}, buttonBackground(color, radius));
        return states;
    }

    private GradientDrawable cardBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), borderColor());
        return drawable;
    }

    private GradientDrawable completedCourseFilterBackground(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode
                ? Color.argb(102, 44, 48, 56)
                : Color.argb(92, 156, 163, 174));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable elevatedCardBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), borderColor());
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // 在支持的设备上添加轻微阴影效果
            drawable.setStroke(dp(1), borderColor());
        }
        return drawable;
    }

    private GradientDrawable elevatedButtonBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int bgColor() {
        return isDarkMode ? Color.rgb(15, 18, 23) : Color.rgb(245, 248, 252);
    }

    private int cardColor() {
        return isDarkMode ? Color.rgb(27, 31, 38) : Color.rgb(255, 255, 255);
    }

    private int segmentedTrackColor() {
        return isDarkMode ? Color.rgb(22, 26, 32) : Color.rgb(236, 242, 249);
    }

    private int pressedButtonColor(int color) {
        return mixColor(color, Color.BLACK, isDarkMode ? 0.12f : 0.08f);
    }

    private int primaryTextColor() {
        return isDarkMode ? Color.rgb(243, 246, 250) : Color.rgb(18, 24, 33);
    }

    private int secondaryTextColor() {
        return isDarkMode ? Color.rgb(164, 173, 184) : Color.rgb(91, 101, 114);
    }

    private int borderColor() {
        return isDarkMode ? Color.rgb(51, 59, 70) : Color.rgb(221, 228, 238);
    }

    private int accentColor() {
        return ACCENT_PRESETS[accentPresetIndex()][isDarkMode ? 1 : 0];
    }

    private PastelAccent pastelAccent() {
        return PastelAccent.of(accentColor(), isDarkMode);
    }

    private int todayColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(accentColor(), hsv);
        hsv[0] = (hsv[0] + 42f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] + 0.05f);
        hsv[2] = isDarkMode ? Math.min(1f, hsv[2] + 0.22f) : Math.max(0.14f, hsv[2] - 0.10f);
        return Color.HSVToColor(hsv);
    }

    private int accentPresetIndex() {
        int saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(ACCENT_KEY, DEFAULT_ACCENT_PRESET);
        if (saved < 0 || saved >= ACCENT_PRESETS.length) {
            return DEFAULT_ACCENT_PRESET;
        }
        return saved;
    }

    static int accentColorFor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(ACCENT_KEY, DEFAULT_ACCENT_PRESET);
        int index = (saved >= 0 && saved < ACCENT_PRESETS.length) ? saved : DEFAULT_ACCENT_PRESET;
        boolean dark = isDarkModeFor(context, prefs);
        return ACCENT_PRESETS[index][dark ? 1 : 0];
    }

    private static boolean isDarkModeFor(Context context, SharedPreferences prefs) {
        int mode = prefs.getInt(THEME_KEY, 0);
        if (mode == 2) {
            return true;
        }
        if (mode == 1) {
            return false;
        }
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private int accentContainerColor() {
        int accent = accentColor();
        return Color.argb(isDarkMode ? 46 : 26, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    private int tonalContainerColor() {
        return mixColor(accentColor(), cardColor(), 0.90f);
    }

    private void loadCourses() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String encrypted = prefs.getString(COURSES_KEY, null);
        String raw;
        boolean migrateLegacy = false;
        boolean encryptedSource = encrypted != null;
        try {
            if (encryptedSource) {
                raw = secureStorage.decrypt(encrypted);
            } else {
                raw = prefs.getString(LEGACY_COURSES_KEY, "[]");
                migrateLegacy = !raw.equals("[]");
            }
        } catch (Exception error) {
            courses.clear();
            storageLocked = true;
            Toast.makeText(this, getString(R.string.main_courses_decrypt_failed), Toast.LENGTH_LONG).show();
            return;
        }

        courses.clear();
        try {
            JSONArray array = new JSONArray(raw);
            int invalidWeekCount = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Course course = new Course();
                course.id = object.optLong("id", System.currentTimeMillis() + i);
                course.day = clamp(object.optInt("day", 0), 0, dayNames.length - 1);
                course.period = clamp(object.optInt("period", 1), 1, periodCount());
                course.endPeriod = clamp(object.optInt("endPeriod", course.period), course.period, periodCount());
                course.name = cleanField(object.optString("name", ""));
                course.weeks = cleanField(object.optString("weeks", ""));
                course.invalidWeeks = !TimetableRules.parseWeeks(course.weeks).valid;
                if (course.invalidWeeks) {
                    invalidWeekCount++;
                }
                course.room = cleanField(object.optString("room", ""));
                course.teacher = cleanField(object.optString("teacher", ""));
                course.color = safeColor(object.optInt("color", COURSE_COLORS[i % COURSE_COLORS.length]));
                if (!course.name.isEmpty()) {
                    courses.add(course);
                }
                if (courses.size() >= MAX_COURSES) {
                    break;
                }
            }
            if (migrateLegacy) {
                saveCourses(courses);
            }
            if (invalidWeekCount > 0) {
                Toast.makeText(
                        this,
                        getResources().getQuantityString(R.plurals.main_invalid_weeks, invalidWeekCount, invalidWeekCount),
                        Toast.LENGTH_LONG
                ).show();
            }
        } catch (Exception ignored) {
            courses.clear();
            storageLocked = encryptedSource;
            Toast.makeText(this, getString(R.string.main_courses_corrupt), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断课程数据写入成败，不能改 apply
    private boolean saveCourses() {
        return saveCourses(courses);
    }

    /** Persists {@code candidate}; publishes it as {@link #courses} only after the commit succeeds. */
    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断课程数据写入成败，不能改 apply
    private boolean saveCourses(List<Course> candidate) {
        if (storageLocked) {
            Toast.makeText(this, getString(R.string.main_locked_refuse_save), Toast.LENGTH_LONG).show();
            return false;
        }
        List<Course> normalized = new ArrayList<>();
        for (Course course : candidate) normalized.add(course.copy());
        CourseColorRules.synchronize(normalized);
        boolean saved = ListTransaction.commit(courses, normalized, this::writeCourses);
        if (saved) {
            scheduleClassReminderNotifications();
        }
        return saved;
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断课程数据写入成败，不能改 apply
    private boolean writeCourses(List<Course> snapshot) {
        JSONArray array = new JSONArray();
        try {
            for (Course course : snapshot) {
                if (course.temporary) {
                    continue;
                }
                JSONObject object = new JSONObject();
                object.put("id", course.id);
                object.put("day", course.day);
                object.put("period", course.period);
                object.put("endPeriod", course.endPeriod);
                object.put("name", course.name);
                object.put("weeks", course.weeks);
                object.put("room", course.room);
                object.put("teacher", course.teacher);
                object.put("color", course.color);
                array.put(object);
            }
        } catch (Exception ignored) {
            Toast.makeText(this, getString(R.string.main_serialize_failed), Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            String encrypted = secureStorage.encrypt(array.toString());
            boolean committed = SafePreferences.commit(getSharedPreferences(PREFS_NAME, MODE_PRIVATE),
                    new PreferenceTransaction()
                            .put(COURSES_KEY, encrypted)
                            .remove(LEGACY_COURSES_KEY));
            if (!committed) {
                Toast.makeText(this, getString(R.string.main_courses_write_failed), Toast.LENGTH_LONG).show();
            }
            return committed;
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.main_courses_encrypt_failed), Toast.LENGTH_LONG).show();
            return false;
        }
    }


    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- personal events

    private void loadPersonalEvents() {
        personalEvents.clear();
        eventsLocked = false;
        String encrypted = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(EVENTS_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(secureStorage.decrypt(encrypted));
            for (int i = 0; i < array.length() && personalEvents.size() < PersonalEvent.MAX_EVENTS; i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                PersonalEvent e = new PersonalEvent();
                e.id = o.optLong("id", 0L);
                e.year = o.optInt("year", 0);
                e.month = o.optInt("month", 0);
                e.day = o.optInt("day", 0);
                e.startMinutes = o.optInt("start", -1);
                e.endMinutes = o.optInt("end", PersonalEvent.NO_END);
                e.remindMinutes = o.optInt("remind", PersonalEvent.NO_REMINDER);
                e.title = o.optString("title", "").trim();
                e.location = o.optString("location", "").trim();
                e.note = o.optString("note", "").trim();
                if (e.id != 0L && e.validate() == null) {
                    personalEvents.add(e);
                }
            }
        } catch (Exception error) {
            personalEvents.clear();
            eventsLocked = true;
            Toast.makeText(this, getString(R.string.main_events_unreadable), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("ApplySharedPref") // 依赖 commit 返回值判断日程写入成败，不能改 apply
    private boolean writePersonalEvents(List<PersonalEvent> snapshot) {
        try {
            JSONArray array = new JSONArray();
            for (PersonalEvent e : snapshot) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("year", e.year);
                o.put("month", e.month);
                o.put("day", e.day);
                o.put("start", e.startMinutes);
                o.put("end", e.endMinutes);
                o.put("remind", e.remindMinutes);
                o.put("title", e.title);
                o.put("location", e.location);
                o.put("note", e.note);
                array.put(o);
            }
            String encrypted = secureStorage.encrypt(array.toString());
            return SafePreferences.commit(getSharedPreferences(PREFS_NAME, MODE_PRIVATE),
                    new PreferenceTransaction().put(EVENTS_KEY, encrypted));
        } catch (Exception error) {
            return false;
        }
    }

    /** Returns null on success, otherwise a message for the editor. */
    private String savePersonalEvents(List<PersonalEvent> candidate) {
        if (eventsLocked) {
            return getString(R.string.main_events_locked_save);
        }
        if (!ListTransaction.commit(personalEvents, candidate, this::writePersonalEvents)) {
            return getString(R.string.main_events_save_failed);
        }
        scheduleClassReminderNotifications();
        render();
        return null;
    }

    private PersonalEvent findPersonalEvent(long id) {
        for (PersonalEvent e : personalEvents) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    /** Only events whose date falls inside the displayed teaching week are shown in 日程. */
    private void bindPastelEvents(int week) {
        if (pastelTimetable == null) {
            return;
        }
        long[] dates = AgendaDates.weekdays(pastelDisplayedStart());
        String[] labels = new String[7];
        SimpleDateFormat format = new SimpleDateFormat(getString(R.string.main_date_pattern_month_day), uiLocale());
        for (int day = 0; day < 7; day++) {
            labels[day] = dates[day] > 0 ? format.format(new java.util.Date(dates[day])) : null;
        }
        List<PastelTimetableView.EventEntry> visible = new ArrayList<>();
        for (PersonalEvent e : personalEvents) {
            long date = e.dayMillis();
            for (int day = 0; day < 7; day++) {
                if (dates[day] == date) {
                    PastelTimetableView.EventEntry entry = new PastelTimetableView.EventEntry();
                    entry.id = e.id;
                    entry.day = day;
                    entry.startMinutes = e.startMinutes;
                    entry.endMillis = e.endMillis();
                    entry.hasEnd = e.endMinutes != PersonalEvent.NO_END;
                    entry.title = e.title;
                    entry.time = e.timeLabel();
                    entry.location = e.location;
                    visible.add(entry);
                    break;
                }
            }
        }
        pastelTimetable.setEvents(visible, labels, dates);
        if (minePage != null) minePage.setEvents(new ArrayList<>(personalEvents));
    }

    private void showPersonalEventDetail(PersonalEvent event) {
        if (eventFromMine && pastelTimetable != null) pastelTimetable.setMineSelected(true);
        PastelCourseSheet sheet = new PastelCourseSheet(this, isDarkMode, pastelAccent());
        activeEventDetail = sheet;
        activeEventDetailId = event.id;
        Bundle restored = pendingDetailRestore;
        pendingDetailRestore = null;
        sheet.showEventDetail(event, System.currentTimeMillis(), () -> {
            activeEventDetail = null;
            activeEventDetailId = 0;
            PersonalEvent latest = findPersonalEvent(event.id);
            if (latest != null) showPersonalEventEditor(latest);
            else if (eventFromMine) showMineSheet();
        }, () -> {
            if (activeEventDetail == sheet) {
                activeEventDetail = null;
                activeEventDetailId = 0;
            }
            if (eventFromMine && !isChangingConfigurations() && !isFinishing() && !isDestroyed()) showMineSheet();
        }, restored);
    }

    private void showPersonalEventEditor(PersonalEvent editing) {
        if (eventFromMine && pastelTimetable != null) pastelTimetable.setMineSelected(true);
        if (eventsLocked) {
            Toast.makeText(this, getString(R.string.main_events_locked_edit), Toast.LENGTH_LONG).show();
            return;
        }
        PersonalEvent draft;
        if (editing != null) {
            draft = editing.copy();
        } else {
            draft = new PersonalEvent();
            Calendar now = Calendar.getInstance();
            draft.year = now.get(Calendar.YEAR);
            draft.month = now.get(Calendar.MONTH) + 1;
            draft.day = now.get(Calendar.DAY_OF_MONTH);
            // Next whole hour; past 23:00 suggest 09:00 tomorrow.
            int hour = now.get(Calendar.HOUR_OF_DAY) + 1;
            if (hour > 23) {
                now.add(Calendar.DAY_OF_YEAR, 1);
                draft.year = now.get(Calendar.YEAR);
                draft.month = now.get(Calendar.MONTH) + 1;
                draft.day = now.get(Calendar.DAY_OF_MONTH);
                hour = 9;
            }
            draft.startMinutes = hour * 60;
            draft.remindMinutes = 10;
            if (!eventFromMine && pastelTimetable != null && pastelBrowseDate != 0) {
                Calendar shown = Calendar.getInstance();
                shown.setTimeInMillis(AgendaDates.weekdays(pastelDisplayedStart())[currentSchoolDay()]);
                draft.year = shown.get(Calendar.YEAR);
                draft.month = shown.get(Calendar.MONTH) + 1;
                draft.day = shown.get(Calendar.DAY_OF_MONTH);
            }
        }
        PastelCourseSheet sheet = new PastelCourseSheet(this, isDarkMode, pastelAccent());
        activeEventEditor = sheet;
        activeEventId = editing == null ? 0 : editing.id;
        Bundle restored = pendingEventRestore;
        pendingEventRestore = null;
        sheet.showEventEditor(editing != null, draft, candidate -> {
            List<PersonalEvent> next = new ArrayList<>(personalEvents);
            if (editing == null) {
                if (personalEvents.size() >= PersonalEvent.MAX_EVENTS) {
                    return getString(R.string.main_event_limit, PersonalEvent.MAX_EVENTS);
                }
                long id = System.currentTimeMillis();
                while (id == 0L || findPersonalEvent(id) != null) {
                    id++;
                }
                candidate.id = id;
                next.add(candidate);
            } else {
                boolean replaced = false;
                for (int i = 0; i < next.size(); i++) {
                    if (next.get(i).id == editing.id) {
                        candidate.id = editing.id;
                        next.set(i, candidate);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    return getString(R.string.main_event_deleted_already);
                }
            }
            String error = savePersonalEvents(next);
            if (error == null) {
                Toast.makeText(this, editing == null ? getString(R.string.main_event_added) : getString(R.string.main_event_saved), Toast.LENGTH_SHORT).show();
                if (pastelTimetable != null && !eventFromMine) {
                    pastelTimetable.showAgenda();
                    browsePastelDate(candidate.dayMillis());
                }
            }
            return error;
        }, editing == null ? null : () -> {
            List<PersonalEvent> next = new ArrayList<>(personalEvents);
            for (java.util.Iterator<PersonalEvent> it = next.iterator(); it.hasNext(); ) {
                if (it.next().id == editing.id) {
                    it.remove();
                }
            }
            String error = savePersonalEvents(next);
            Toast.makeText(this, error == null ? getString(R.string.main_event_deleted) : error, Toast.LENGTH_SHORT).show();
            return error == null;
        }, restored, () -> {
            activeEventEditor = null;
            activeEventId = 0;
            if (eventFromMine && !isChangingConfigurations() && !isFinishing()) showMineSheet();
        });
    }

    private void showMineSheet() {
        if (pastelTimetable == null) return;
        pastelTimetable.setMineSelected(true);
        if (minePage != null) minePage.setEvents(new ArrayList<>(personalEvents));
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_FIELD_LENGTH ? trimmed : trimmed.substring(0, MAX_FIELD_LENGTH);
    }

    private int safeColor(int color) {
        for (int allowed : COURSE_COLORS) {
            if (allowed == color) {
                return color;
            }
        }
        int closest = COURSE_COLORS[0];
        float bestDistance = Float.MAX_VALUE;
        for (int allowed : COURSE_COLORS) {
            float distance = rgbDistance(color, allowed);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = allowed;
            }
        }
        return closest;
    }

    private float rgbDistance(int left, int right) {
        int red = Color.red(left) - Color.red(right);
        int green = Color.green(left) - Color.green(right);
        int blue = Color.blue(left) - Color.blue(right);
        return red * red + green * green + blue * blue;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean animationsEnabled() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return android.animation.ValueAnimator.areAnimatorsEnabled();
        }
        try {
            return android.provider.Settings.Global.getFloat(
                    getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            ) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isCompactWidth() {
        return getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density < 430f;
    }

    private int contentSideMarginDp() {
        return isCompactWidth() ? 12 : CONTENT_SIDE_MARGIN_DP;
    }

    private Typeface appTypeface(int style) {
        // System preset: the platform font stack, so the UI follows the system font setting.
        return AppFonts.typeface(this, style);
    }

    private interface PositionCallback {
        void onSelected(int position);
    }

    private static class SimpleSelectedListener implements AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;

        SimpleSelectedListener(PositionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private class CountdownRow extends LinearLayout {
        private final Course course;
        private final boolean compact;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        };
        private TextView plainView;
        private TextView prefixView;
        private TextView suffixView;
        private FrameLayout numberSlot;
        private String lastPhase = "";
        private String lastSecondsText;

        CountdownRow(Course course, boolean compact) {
            super(MainActivity.this);
            this.course = course;
            this.compact = compact;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBaselineAligned(false);
            setClipChildren(true);
            setClipToPadding(true);
            refresh();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refresh();
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            cancelChildViewAnimations(this);
            super.onDetachedFromWindow();
        }

        private void cancelChildViewAnimations(View view) {
            view.animate().cancel();
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    cancelChildViewAnimations(group.getChildAt(i));
                }
            }
        }

        private void refresh() {
            CountdownState state = remainingCountdownState(course);
            if (state.showSeconds) {
                showSecondsState(state);
            } else {
                showPlainState(state.text);
            }
            scheduleNextTick(state.showSeconds);
        }

        private void scheduleNextTick(boolean enabled) {
            removeCallbacks(tick);
            if (!enabled || !activityResumed || !isAttachedToWindow()) {
                return;
            }
            long delay = COUNTDOWN_REFRESH_MS - (System.currentTimeMillis() % COUNTDOWN_REFRESH_MS) + 36L;
            postDelayed(tick, delay);
        }

        private void pauseTicks() {
            removeCallbacks(tick);
            cancelChildViewAnimations(this);
        }

        private void showPlainState(String text) {
            lastSecondsText = null;
            if (plainView == null || getChildCount() != 1) {
                removeAllViews();
                prefixView = null;
                suffixView = null;
                numberSlot = null;
                plainView = createCountdownTextView(compact);
                addView(plainView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            plainView.setText(text);
        }

        private void showSecondsState(CountdownState state) {
            if (numberSlot == null || plainView != null || !TextUtils.equals(lastPhase, state.phase)) {
                rebuildSecondsLayout();
                lastPhase = state.phase;
            }
            prefixView.setText(state.prefix);
            suffixView.setText(state.suffix);

            String previous = lastSecondsText;
            String key = countdownKey(course, state.phase);
            CountdownSnapshot snapshot = previous == null ? recentCountdownSnapshot(key) : null;
            if (snapshot != null) {
                previous = snapshot.secondsText;
            }
            boolean animate = animationsEnabled()
                    && previous != null
                    && !TextUtils.equals(previous, state.secondsText);
            setNumberText(previous, state.secondsText, animate);
            lastSecondsText = state.secondsText;
            rememberCountdownSnapshot(key, state.secondsText);
        }

        private void rebuildSecondsLayout() {
            removeAllViews();
            plainView = null;
            prefixView = createCountdownTextView(compact);
            suffixView = createCountdownTextView(compact);
            numberSlot = new FrameLayout(MainActivity.this);
            numberSlot.setClipChildren(true);
            numberSlot.setClipToPadding(true);

            addView(prefixView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            addView(numberSlot, new LinearLayout.LayoutParams(
                    dp(compact ? 21 : 23),
                    dp(compact ? 20 : 22)
            ));
            addView(suffixView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        private void setNumberText(String previous, String current, boolean animate) {
            numberSlot.removeAllViews();
            LinearLayout digits = new LinearLayout(MainActivity.this);
            digits.setOrientation(LinearLayout.HORIZONTAL);
            digits.setGravity(Gravity.CENTER);
            digits.setClipChildren(true);
            digits.setClipToPadding(true);
            numberSlot.addView(digits, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            for (int i = 0; i < current.length(); i++) {
                FrameLayout digitSlot = new FrameLayout(MainActivity.this);
                digitSlot.setClipChildren(true);
                digitSlot.setClipToPadding(true);
                digits.addView(digitSlot, new LinearLayout.LayoutParams(
                        dp(compact ? 9 : 10),
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

                String previousDigit = previous != null && previous.length() == current.length()
                        ? String.valueOf(previous.charAt(i))
                        : null;
                String currentDigit = String.valueOf(current.charAt(i));
                boolean digitChanged = animate
                        && previousDigit != null
                        && !TextUtils.equals(previousDigit, currentDigit);
                setDigitText(digitSlot, previousDigit, currentDigit, digitChanged);
            }
        }

        private void setDigitText(FrameLayout digitSlot, String previous, String current, boolean animate) {
            TextView currentView = createCountdownNumberView();
            currentView.setText(current);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            );

            if (!animate || previous == null) {
                digitSlot.addView(currentView, params);
                return;
            }

            TextView previousView = createCountdownNumberView();
            previousView.setText(previous);
            digitSlot.addView(previousView, params);

            currentView.setTranslationY(dp(compact ? 15 : 17));
            currentView.setAlpha(0.55f);
            currentView.setScaleY(0.96f);
            digitSlot.addView(currentView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            previousView.animate()
                    .translationY(-dp(compact ? 13 : 15))
                    .alpha(0f)
                    .scaleY(0.96f)
                    .setDuration(COUNTDOWN_ANIMATION_MS - 40L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
                    .withEndAction(() -> digitSlot.removeView(previousView))
                    .start();
            currentView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleY(1f)
                    .setDuration(COUNTDOWN_ANIMATION_MS)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                    .start();
        }

        private TextView createCountdownNumberView() {
            TextView text = createCountdownTextView(compact);
            text.setGravity(Gravity.CENTER);
            text.setIncludeFontPadding(false);
            return text;
        }
    }

    private static class CountdownState {
        final boolean showSeconds;
        final String text;
        final String prefix;
        final String secondsText;
        final String suffix;
        final String phase;
        final int remainingSeconds;

        private CountdownState(
                boolean showSeconds,
                String text,
                String prefix,
                String secondsText,
                String suffix,
                String phase,
                int remainingSeconds
        ) {
            this.showSeconds = showSeconds;
            this.text = text;
            this.prefix = prefix;
            this.secondsText = secondsText;
            this.suffix = suffix;
            this.phase = phase;
            this.remainingSeconds = remainingSeconds;
        }

        static CountdownState plain(String text) {
            return new CountdownState(false, text, "", "", "", "", -1);
        }

        static CountdownState seconds(String prefix, String secondsText, String suffix, String phase, int remainingSeconds) {
            return new CountdownState(true, "", prefix, secondsText, suffix, phase, remainingSeconds);
        }
    }

    private static class CountdownSnapshot {
        final String secondsText;
        final long updatedAtMillis;

        CountdownSnapshot(String secondsText, long updatedAtMillis) {
            this.secondsText = secondsText;
            this.updatedAtMillis = updatedAtMillis;
        }
    }

    private static class SummaryCourse {
        final Course course;
        final int day;
        final long startAtMillis;

        SummaryCourse(Course course, int day, long startAtMillis) {
            this.course = course;
            this.day = day;
            this.startAtMillis = startAtMillis;
        }
    }

    private final class CourseProgressView extends View {
        private final Course course;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        CourseProgressView(Course course) {
            super(MainActivity.this);
            this.course = course;
            paint.setColor(Color.argb(72, 255, 255, 255));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float progress = Math.max(0f, Math.min(1f, courseProgress(course)));
            if (progress > 0f) {
                canvas.drawRect(0f, 0f, getWidth() * progress, getHeight(), paint);
            }
        }
    }

    private static class BoundedMaterialCardView extends MaterialCardView {
        private int maxHeightPx;

        BoundedMaterialCardView(android.content.Context context) {
            super(context);
        }

        void setMaxHeightPx(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (maxHeightPx > 0 && getMeasuredHeight() > maxHeightPx) {
                int cappedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.EXACTLY);
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        }
    }

    private static class Course implements CourseColorRules.Item {
        long id;
        int day;
        int period;
        int endPeriod = 1;
        String name = "";
        String weeks = "";
        String room = "";
        String teacher = "";
        int color = COURSE_COLORS[0];
        boolean temporary = false;
        boolean invalidWeeks = false;
        int customStartMinutes = -1;
        int customEndMinutes = -1;

        @Override public String colorName() { return name; }
        @Override public int colorValue() { return color; }
        @Override public void setColorValue(int value) { color = value; }
        @Override public boolean temporaryColor() { return temporary; }

        String periodLabel() {
            if (endPeriod > period) {
                return UiLang.t("第" + period + "-" + endPeriod + "节", "第" + period + "-" + endPeriod + "節",
                        "Periods " + period + "–" + endPeriod);
            }
            return UiLang.t("第" + period + "节", "第" + period + "節", "Period " + period);
        }

        String periodBadgeText() {
            if (endPeriod > period) {
                return UiLang.t("第\n" + period + "-" + endPeriod + "\n节", "第\n" + period + "-" + endPeriod + "\n節",
                        "Periods\n" + period + "–" + endPeriod);
            }
            return UiLang.t("第\n" + period + "\n节", "第\n" + period + "\n節", "Period\n" + period);
        }

        String secondaryText() {
            List<String> parts = new ArrayList<>();
            if (invalidWeeks) {
                parts.add(UiLang.t("⚠ 上课周格式需修正", "⚠ 上課週格式需修正", "⚠ Weeks need fixing"));
            }
            if (weeks != null && !weeks.isEmpty()) {
                parts.add(weeks);
            }
            if (room != null && !room.isEmpty()) {
                parts.add(room);
            }
            if (teacher != null && !teacher.isEmpty()) {
                parts.add(teacher);
            }
            return parts.isEmpty() ? UiLang.t("暂无上课周、地点和老师信息", "暫無上課週、地點和老師資訊", "No weeks, location or teacher") : join(parts, " · ");
        }

        Course copy() {
            Course copy = new Course();
            copy.id = id;
            copy.day = day;
            copy.period = period;
            copy.endPeriod = endPeriod;
            copy.name = name;
            copy.weeks = weeks;
            copy.room = room;
            copy.teacher = teacher;
            copy.color = color;
            copy.temporary = temporary;
            copy.invalidWeeks = invalidWeeks;
            copy.customStartMinutes = customStartMinutes;
            copy.customEndMinutes = customEndMinutes;
            return copy;
        }

        private static String join(List<String> parts, String separator) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    builder.append(separator);
                }
                builder.append(parts.get(i));
            }
            return builder.toString();
        }
    }
}

