package com.example.meinstundenplan;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Base64;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
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
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "timetable";
    private static final String LEGACY_COURSES_KEY = "courses";
    private static final String COURSES_KEY = "courses_encrypted_v2";
    private static final String THEME_KEY = "theme_mode";
    private static final String REMINDER_CODES_KEY = "class_reminder_codes";
    private static final String PERIOD_TIMES_KEY = "period_times_v1";
    private static final String CALENDAR_START_KEY = "calendar_start_millis";
    private static final String CALENDAR_END_KEY = "calendar_end_millis";
    private static final String CALENDAR_WEEKS_KEY = "calendar_total_weeks";
    private static final String CALENDAR_NAME_KEY = "calendar_name";
    private static final String DEFAULT_CALENDAR_URL = "https://jwc.jyu.edu.cn/info/1102/4690.htm";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mein_stundenplan_courses_key";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int MAX_FIELD_LENGTH = 60;
    private static final int MAX_COURSES = 200;
    private static final int REQUEST_IMPORT_PDF = 40;
    private static final int MAX_PDF_BYTES = 5 * 1024 * 1024;
    private static final int MAX_STREAM_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CALENDAR_BYTES = 512 * 1024;
    private static final long PROGRESS_REFRESH_MS = 5000L;
    private static final long COUNTDOWN_REFRESH_MS = 1000L;
    private static final int COUNTDOWN_SECONDS_THRESHOLD = 5 * 60;
    private static final int COUNTDOWN_ANIMATION_MS = 260;
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final long MILLIS_PER_WEEK = 7L * MILLIS_PER_DAY;
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
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
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
    private static final int[] COURSE_COLORS = {
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
    };
    private final List<Course> courses = new ArrayList<>();
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
    private FrameLayout dayTabFrame;
    private View daySelectionSlider;
    private LinearLayout dayTabs;
    private LinearLayout courseList;
    private TextView summaryText;
    private ImageButton themeToggle;
    private View settingsOverlay;
    private int selectedDay = 0;
    private boolean storageLocked = false;
    private boolean isDarkMode = false;
    private boolean activityResumed = false;
    private Runnable pendingReminderPopup;
    private String renderedTemporalSignature = "";
    private boolean animateDaySlider = false;
    private float daySwipeDownX = 0f;
    private float daySwipeDownY = 0f;
    private boolean daySwipeTracking = false;
    private int daySwipeAxis = 0; // 0=undecided, 1=horizontal, 2=vertical
    private int daySwipePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean daySwipeChildCancelled = false;
    private int themeMode = 1; // 1=浅色, 2=深色

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadThemeMode();
        loadPeriodTimes();
        configureWindow();
        selectedDay = currentSchoolDay();
        loadCourses();
        if (rebalanceCourseColorsIfNeeded()) {
            saveCourses();
        }
        removeExpiredCoursesIfNeeded(false);
        addTemporaryDynamicIslandCourseIfDebug();
        buildLayout();
        render();
        maybeShowReminderPopupFromIntent(getIntent());
        ensureNotificationPermission();
        maybeSendDebugReminderTestNotification(getIntent());
        scheduleClassReminderNotifications();
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
        removeExpiredCoursesIfNeeded(false);
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
        return !isTouchInsideDayTabs(event);
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

    private boolean handleDaySwipe(float distanceX, float distanceY) {
        if (!isDaySwipeGesture(distanceX, distanceY)) {
            return false;
        }
        int direction = distanceX < 0 ? 1 : -1;
        int targetDay = selectedDay + direction;
        if (targetDay < 0 || targetDay >= DAYS.length) {
            return false;
        }
        switchToDay(targetDay, direction);
        return true;
    }

    private boolean canSwitchDayBySwipe(float distanceX) {
        int direction = distanceX < 0 ? 1 : -1;
        int targetDay = selectedDay + direction;
        return targetDay >= 0 && targetDay < DAYS.length;
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
        int length = DAYS.length;
        return ((day % length) + length) % length;
    }

    private void loadThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedMode = prefs.getInt(THEME_KEY, 0);
        if (savedMode == 1 || savedMode == 2) {
            themeMode = savedMode;
        } else {
            themeMode = isSystemDarkMode() ? 2 : 1;
            prefs.edit().putInt(THEME_KEY, themeMode).commit();
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(THEME_KEY, themeMode).commit();
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
            Toast.makeText(this, "\u901a\u77e5\u6743\u9650\u672a\u5141\u8bb8\uff0c\u8bfe\u524d\u63d0\u9192\u4e0d\u4f1a\u5f39\u51fa", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureNotificationPermission() {
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            ClassReminderReceiver.ensureChannel(manager);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void scheduleClassReminderNotifications() {
        if (storageLocked) {
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
        ReminderScheduler.replaceSchedule(this, entries);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(REMINDER_CODES_KEY).commit();
    }

    private void cancelScheduledClassReminders(android.app.AlarmManager alarmManager) {
        String encoded = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(REMINDER_CODES_KEY, "[]");
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
        long id = course.id == 0L ? course.name.hashCode() : course.id;
        return (int) (id ^ (id >>> 32)) & 0x7fffffff;
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
        title.setText("我的课表");
        title.setTextColor(primaryTextColor());
        title.setTextSize(compact ? 28 : 32);
        title.setTypeface(dengXian(Typeface.BOLD));
        title.setLetterSpacing(0f);
        titleBlock.addView(title);

        summaryText = new TextView(this);
        summaryText.setTextColor(secondaryTextColor());
        summaryText.setTextSize(15);
        summaryText.setTypeface(dengXian(Typeface.NORMAL));
        summaryText.setAlpha(0.85f);
        summaryText.setMaxLines(2);
        summaryText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                compact ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMargins(0, dp(4), 0, 0);
        titleBlock.addView(summaryText, summaryParams);

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
        themeToggle.setImageResource(isDarkMode ? R.drawable.ic_sun_10_white : R.drawable.ic_moon_deep_blue);
        themeToggle.setImageTintList(null);
        themeToggle.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        themeToggle.setContentDescription(isDarkMode ? "切换到亮色模式" : "切换到暗黑模式");
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
        settingsButton.setContentDescription("\u8bbe\u7f6e");
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
        addButton.setContentDescription("\u6dfb\u52a0\u8bfe\u7a0b");
        addButton.setBackground(interactiveButtonBackground(accentColor(), dp(22)));
        addButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        addButton.setOnClickListener(view -> showCourseDialog(null));
        actions.addView(addButton, new LinearLayout.LayoutParams(dp(actionButtonSize), dp(actionButtonSize)));

        HorizontalScrollView dayScroll = new HorizontalScrollView(this);
        dayScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams dayScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dayScrollParams.setMargins(dp(sideMargin), dp(compact ? 18 : 24), dp(sideMargin), dp(16));
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

    private void render() {
        renderDayTabs();
        renderCourseList();
        int count = 0;
        int currentWeek = currentTeachingWeek();
        int today = currentSchoolDay();
        for (Course course : courses) {
            if (course.day == selectedDay && courseOccursInTeachingWeek(course, currentWeek)) {
                count++;
            }
        }
        Course currentCourse = currentCourseInProgress();
        SummaryCourse nextCourse = currentCourse == null ? nextUpcomingCourseFromToday() : null;
        String statusText;
        if (currentCourse != null) {
            statusText = "正在上 " + currentCourse.periodLabel() + " " + currentCourse.name;
        } else if (nextCourse != null) {
            String dayPrefix;
            boolean occursToday = startOfDayMillis(nextCourse.startAtMillis) == startOfTodayMillis();
            if (occursToday) {
                dayPrefix = selectedDay == today ? "" : "\u4eca\u5929 ";
            } else {
                dayPrefix = DAYS[nextCourse.day] + " ";
            }
            statusText = "\u4e0b\u4e00\u8282 " + dayPrefix
                    + nextCourse.course.periodLabel() + " " + nextCourse.course.name;
        } else {
            statusText = "\u6682\u65e0\u540e\u7eed\u8bfe\u7a0b";
        }
        String weekText;
        if (currentWeek >= 1 && currentWeek <= calendarTotalWeeks()) {
            weekText = "第" + currentWeek + "周 · ";
        } else if (startOfTodayMillis() < calendarStartMillis()) {
            weekText = "未开学 · ";
        } else {
            weekText = "假期 · ";
        }
        summaryText.setText(String.format(
                Locale.CHINA,
                "%s%s · %d 门课 · %s",
                weekText,
                DAYS[selectedDay],
                count,
                statusText
        ));
        renderedTemporalSignature = temporalSignature();
    }

    private void refreshTimedUi() {
        String currentSignature = temporalSignature();
        if (!TextUtils.equals(renderedTemporalSignature, currentSignature)) {
            render();
            return;
        }
        invalidateCourseProgressViews(courseList);
    }

    private String temporalSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(currentTeachingWeek())
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
        testCourse.name = "灵动岛测试课程";
        testCourse.weeks = "临时测试";
        testCourse.room = "当前节次";
        testCourse.teacher = "不保存";
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
        for (int i = 0; i < DAYS.length; i++) {
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
                textColor = Color.WHITE;
            } else if (isToday) {
                textColor = accentColor();
            } else {
                textColor = secondaryTextColor();
            }

            tabContainer.setBackground(buttonBackground(Color.TRANSPARENT, dp(18)));
            tabContainer.setSelected(isSelected);
            tabContainer.setContentDescription(DAYS[i]
                    + (isToday ? "\uff0c\u4eca\u5929" : "")
                    + (isSelected ? "\uff0c\u5df2\u9009\u62e9" : ""));
            tabContainer.setOnClickListener(view -> switchToDay(day));

            TextView dayText = new TextView(this);
            dayText.setText(DAYS[i]);
            dayText.setTextSize(compact ? 15 : 16);
            dayText.setGravity(Gravity.CENTER);
            dayText.setTypeface(dengXian(Typeface.BOLD));
            dayText.setTextColor(textColor);
            tabContainer.addView(dayText);

            // 今天添加小圆点指示器
            View todayMarker = new View(this);
            todayMarker.setBackground(buttonBackground(isToday ? accentColor() : Color.TRANSPARENT, dp(3)));
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(isToday ? 24 : 18), dp(4));
            markerParams.setMargins(0, dp(5), 0, 0);
            tabContainer.addView(todayMarker, markerParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(compact ? 62 : 70), dp(compact ? 48 : 52));
            params.setMargins(0, 0, dp(2), 0);
            dayTabs.addView(tabContainer, params);
        }
        boolean shouldAnimateSlider = animateDaySlider;
        animateDaySlider = false;
        dayTabs.post(() -> updateDaySelectionSlider(shouldAnimateSlider));
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
        daySelectionSlider.setBackground(elevatedCardBackground(todayColor(), dp(20)));

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
            warning.setText("⚠️ 加密课表无法读取\n\n为避免覆盖原数据，已暂停保存。\n请检查系统密钥或清除应用数据后重新开始。");
            warning.setTextSize(15);
            warning.setTextColor(Color.rgb(220, 38, 38));
            warning.setGravity(Gravity.CENTER);
            warning.setBackground(elevatedCardBackground(isDarkMode ? Color.rgb(60, 20, 20) : Color.rgb(254, 242, 242), dp(16)));
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

        int currentWeek = currentTeachingWeek();
        for (Course course : courses) {
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
            LinearLayout emptyState = new LinearLayout(this);
            emptyState.setOrientation(LinearLayout.VERTICAL);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setBackground(elevatedCardBackground(cardColor(), dp(16)));
            emptyState.setPadding(dp(24), dp(48), dp(24), dp(48));

            TextView emptyIcon = new TextView(this);
            emptyIcon.setText(selectedDayDateLabel());
            emptyIcon.setTextSize(30);
            emptyIcon.setTypeface(dengXian(Typeface.BOLD));
            emptyIcon.setTextColor(accentColor());
            emptyIcon.setGravity(Gravity.CENTER);
            emptyIcon.setIncludeFontPadding(false);
            emptyState.addView(emptyIcon);

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("暂无课程");
            emptyTitle.setTextSize(18);
            emptyTitle.setTypeface(dengXian(Typeface.BOLD));
            emptyTitle.setTextColor(primaryTextColor());
            emptyTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, dp(16), 0, 0);
            emptyState.addView(emptyTitle, titleParams);

            TextView emptyHint = new TextView(this);
            emptyHint.setText("点击右上角 ＋ 添加课程\n或导入学校 PDF 课表");
            emptyHint.setTextSize(14);
            emptyHint.setTextColor(secondaryTextColor());
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setLineSpacing(dp(4), 1.0f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            hintParams.setMargins(0, dp(8), 0, 0);
            emptyState.addView(emptyHint, hintParams);

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
        int maxHeightDp = active ? (compact ? 158 : 168) : (compact ? 122 : 132);
        BoundedFrameLayout shell = new BoundedFrameLayout(this);
        shell.setMinimumHeight(dp(minHeightDp));
        shell.setMaxHeightPx(dp(maxHeightDp));
        shell.setBackground(active ? activeCourseBackground(course.color, dp(28)) : courseCardBackground(course.color, dp(18)));
        shell.setClipChildren(true);
        shell.setClipToPadding(true);
        shell.setElevation(dp(active ? 11 : 4));
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
        badge.setPadding(dp(compact ? 10 : 12), dp(compact ? 14 : 16), dp(compact ? 10 : 12), dp(compact ? 14 : 16));
        badge.setBackground(buttonBackground(active ? Color.argb(64, 255, 255, 255) : course.color, dp(active ? 18 : 12)));

        TextView periodNum = new TextView(this);
        if (course.endPeriod > course.period) {
            periodNum.setText(String.format(Locale.CHINA, "%d-%d", course.period, course.endPeriod));
        } else {
            periodNum.setText(String.valueOf(course.period));
        }
        periodNum.setTextColor(active ? Color.WHITE : Color.WHITE);
        periodNum.setTypeface(dengXian(Typeface.BOLD));
        periodNum.setTextSize(active ? (compact ? 21 : 22) : (compact ? 19 : 20));
        periodNum.setLetterSpacing(0f);
        periodNum.setGravity(Gravity.CENTER);
        periodNum.setSingleLine(true);
        periodNum.setIncludeFontPadding(false);
        badge.addView(periodNum, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView periodLabel = new TextView(this);
        periodLabel.setText("节");
        periodLabel.setTextColor(Color.WHITE);
        periodLabel.setTextSize(12);
        periodLabel.setTypeface(dengXian(Typeface.NORMAL));
        periodLabel.setAlpha(active ? 0.95f : 0.9f);
        periodLabel.setGravity(Gravity.CENTER);
        periodLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(2), 0, 0);
        badge.addView(periodLabel, labelParams);

        card.addView(badge, new LinearLayout.LayoutParams(dp(active ? (compact ? 66 : 74) : (compact ? 58 : 64)), ViewGroup.LayoutParams.WRAP_CONTENT));

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
        name.setTypeface(dengXian(Typeface.BOLD));
        name.setLetterSpacing(0f);
        name.setMaxLines(active ? 1 : 2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);

        TextView time = new TextView(this);
        time.setText(periodRangeTime(course));
        time.setTextColor(active ? Color.argb(230, 255, 255, 255) : accentColor());
        time.setTextSize(active ? (compact ? 14 : 15) : 14);
        time.setTypeface(dengXian(Typeface.BOLD));
        time.setSingleLine(true);
        time.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, dp(4), 0, 0);
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
            detail.setTypeface(dengXian(Typeface.NORMAL));
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
        edit.setBackground(interactiveSurfaceBackground(active ? Color.argb(34, 255, 255, 255) : Color.TRANSPARENT, dp(18)));
        edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        edit.setContentDescription("\u7f16\u8f91\u8bfe\u7a0b");
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
            return CountdownState.plain("\u8ddd\u79bb\u4e0b\u8bfe\u8fd8\u6709 --");
        }
        if (now < start) {
            return countdownStateForRemaining(true, start - now);
        }
        if (now >= end) {
            return CountdownState.plain("\u5df2\u4e0b\u8bfe");
        }
        return countdownStateForRemaining(false, end - now);
    }

    private CountdownState countdownStateForRemaining(boolean starts, int remainingSeconds) {
        String prefix = starts
                ? "\u8ddd\u79bb\u4e0a\u8bfe\u8fd8\u6709 "
                : "\u8ddd\u79bb\u4e0b\u8bfe\u8fd8\u6709 ";
        if (remainingSeconds <= 0) {
            return CountdownState.plain(starts ? "\u5373\u5c06\u4e0a\u8bfe" : "\u5df2\u4e0b\u8bfe");
        }
        if (remainingSeconds < COUNTDOWN_SECONDS_THRESHOLD) {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            String numberPrefix = minutes > 0 ? prefix + minutes + " \u5206 " : prefix;
            return CountdownState.seconds(
                    numberPrefix,
                    String.valueOf(seconds),
                    " \u79d2",
                    starts ? "start" : "end",
                    remainingSeconds
            );
        }

        int totalMinutes = Math.max(1, (remainingSeconds + 59) / 60);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return CountdownState.plain(prefix + hours + " \u5c0f\u65f6 " + minutes + " \u5206\u949f");
        }
        if (hours > 0) {
            return CountdownState.plain(prefix + hours + " \u5c0f\u65f6");
        }
        return CountdownState.plain(prefix + totalMinutes + " \u5206\u949f");
    }

    private TextView createCountdownTextView(boolean compact) {
        TextView text = new TextView(this);
        text.setTextColor(Color.argb(245, 255, 255, 255));
        text.setTextSize(compact ? 14 : 15);
        text.setTypeface(dengXian(Typeface.BOLD));
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
            return "距离下课还有 --";
        }
        if (now < start) {
            return remainingUntilClassStartsText(start - now);
        }
        if (now >= end) {
            return "已下课";
        }
        int remaining = end - now;
        if (remaining <= 0) {
            return "距离下课还有 不到 1 分钟";
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return "距离下课还有 " + hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return "距离下课还有 " + hours + " 小时";
        }
        return "距离下课还有 " + minutes + " 分钟";
    }

    private String remainingUntilClassStartsText(int remaining) {
        if (remaining <= 0) {
            return "即将上课";
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0 && minutes > 0) {
            return "距离上课还有 " + hours + " 小时 " + minutes + " 分钟";
        }
        if (hours > 0) {
            return "距离上课还有 " + hours + " 小时";
        }
        return "距离上课还有 " + minutes + " 分钟";
    }

    private boolean isCourseApproachingStart(Course course) {
        return courseTemporalState(course) == TimetableRules.TemporalState.REMINDER_WINDOW;
    }

    private TimetableRules.TemporalState courseTemporalState(Course course) {
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
        target.setContentDescription(course.name + "，双击停止课前抖动");
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
        Toast.makeText(this, "已停止本节课抖动", Toast.LENGTH_SHORT).show();
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

    private void showCourseDialog(Course editing) {
        boolean isEditing = editing != null;
        Course draft = isEditing ? editing.copy() : new Course();
        if (!isEditing) {
            draft.id = System.currentTimeMillis();
            draft.day = selectedDay;
            draft.period = 1;
            draft.endPeriod = 1;
            draft.color = chooseCourseColorForDay(draft.day, draft.id);
        }

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.setClipToPadding(false);
        formScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), dp(14));
        formScroll.addView(form);

        Spinner daySpinner = spinner(DAYS);
        daySpinner.setSelection(draft.day);
        form.addView(label("星期"));
        form.addView(daySpinner, fieldParams());

        String[] periods = new String[periodCount()];
        for (int i = 0; i < periods.length; i++) {
            periods[i] = "第 " + (i + 1) + " 节 · " + periodTime(i + 1);
        }
        Spinner periodSpinner = spinner(periods);
        periodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.period - 1)));
        form.addView(label("开始节次"));
        form.addView(periodSpinner, fieldParams());

        Spinner endPeriodSpinner = spinner(periods);
        endPeriodSpinner.setSelection(Math.max(0, Math.min(periodCount() - 1, draft.endPeriod - 1)));
        form.addView(label("结束节次"));
        form.addView(endPeriodSpinner, fieldParams());

        EditText nameInput = input("课程名称", draft.name);
        form.addView(label("课程"));
        form.addView(nameInput, fieldParams());

        EditText weeksInput = input("例如 1-6周,9-17周,1-16周单周", draft.weeks);
        form.addView(label("上课周"));
        form.addView(weeksInput, fieldParams());

        Button temporaryWeeksButton = new Button(this);
        temporaryWeeksButton.setText("临时课程：从本周开始上 N 周");
        temporaryWeeksButton.setAllCaps(false);
        temporaryWeeksButton.setTextColor(accentColor());
        temporaryWeeksButton.setTextSize(14);
        temporaryWeeksButton.setTypeface(dengXian(Typeface.BOLD));
        temporaryWeeksButton.setBackground(elevatedCardBackground(cardColor(), dp(12)));
        temporaryWeeksButton.setPadding(dp(12), 0, dp(12), 0);
        temporaryWeeksButton.setOnClickListener(view -> showTemporaryWeeksDialog(weeksInput));
        LinearLayout.LayoutParams temporaryWeeksParams = fieldParams();
        temporaryWeeksParams.setMargins(0, 0, 0, dp(10));
        form.addView(temporaryWeeksButton, temporaryWeeksParams);

        EditText roomInput = input("教室 / 地点", draft.room);
        form.addView(label("地点"));
        form.addView(roomInput, fieldParams());

        EditText teacherInput = input("老师", draft.teacher);
        form.addView(label("老师"));
        form.addView(teacherInput, fieldParams());

        final int[] selectedColor = {safeColor(draft.color)};
        final boolean[] colorManuallySelected = {isEditing};
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(label("颜色"));
        colorScroll.addView(colorRow);
        form.addView(colorScroll, fieldParams());
        renderColorChoices(colorRow, selectedColor, () -> colorManuallySelected[0] = true);

        daySpinner.setOnItemSelectedListener(new SimpleSelectedListener(position -> {
            draft.day = position;
            if (!colorManuallySelected[0]) {
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEditing ? "编辑课程" : "添加课程")
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                if (storageLocked) {
                    Toast.makeText(this, "加密数据未解锁，暂时不能保存新内容", Toast.LENGTH_LONG).show();
                    return;
                }
                String name = cleanField(nameInput.getText().toString());
                if (name.isEmpty()) {
                    Toast.makeText(this, "课程名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isEditing && courses.size() >= MAX_COURSES) {
                    Toast.makeText(this, "课程数量已达到上限", Toast.LENGTH_SHORT).show();
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
                if (hasCourseConflict(draft)) {
                    Toast.makeText(this, "该节次和周次已有课程", Toast.LENGTH_SHORT).show();
                    return;
                }
                upsertCourse(draft);
                selectedDay = draft.day;
                saveCourses();
                render();
                dialog.dismiss();
            });

            if (isEditing) {
                Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (delete != null) {
                    delete.setTextColor(Color.rgb(180, 58, 47));
                }
            }
        });

        if (isEditing) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "删除", (d, which) -> {
                for (int i = courses.size() - 1; i >= 0; i--) {
                    if (courses.get(i).id == editing.id) {
                        courses.remove(i);
                    }
                }
                saveCourses();
                render();
            });
        }

        dialog.show();
        styleDialogWindow(dialog);
    }

    private void showTemporaryWeeksDialog(EditText weeksInput) {
        int currentWeek = currentTeachingWeek();
        int totalWeeks = calendarTotalWeeks();
        if (currentWeek <= 0 || currentWeek > totalWeeks) {
            Toast.makeText(this, "当前不在校历教学周内，无法设置临时周次", Toast.LENGTH_LONG).show();
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("临时课程周数")
                .setMessage("从当前第 " + currentWeek + " 周开始，只上 N 周。")
                .setView(durationInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                int duration = parseInt(durationInput.getText().toString(), 0);
                if (duration <= 0) {
                    Toast.makeText(this, "周数必须大于 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                int endWeek = Math.min(totalWeeks, currentWeek + duration - 1);
                String weeksText = currentWeek == endWeek ? currentWeek + "周" : currentWeek + "-" + endWeek + "周";
                weeksInput.setText(weeksText);
                weeksInput.setSelection(weeksInput.getText().length());
                if (endWeek < currentWeek + duration - 1) {
                    Toast.makeText(this, "已按本学期最后一周截断为 " + weeksText, Toast.LENGTH_LONG).show();
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
        title.setText("\u8bbe\u7f6e");
        title.setTextSize(22);
        title.setTypeface(dengXian(Typeface.BOLD));
        title.setTextColor(primaryTextColor());
        title.setIncludeFontPadding(false);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("\u5173\u95ed");
        close.setTextSize(13);
        close.setTypeface(dengXian(Typeface.BOLD));
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

        panel.addView(settingsSectionLabel("\u8bfe\u8868"));
        panel.addView(settingsMenuRow(R.drawable.ic_file_text, "\u5bfc\u5165 PDF \u8bfe\u8868", "\u4ece\u6587\u4ef6\u89e3\u6790\u8bfe\u7a0b\u3001\u5468\u6b21\u548c\u6559\u5ba4", accentColor(), view -> {
            dismissSettings[0].run();
            openPdfPicker();
        }), settingsRowParams());

        if (BuildConfig.ENABLE_TEST_COURSE) {
            panel.addView(settingsMenuRow(R.drawable.ic_bell, "\u6d4b\u8bd5\u8bfe\u524d\u901a\u77e5", "\u5728\u8c03\u8bd5\u5305\u4e2d\u7acb\u5373\u5f39\u51fa\u4e00\u6b21\u63d0\u9192", accentColor(), view -> {
                dismissSettings[0].run();
                sendDebugReminderTestNotification();
            }), settingsRowParams());
        }
        panel.addView(settingsMenuRow(
                R.drawable.ic_bell,
                "通知状态",
                notificationStatusText(),
                accentColor(),
                view -> {
                    dismissSettings[0].run();
                    openReminderSystemSettings();
                }
        ), settingsRowParams());

        panel.addView(settingsSectionLabel("\u65f6\u95f4"));
        panel.addView(settingsMenuRow(R.drawable.ic_clock_outline, "\u8bfe\u7a0b\u8282\u6b21\u8bbe\u7f6e", "\u8bbe\u7f6e\u7b2c 1 \u8282\u5230\u7b2c N \u8282\u7684\u65f6\u95f4", accentColor(), view -> {
            dismissSettings[0].run();
            showPeriodSettingsDialog();
        }), settingsRowParams());
        panel.addView(settingsMenuRow(R.drawable.ic_calendar_outline, "\u6821\u5386\u8bbe\u7f6e", "\u8bbe\u7f6e\u5f00\u5b66\u65e5\u548c\u6563\u5b66\u65e5", accentColor(), view -> {
            dismissSettings[0].run();
            showCalendarImportDialog();
        }), settingsRowParams());

        panel.addView(settingsSectionLabel("\u6570\u636e"));
        panel.addView(settingsMenuRow(R.drawable.ic_trash_outline, "\u5220\u9664\u5f53\u524d\u914d\u7f6e", "\u5220\u9664\u8bfe\u7a0b\u3001\u6821\u5386\u548c\u63d0\u9192\u6392\u7a0b", dangerColor(), view -> {
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

    private TextView settingsSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(dengXian(Typeface.BOLD));
        label.setTextColor(secondaryTextColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(4), dp(7), dp(4), dp(4));
        return label;
    }

    private LinearLayout settingsMenuRow(int iconResId, String titleText, String subtitleText, int tintColor, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(12), dp(9), dp(10), dp(9));
        row.setBackground(interactiveGlassSurfaceBackground(glassRowColor(), dp(16)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(titleText + "\uff0c" + subtitleText);
        row.setOnClickListener(listener);

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(buttonBackground(glassIconTintColor(tintColor), dp(12)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setImageTintList(ColorStateList.valueOf(tintColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconSurface.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(10), 0);
        row.addView(iconSurface, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(primaryTextColor());
        title.setTextSize(15);
        title.setTypeface(dengXian(Typeface.BOLD));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(secondaryTextColor());
        subtitle.setTextSize(11);
        subtitle.setTypeface(dengXian(Typeface.NORMAL));
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
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(elevatedCardBackground(cardColor(), dp(2)));
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(accentColor());
            positive.setTypeface(dengXian(Typeface.BOLD));
        }
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(positive == null ? accentColor() : secondaryTextColor());
            negative.setTypeface(dengXian(Typeface.BOLD));
        }
    }

    private void showSettingsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(panel)
                .setNegativeButton("关闭", null)
                .create();

        if (BuildConfig.ENABLE_TEST_COURSE) {
            Button testReminder = settingsActionButton("\u6d4b\u8bd5\u8bfe\u524d\u901a\u77e5", accentColor());
            testReminder.setOnClickListener(view -> {
                dialog.dismiss();
                sendDebugReminderTestNotification();
            });
            panel.addView(testReminder, fieldParams());
        }

        Button importPdf = settingsActionButton("导入 PDF 课表", accentColor());
        importPdf.setOnClickListener(view -> {
            dialog.dismiss();
            openPdfPicker();
        });
        panel.addView(importPdf, fieldParams());

        Button periodSettings = settingsActionButton("\u8bfe\u7a0b\u8282\u6b21\u8bbe\u7f6e", accentColor());
        periodSettings.setOnClickListener(view -> {
            dialog.dismiss();
            showPeriodSettingsDialog();
        });
        panel.addView(periodSettings, fieldParams());

        Button calendar = settingsActionButton("校历设置", accentColor());
        calendar.setOnClickListener(view -> {
            dialog.dismiss();
            showCalendarImportDialog();
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

        Button deleteConfig = settingsActionButton("删除当前配置", Color.rgb(180, 58, 47));
        deleteConfig.setOnClickListener(view -> {
            dialog.dismiss();
            confirmDeleteCurrentConfiguration();
        });
        panel.addView(deleteConfig, fieldParams());

        dialog.show();
    }

    private void showPeriodSettingsDialog() {
        periodCount();
        List<String> draft = new ArrayList<>(periodTimes);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(8), dp(4), 0);

        TextView hint = label("每条规则对应一天中的一节课，按从早到晚排列");
        panel.addView(hint);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(300)
        ));

        Button addRule = settingsActionButton("添加节次规则", accentColor());
        panel.addView(addRule, fieldParams());

        Button resetDefault = settingsActionButton("恢复默认 1-11 节", secondaryTextColor());
        panel.addView(resetDefault, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("课程节次设置")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> renderPeriodRuleList(list, draft, refresh[0]);
        refresh[0].run();

        addRule.setOnClickListener(view -> {
            if (draft.size() >= MAX_PERIODS) {
                Toast.makeText(this, "最多只能设置 " + MAX_PERIODS + " 节", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "已有课程使用第 " + maxUsedPeriod + " 节，不能删到更少", Toast.LENGTH_LONG).show();
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
    }

    private void maybeShowReminderPopupFromIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SHOW_REMINDER_POPUP, false)) {
            return;
        }
        String name = intent.getStringExtra(EXTRA_REMINDER_COURSE_NAME);
        String time = intent.getStringExtra(EXTRA_REMINDER_COURSE_TIME);
        String room = intent.getStringExtra(EXTRA_REMINDER_COURSE_ROOM);
        int minutes = intent.getIntExtra(EXTRA_REMINDER_LEAD_MINUTES, LONG_CLASS_REMINDER_MINUTES);
        intent.removeExtra(EXTRA_SHOW_REMINDER_POPUP);
        Runnable popup = () -> showClassReminderPopup(name, time, room, minutes);
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

    private void showClassReminderPopup(String name, String time, String room, int minutes) {
        String courseName = TextUtils.isEmpty(name) ? "\u4e0b\u4e00\u8282\u8bfe" : name.trim();
        StringBuilder message = new StringBuilder("\u8ddd\u79bb\u4e0a\u8bfe\u8fd8\u6709 " + minutes + " \u5206\u949f");
        if (!TextUtils.isEmpty(time)) {
            message.append("\n").append("\u65f6\u95f4\uff1a").append(time.trim());
        }
        if (!TextUtils.isEmpty(room)) {
            message.append("\n").append("\u5730\u70b9\uff1a").append(room.trim());
        }
        new AlertDialog.Builder(this)
                .setTitle(courseName)
                .setMessage(message.toString())
                .setPositiveButton("\u77e5\u9053\u4e86", null)
                .show();
    }

    private void renderPeriodRuleList(LinearLayout list, List<String> draft, Runnable refresh) {
        list.removeAllViews();
        for (int i = 0; i < draft.size(); i++) {
            final int index = i;
            Button row = settingsActionButton("第 " + (i + 1) + " 节 · " + draft.get(i), primaryTextColor());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnClickListener(view -> showPeriodRuleDialog(draft, index, refresh));
            list.addView(row, fieldParams());
        }
    }

    private void showPeriodRuleDialog(List<String> draft, int index, Runnable refresh) {
        boolean editing = index >= 0;
        String value = editing ? draft.get(index) : suggestedNextPeriodTime(draft);
        String[] parts = value.split("-");

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("HH:mm", parts.length == 2 ? parts[0] : "");
        EditText endInput = input("HH:mm", parts.length == 2 ? parts[1] : "");
        form.addView(label("开始时间"));
        form.addView(startInput, fieldParams());
        form.addView(label("结束时间"));
        form.addView(endInput, fieldParams());

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(editing ? "第 " + (index + 1) + " 节" : "添加节次规则")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        if (editing && draft.size() > 1) {
            builder.setNeutralButton("删除", null);
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
                    draft.remove(index);
                    refresh.run();
                    dialog.dismiss();
                });
            }
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private String periodRangeFromInputs(EditText startInput, EditText endInput) {
        String start = normalizeClock(startInput.getText().toString());
        String end = normalizeClock(endInput.getText().toString());
        if (start == null || end == null) {
            Toast.makeText(this, "时间格式应为 HH:mm，例如 08:00", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (clockMinutes(end) <= clockMinutes(start)) {
            Toast.makeText(this, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
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
            return "至少需要保留 1 节课";
        }
        if (rules.size() > MAX_PERIODS) {
            return "最多只能设置 " + MAX_PERIODS + " 节";
        }
        int previousEnd = -1;
        for (int i = 0; i < rules.size(); i++) {
            String[] parts = rules.get(i).split("-");
            if (parts.length != 2) {
                return "第 " + (i + 1) + " 节格式不正确";
            }
            int start = clockMinutes(parts[0]);
            int end = clockMinutes(parts[1]);
            if (start < 0 || end <= start) {
                return "第 " + (i + 1) + " 节时间不正确";
            }
            if (previousEnd > start) {
                return "节次时间需要按从早到晚排列，且不能重叠";
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
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PERIOD_TIMES_KEY, array.toString())
                .commit();
        if (!committed) {
            Toast.makeText(this, "节次设置保存失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            return false;
        }
        periodTimes.clear();
        periodTimes.addAll(rules);
        Toast.makeText(this, "节次设置已保存", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void sendDebugReminderTestNotification() {
        if (!BuildConfig.ENABLE_TEST_COURSE) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ensureNotificationPermission();
            Toast.makeText(this, "\u8bf7\u5148\u5141\u8bb8\u901a\u77e5\u6743\u9650", Toast.LENGTH_SHORT).show();
            return;
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            Toast.makeText(this, "系统已关闭本应用通知，请先在通知设置中开启", Toast.LENGTH_LONG).show();
            openNotificationSettings();
            return;
        }
        Course course = new Course();
        int period = currentPeriod();
        if (period < 1) {
            period = nearestDisplayPeriod();
        }
        course.name = "\u8bfe\u524d\u901a\u77e5\u6d4b\u8bd5";
        course.period = period;
        course.endPeriod = period;
        course.room = "\u6a21\u62df\u5668\u6d4b\u8bd5";
        int minutes = reminderLeadMinutes(course);
        new ClassReminderReceiver().onReceive(this, classReminderIntent(course));
        showClassReminderPopup(course.name, periodRangeTime(course), course.room, minutes);
        Toast.makeText(this, "\u5df2\u53d1\u9001\u4e00\u6761\u6d4b\u8bd5\u901a\u77e5", Toast.LENGTH_SHORT).show();
    }

    private String notificationStatusText() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "未授权，点击前往系统设置";
        }
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return "系统通知服务不可用";
        }
        if (android.os.Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) {
            return "已被系统关闭，点击恢复";
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
            if (channel != null && channel.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) {
                return "课程提醒频道已关闭，点击恢复";
            }
        }
        ReminderScheduler.Status status = ReminderScheduler.status(this);
        if (!status.submissionOk) {
            return "提醒规则已保存，但系统闹钟提交失败；点击检查设置";
        }
        if (status.pendingCourses <= 0 || status.nextTriggerAt <= 0L) {
            return "可用；当前没有待触发的课程提醒";
        }
        String nextTime = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new java.util.Date(status.nextTriggerAt));
        String precision = status.exactAlarmAllowed ? "精确" : "可能受系统节电策略延迟";
        return "可用；已安排 " + status.pendingCourses + " 门，下一次 " + nextTime + "（" + precision + "）";
    }

    private void openReminderSystemSettings() {
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
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(dengXian(Typeface.BOLD));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(50));
        button.setBackground(elevatedCardBackground(cardColor(), dp(14)));
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private void confirmDeleteCurrentConfiguration() {
        new AlertDialog.Builder(this)
                .setTitle("删除当前配置")
                .setMessage("这会删除已保存的课程、校历设置和提醒排程。亮暗模式不会被删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteCurrentConfiguration())
                .show();
    }

    private void deleteCurrentConfiguration() {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            cancelScheduledClassReminders(alarmManager);
        }
        ReminderScheduler.clear(this);
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(COURSES_KEY)
                .remove(LEGACY_COURSES_KEY)
                .remove(PERIOD_TIMES_KEY)
                .remove(CALENDAR_START_KEY)
                .remove(CALENDAR_END_KEY)
                .remove(CALENDAR_WEEKS_KEY)
                .remove(CALENDAR_NAME_KEY)
                .remove(REMINDER_CODES_KEY)
                .commit();
        courses.clear();
        storageLocked = false;
        loadPeriodTimes();
        selectedDay = currentSchoolDay();
        addTemporaryDynamicIslandCourseIfDebug();
        render();
        Toast.makeText(this, committed ? "当前配置已删除" : "配置删除可能未完全写入", Toast.LENGTH_LONG).show();
    }

    private void openPdfPicker() {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，暂时不能导入", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_PDF);
    }

    private void showCalendarImportDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);

        EditText startInput = input("YYYY-MM-DD", formatDate(calendarStartMillis()));
        EditText endInput = input("YYYY-MM-DD", formatDate(calendarEndMillis()));
        form.addView(label("开学日（第1周起点）"));
        form.addView(startInput, fieldParams());
        form.addView(label("散学日"));
        form.addView(endInput, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("校历设置")
                .setMessage("总周数会按开学日和散学日自动计算。")
                .setView(form)
                .setNegativeButton("取消", null)
                .setNeutralButton("在线导入", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setOnClickListener(view -> {
                Long start = parseDateMillis(startInput.getText().toString());
                Long end = parseDateMillis(endInput.getText().toString());
                if (start == null || end == null) {
                    Toast.makeText(this, "日期格式应为 YYYY-MM-DD", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (end < start) {
                    Toast.makeText(this, "散学日不能早于开学日", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveAcademicCalendar("手动设置校历", start, end);
                dialog.dismiss();
            });
            Button online = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            online.setOnClickListener(view -> {
                dialog.dismiss();
                importAcademicCalendarOnline(DEFAULT_CALENDAR_URL);
            });
        });
        dialog.show();
        styleDialogWindow(dialog);
    }

    private void importAcademicCalendarOnline(String urlText) {
        if (urlText == null || urlText.trim().isEmpty()) {
            Toast.makeText(this, "校历链接不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在在线导入校历...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                AcademicCalendar calendar = fetchAcademicCalendar(urlText.trim());
                runOnUiThread(() -> applyAcademicCalendar(calendar));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "校历导入失败: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private AcademicCalendar fetchAcademicCalendar(String urlText) throws Exception {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new ImportException("仅支持 HTTPS 校历链接");
        }
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("jwc.jyu.edu.cn") && !host.endsWith(".jyu.edu.cn")) {
            throw new ImportException("仅支持嘉应学院官网校历链接");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "MeinStundenplan/1.0");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new ImportException("服务器返回 " + code);
            }
            byte[] bytes = readWithLimit(connection.getInputStream(), MAX_CALENDAR_BYTES, "校历网页过大，已拒绝导入");
            String html = decodeCalendarText(bytes, connection.getContentType());
            return parseAcademicCalendar(html, urlText);
        } finally {
            connection.disconnect();
        }
    }

    private String decodeCalendarText(byte[] bytes, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            Matcher matcher = Pattern.compile("charset=([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (matcher.find()) {
                try {
                    charset = Charset.forName(matcher.group(1));
                } catch (Exception ignored) {
                    charset = StandardCharsets.UTF_8;
                }
            }
        }
        return new String(bytes, charset);
    }

    private AcademicCalendar parseAcademicCalendar(String html, String sourceUrl) throws ImportException {
        String normalized = html == null ? "" : html.replaceAll("\\s+", "");
        boolean knownCalendar = sourceUrl.equals(DEFAULT_CALENDAR_URL)
                || normalized.contains("嘉应学院2025-2026学年校历")
                || normalized.contains("2025-2026学年校历")
                || normalized.contains("2025—2026学年校历");
        if (!knownCalendar) {
            throw new ImportException("未识别到支持的校历内容");
        }
        AcademicCalendar calendar = new AcademicCalendar();
        calendar.name = "嘉应学院 2025-2026 第二学期";
        calendar.startMillis = dateMillis(2026, Calendar.MARCH, 2);
        calendar.endMillis = dateMillis(2026, Calendar.JULY, 19);
        calendar.totalWeeks = 20;
        return calendar;
    }

    private void applyAcademicCalendar(AcademicCalendar calendar) {
        saveAcademicCalendar(calendar.name, calendar.startMillis, calendar.endMillis);
    }

    private void saveAcademicCalendar(String name, long startMillis, long endMillis) {
        int totalWeeks = Math.max(1, (int) ((endMillis - startMillis) / MILLIS_PER_WEEK) + 1);
        boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(CALENDAR_START_KEY, startMillis)
                .putLong(CALENDAR_END_KEY, endMillis)
                .putInt(CALENDAR_WEEKS_KEY, totalWeeks)
                .putString(CALENDAR_NAME_KEY, name)
                .commit();
        if (!committed) {
            Toast.makeText(this, "校历保存失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            return;
        }
        int before = courses.size();
        removeExpiredCoursesIfNeeded(false);
        int removed = Math.max(0, before - courses.size());
        render();
        scheduleClassReminderNotifications();
        Toast.makeText(this, "校历已保存：共 " + totalWeeks + " 周，当前第 " + currentTeachingWeek() + " 周，已删除 " + removed + " 门过期课程", Toast.LENGTH_LONG).show();
    }

    private void importPdf(Uri uri) {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，暂时不能导入", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            byte[] pdfBytes = readUriBytes(uri, MAX_PDF_BYTES);

            List<PdfTextItem> allTextItems = extractAllPdfText(pdfBytes);
            parsedAndShowCourses(allTextItems);

        } catch (ImportException error) {
            Toast.makeText(this, "导入错误: " + error.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "PDF 导入失败: " + error.getClass().getSimpleName() + " - " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private List<PdfTextItem> extractAllPdfText(byte[] pdfBytes) throws ImportException {
        if (pdfBytes.length < 8 || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            throw new ImportException("选择的文件不是有效 PDF");
        }

        List<PdfTextItem> textItems = new ArrayList<>();
        byte[] streamToken = "stream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] endStreamToken = "endstream".getBytes(StandardCharsets.ISO_8859_1);
        int searchFrom = 0;
        int parsedBytes = 0;
        int streamCount = 0;

        while (searchFrom < pdfBytes.length) {
            int streamStart = indexOf(pdfBytes, streamToken, searchFrom);
            if (streamStart < 0) {
                break;
            }
            int contentStart = skipPdfLineBreak(pdfBytes, streamStart + streamToken.length);
            int streamEnd = indexOf(pdfBytes, endStreamToken, contentStart);
            if (streamEnd < 0) {
                break;
            }
            int contentEnd = trimPdfLineBreak(pdfBytes, contentStart, streamEnd);
            if (contentEnd > contentStart) {
                byte[] rawStream = Arrays.copyOfRange(pdfBytes, contentStart, contentEnd);
                byte[] stream = inflateOrRaw(rawStream);
                parsedBytes += stream.length;
                if (parsedBytes > MAX_STREAM_BYTES) {
                    throw new ImportException("PDF 内容过大，已拒绝导入");
                }
                int beforeCount = textItems.size();
                parsePdfTextItems(stream, textItems);
                int addedCount = textItems.size() - beforeCount;
                if (addedCount > 0) {
                    streamCount++;
                    android.util.Log.d("PDFStream", String.format(
                            Locale.CHINA,
                            "Stream %d: 添加了 %d 个文本项",
                            streamCount,
                            addedCount
                    ));
                }
            }
            searchFrom = streamEnd + endStreamToken.length;
        }

        android.util.Log.d("PDFStream", String.format(
                Locale.CHINA,
                "总计: 解析了 %d 个stream，提取了 %d 个文本项",
                streamCount,
                textItems.size()
        ));

        if (textItems.isEmpty()) {
            throw new ImportException("PDF 中没有可解析的文字课程表");
        }

        return textItems;
    }

    private void parsedAndShowCourses(List<PdfTextItem> textItems) {
        List<Course> parsedCourses = coursesFromPdfItems(textItems);

        if (parsedCourses.isEmpty()) {
            Toast.makeText(this, "没有在 PDF 中识别到课程", Toast.LENGTH_LONG).show();
            return;
        }

        // 显示解析结果预览 + 调试信息
        StringBuilder preview = new StringBuilder();
        preview.append("【解析统计】\n");
        preview.append("成功解析: ").append(parsedCourses.size()).append(" 门课程\n\n");

        preview.append("【课程分布】\n");
        int[] perDay = new int[7];
        for (Course c : parsedCourses) {
            perDay[c.day]++;
        }
        for (int i = 0; i < 7; i++) {
            if (perDay[i] > 0) {
                preview.append(DAYS[i]).append(": ").append(perDay[i]).append("门  ");
            }
        }
        preview.append("\n\n");

        preview.append("【节次分布】\n");
        int[] perPeriod = new int[periodCount() + 1];
        for (Course c : parsedCourses) {
            for (int p = c.period; p <= c.endPeriod; p++) {
                if (p >= 1 && p <= periodCount()) perPeriod[p]++;
            }
        }
        for (int i = 1; i <= periodCount(); i++) {
            if (perPeriod[i] > 0) {
                preview.append("第").append(i).append("节: ").append(perPeriod[i]).append("次  ");
                if (i % 4 == 0) preview.append("\n");
            }
        }
        preview.append("\n\n");

        preview.append("【课程预览】\n");
        for (int i = 0; i < Math.min(5, parsedCourses.size()); i++) {
            Course c = parsedCourses.get(i);
            preview.append(String.format(Locale.CHINA, "%d. %s\n   %s 第%d-%d节 %s\n",
                i+1, c.name, DAYS[c.day], c.period, c.endPeriod, c.weeks));
        }
        if (parsedCourses.size() > 5) {
            preview.append("... 还有 ").append(parsedCourses.size() - 5).append(" 门");
        }

        new AlertDialog.Builder(this)
            .setTitle("步骤2: 课程解析结果")
            .setMessage(preview.toString())
            .setPositiveButton("确认导入", (dialog, which) -> {
                int imported = 0;
                int skipped = 0;
                long baseId = System.currentTimeMillis();
                StringBuilder skipLog = new StringBuilder();

                for (Course parsed : parsedCourses) {
                    if (courses.size() >= MAX_COURSES) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 达到上限\n");
                        continue;
                    }
                    parsed.id = baseId + imported + skipped;
                    parsed.color = chooseCourseColorForDay(parsed.day, parsed.id);
                    if (hasImportedCourseDuplicate(parsed)) {
                        skipped++;
                        skipLog.append(parsed.name).append(": 已存在\n");
                        continue;
                    }
                    courses.add(parsed);
                    imported++;
                }

                int removedExpired = 0;
                if (imported > 0) {
                    selectedDay = parsedCourses.get(0).day;
                    saveCourses();
                    int beforeClean = courses.size();
                    removeExpiredCoursesIfNeeded(false);
                    removedExpired = Math.max(0, beforeClean - courses.size());
                    render();
                }

                String result = "导入 " + imported + " 门课程，跳过 " + skipped + " 门";
                if (removedExpired > 0) {
                    result += "，自动删除过期 " + removedExpired + " 门";
                }
                if (skipped > 0 && skipLog.length() > 0) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle(result)
                        .setMessage("跳过原因:\n" + skipLog.toString())
                        .setPositiveButton("确定", null)
                        .show();
                } else {
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws IOException, ImportException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new ImportException("无法读取 PDF 文件");
            }
            return readWithLimit(inputStream, maxBytes, "PDF 文件超过 5MB，已拒绝导入");
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

    private List<Course> parsePdfCourses(byte[] pdfBytes) throws ImportException {
        if (pdfBytes.length < 8 || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            throw new ImportException("选择的文件不是有效 PDF");
        }

        List<PdfTextItem> textItems = new ArrayList<>();
        byte[] streamToken = "stream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] endStreamToken = "endstream".getBytes(StandardCharsets.ISO_8859_1);
        int searchFrom = 0;
        int parsedBytes = 0;
        while (searchFrom < pdfBytes.length) {
            int streamStart = indexOf(pdfBytes, streamToken, searchFrom);
            if (streamStart < 0) {
                break;
            }
            int contentStart = skipPdfLineBreak(pdfBytes, streamStart + streamToken.length);
            int streamEnd = indexOf(pdfBytes, endStreamToken, contentStart);
            if (streamEnd < 0) {
                break;
            }
            int contentEnd = trimPdfLineBreak(pdfBytes, contentStart, streamEnd);
            if (contentEnd > contentStart) {
                byte[] rawStream = Arrays.copyOfRange(pdfBytes, contentStart, contentEnd);
                byte[] stream = inflateOrRaw(rawStream);
                parsedBytes += stream.length;
                if (parsedBytes > MAX_STREAM_BYTES) {
                    throw new ImportException("PDF 内容过大，已拒绝导入");
                }
                parsePdfTextItems(stream, textItems);
            }
            searchFrom = streamEnd + endStreamToken.length;
        }

        if (textItems.isEmpty()) {
            throw new ImportException("PDF 中没有可解析的文字课程表");
        }

        // 调试：输出所有文本项
        StringBuilder allText = new StringBuilder("PDF中所有文本项（前100个）:\n");
        for (int i = 0; i < Math.min(100, textItems.size()); i++) {
            PdfTextItem item = textItems.get(i);
            allText.append(String.format(Locale.ROOT, "[%d] x=%.1f y=%.1f: %s\n", i, item.x, item.y, item.text));
        }
        android.util.Log.d("PDFDebug", allText.toString());

        return coursesFromPdfItems(textItems);
    }

    private byte[] inflateOrRaw(byte[] rawStream) throws ImportException {
        try {
            return readWithLimit(new InflaterInputStream(new ByteArrayInputStream(rawStream)), MAX_STREAM_BYTES, "PDF 压缩内容过大，已拒绝导入");
        } catch (ImportException error) {
            throw error;
        } catch (Exception ignored) {
            return rawStream;
        }
    }

    private void parsePdfTextItems(byte[] stream, List<PdfTextItem> textItems) {
        double currentX = 0;
        double currentY = 0;
        int index = 0;
        while (index < stream.length) {
            if (index + 1 < stream.length && stream[index] == 'T' && stream[index + 1] == 'm') {
                double[] matrix = readLastNumbersBefore(stream, index, 6);
                if (matrix != null) {
                    currentX = matrix[4];
                    currentY = matrix[5];
                }
                index += 2;
                continue;
            }

            if (stream[index] == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                int afterLiteral = skipWhitespace(stream, literal.nextIndex);
                if (afterLiteral + 1 < stream.length && stream[afterLiteral] == 'T' && stream[afterLiteral + 1] == 'j') {
                    String text = decodePdfText(literal.bytes).trim();
                    if (!text.isEmpty()) {
                        textItems.add(new PdfTextItem(currentX, currentY, text));
                    }
                    index = afterLiteral + 2;
                    continue;
                }
                index = literal.nextIndex;
                continue;
            }

            if (stream[index] == '[') {
                PdfLiteral arrayText = readPdfTextArray(stream, index);
                int afterArray = skipWhitespace(stream, arrayText.nextIndex);
                if (afterArray + 1 < stream.length && stream[afterArray] == 'T' && stream[afterArray + 1] == 'J') {
                    String text = decodePdfText(arrayText.bytes).trim();
                    if (!text.isEmpty()) {
                        textItems.add(new PdfTextItem(currentX, currentY, text));
                    }
                    index = afterArray + 2;
                    continue;
                }
                index = arrayText.nextIndex;
                continue;
            }

            index++;
        }
    }

    private List<Course> coursesFromPdfItems(List<PdfTextItem> items) {
        List<Course> parsed = new ArrayList<>();
        StringBuilder debugLog = new StringBuilder("PDF解析调试:\n");
        int matchCount = 0;
        int skipCount = 0;

        for (int i = 0; i < items.size() && parsed.size() < MAX_COURSES; i++) {
            PdfTextItem item = items.get(i);
            TimetableRules.PeriodMatch periodMatch =
                    TimetableRules.matchPeriodWeeks(item.text, periodCount());
            if (!periodMatch.matched) {
                continue;
            }

            matchCount++;
            boolean rowAxis = usesRowAxis(items, i);
            int day = rowAxis ? dayFromX(item.y) : dayFromX(item.x);
            debugLog.append(String.format(Locale.CHINA, "\n[匹配%d] 文本: %s\n", matchCount, item.text));
            debugLog.append(String.format(Locale.ROOT, "  位置: x=%.2f, y=%.2f\n", item.x, item.y));
            debugLog.append(String.format(Locale.CHINA, "  解析: 星期=%d, 节次=%s-%s, 周次=%s\n",
                day, periodMatch.startPeriod, periodMatch.endPeriod, periodMatch.trailingText));

            if (!periodMatch.valid) {
                debugLog.append("  结果: 节次超出当前设置或范围倒置，跳过\n");
                skipCount++;
                continue;
            }

            if (day < 0) {
                debugLog.append("  结果: X坐标不匹配任何星期，跳过\n");
                skipCount++;
                continue;
            }

            String name = courseNameBefore(items, i, rowAxis);
            debugLog.append(String.format(Locale.CHINA, "  课程名: %s\n", name));

            if (name.isEmpty()) {
                debugLog.append("  结果: 课程名称为空，跳过\n");
                skipCount++;
                continue;
            }

            int period = periodMatch.startPeriod;
            int endPeriod = periodMatch.endPeriod;
            String block = courseBlockAfter(items, i, rowAxis);

            Course course = new Course();
            course.day = day;
            course.period = period;
            course.endPeriod = endPeriod;
            course.name = cleanField(name);

            // 清理weeks字段：移除时间信息（如"及18:30-20:10"）
            String rawWeeks = periodMatch.trailingText;
            if (rawWeeks != null) {
                // 移除附带的时间格式（如“及18:30-20:10”），保留“1-6周及9-17周”。
                rawWeeks = rawWeeks.replaceAll(
                        "\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}",
                        ""
                ).trim();
            }
            course.weeks = cleanField(cleanPdfWeeks(rawWeeks));
            TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(course.weeks);
            if (!weekResult.valid) {
                debugLog.append("  结果: 上课周格式无法识别，跳过\n");
                skipCount++;
                continue;
            }

            course.room = cleanField(extractPdfField(block, "/场地:"));
            course.teacher = cleanField(extractPdfField(block, "/教师:"));
            course.color = previewCourseColorForDay(parsed, course.day);

            if (!course.name.isEmpty()) {
                parsed.add(course);
                debugLog.append(String.format(Locale.CHINA, "  结果: 成功添加 - %s (%s 第%d-%d节 %s)\n",
                    course.name, DAYS[course.day], course.period, course.endPeriod, course.weeks));
            }
        }

        debugLog.append(String.format(Locale.CHINA, "\n总结: 匹配到%d个，成功%d个，跳过%d个\n", matchCount, parsed.size(), skipCount));
        android.util.Log.d("PDFImport", debugLog.toString());

        return parsed;
    }

    private String cleanPdfWeeks(String rawWeeks) {
        if (rawWeeks == null) {
            return "";
        }
        String cleaned = rawWeeks.replaceFirst("^[\\uff09)\\s:\\uff1a]+", "").trim();
        return cleaned
                .replaceAll("\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}", "")
                .trim();
    }

    private String courseNameBefore(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        List<String> parts = new ArrayList<>();
        for (int i = metadataIndex - 1; i >= 0; i--) {
            PdfTextItem previous = items.get(i);
            if (!sameCourseAxis(previous, metadata, rowAxis) || !looksLikeCourseNameLine(previous.text)) {
                break;
            }
            parts.add(0, previous.text);
        }
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(part);
        }
        String result = name.toString()
                .replace("★", "")
                .replace("○", "")
                .replace("●", "")
                .replace("◇", "")
                .replace("◆", "")
                .trim();

        // 移除结尾可能粘连的星期和节次信息
        result = result.replaceAll("周[一二三四五六日]第\\d{1,2}-?\\d{0,2}节$", "");
        result = result.replaceAll("第\\d{1,2}-?\\d{0,2}节$", "");

        return result.trim();
    }

    private String courseBlockAfter(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        StringBuilder block = new StringBuilder();
        for (int i = metadataIndex; i < items.size(); i++) {
            PdfTextItem current = items.get(i);
            if (!sameCourseAxis(current, metadata, rowAxis)) {
                break;
            }
            if (i > metadataIndex && startsNewCourseTitle(current.text)) {
                break;
            }
            block.append(current.text);
        }
        return block.toString();
    }

    private boolean looksLikeCourseNameLine(String text) {
        String value = text.trim();
        return !value.isEmpty()
                && !value.contains("/")
                && !value.startsWith(":")
                && !value.startsWith("：")
                && !value.contains("节)")
                && !value.contains("节）")
                && !value.matches(".*第\\d{1,2}-?\\d{0,2}节.*") // 排除包含"第X节"的行
                && !value.matches("周[一二三四五六日].*") // 排除以"周X"开头的行
                && !value.matches("\\d+")
                && !value.startsWith("打印时间")
                && !value.startsWith(": 体育课");
    }

    private boolean startsNewCourseTitle(String text) {
        return (text.contains("★")
                || text.contains("○")
                || text.contains("●")
                || text.contains("◇")
                || text.contains("◆"))
                && !text.contains("教学班");
    }

    private boolean sameColumn(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.x - right.x) < 3.0;
    }

    private boolean sameRow(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.y - right.y) < 3.0;
    }

    private boolean sameCourseAxis(PdfTextItem left, PdfTextItem right, boolean rowAxis) {
        return rowAxis ? sameRow(left, right) : sameColumn(left, right);
    }

    private boolean usesRowAxis(List<PdfTextItem> items, int metadataIndex) {
        PdfTextItem metadata = items.get(metadataIndex);
        if (metadataIndex > 0) {
            PdfTextItem previous = items.get(metadataIndex - 1);
            if (sameRow(previous, metadata) && !sameColumn(previous, metadata)) {
                return true;
            }
        }
        if (metadataIndex + 1 < items.size()) {
            PdfTextItem next = items.get(metadataIndex + 1);
            if (sameRow(next, metadata) && !sameColumn(next, metadata)) {
                return true;
            }
        }
        return false;
    }

    private int dayFromX(double x) {
        int day = (int) Math.round((x - DAY_FIRST_COLUMN_X) / DAY_COLUMN_WIDTH);
        if (day < 0 || day >= DAYS.length) {
            return -1;
        }
        double expectedX = DAY_FIRST_COLUMN_X + day * DAY_COLUMN_WIDTH;
        return Math.abs(x - expectedX) <= 35.0 ? day : -1;
    }

    private String extractPdfField(String block, String marker) {
        int start = block.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = block.indexOf("/", start);
        if (end < 0) {
            end = block.length();
        }
        return block.substring(start, end).trim();
    }

    private PdfLiteral readPdfLiteral(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int depth = 1;
        int index = startIndex + 1;
        while (index < stream.length && depth > 0) {
            int value = stream[index] & 0xff;
            if (value == '\\') {
                index++;
                if (index >= stream.length) {
                    break;
                }
                int escaped = stream[index] & 0xff;
                switch (escaped) {
                    case 'n':
                        output.write('\n');
                        index++;
                        break;
                    case 'r':
                        output.write('\r');
                        index++;
                        break;
                    case 't':
                        output.write('\t');
                        index++;
                        break;
                    case 'b':
                        output.write('\b');
                        index++;
                        break;
                    case 'f':
                        output.write('\f');
                        index++;
                        break;
                    case '(':
                    case ')':
                    case '\\':
                        output.write(escaped);
                        index++;
                        break;
                    case '\r':
                        index++;
                        if (index < stream.length && stream[index] == '\n') {
                            index++;
                        }
                        break;
                    case '\n':
                        index++;
                        break;
                    default:
                        if (escaped >= '0' && escaped <= '7') {
                            int octal = escaped - '0';
                            index++;
                            for (int i = 0; i < 2 && index < stream.length; i++) {
                                int digit = stream[index] & 0xff;
                                if (digit < '0' || digit > '7') {
                                    break;
                                }
                                octal = octal * 8 + digit - '0';
                                index++;
                            }
                            output.write(octal & 0xff);
                        } else {
                            output.write(escaped);
                            index++;
                        }
                        break;
                }
            } else if (value == '(') {
                depth++;
                output.write(value);
                index++;
            } else if (value == ')') {
                depth--;
                if (depth > 0) {
                    output.write(value);
                }
                index++;
            } else {
                output.write(value);
                index++;
            }
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private PdfLiteral readPdfTextArray(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == ']') {
                index++;
                break;
            }
            if (value == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                output.write(literal.bytes, 0, literal.bytes.length);
                index = literal.nextIndex;
                continue;
            }
            if (value == '<' && index + 1 < stream.length && stream[index + 1] != '<') {
                PdfLiteral hex = readPdfHexString(stream, index);
                output.write(hex.bytes, 0, hex.bytes.length);
                index = hex.nextIndex;
                continue;
            }
            index++;
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private PdfLiteral readPdfHexString(byte[] stream, int startIndex) {
        ByteArrayOutputStream hex = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == '>') {
                index++;
                break;
            }
            if (isHexByte(value)) {
                hex.write(value);
            }
            index++;
        }

        byte[] hexBytes = hex.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < hexBytes.length; i += 2) {
            int high = hexValue(hexBytes[i]);
            int low = i + 1 < hexBytes.length ? hexValue(hexBytes[i + 1]) : 0;
            output.write(((high << 4) | low) & 0xff);
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private String decodePdfText(byte[] bytes) {
        if (bytes.length >= 2 && bytes.length % 2 == 0) {
            String utf16 = new String(bytes, Charset.forName("UTF-16BE"));
            if (hasReadableText(utf16)) {
                return utf16;
            }
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (hasReadableText(utf8)) {
            return utf8;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private boolean hasReadableText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if ((value >= 0x4e00 && value <= 0x9fff) || Character.isLetterOrDigit(value)) {
                return true;
            }
        }
        return false;
    }

    private double[] readLastNumbersBefore(byte[] data, int operatorIndex, int count) {
        double[] numbers = new double[count];
        int found = 0;
        int index = operatorIndex - 1;
        while (found < count && index >= 0) {
            while (index >= 0 && isWhitespace(data[index])) {
                index--;
            }
            int end = index + 1;
            while (index >= 0 && isNumberByte(data[index])) {
                index--;
            }
            int start = index + 1;
            if (start >= end) {
                return null;
            }
            try {
                String token = new String(data, start, end - start, StandardCharsets.ISO_8859_1);
                numbers[count - found - 1] = Double.parseDouble(token);
                found++;
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return found == count ? numbers : null;
    }

    private boolean isNumberByte(byte value) {
        return (value >= '0' && value <= '9') || value == '-' || value == '+' || value == '.';
    }

    private boolean isHexByte(int value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private int hexValue(byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return 0;
    }

    private boolean isWhitespace(byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t' || value == '\f' || value == 0;
    }

    private int skipWhitespace(byte[] data, int index) {
        int cursor = index;
        while (cursor < data.length && isWhitespace(data[cursor])) {
            cursor++;
        }
        return cursor;
    }

    private int skipPdfLineBreak(byte[] data, int index) {
        int cursor = index;
        if (cursor < data.length && data[cursor] == '\r') {
            cursor++;
            if (cursor < data.length && data[cursor] == '\n') {
                cursor++;
            }
        } else if (cursor < data.length && data[cursor] == '\n') {
            cursor++;
        }
        return cursor;
    }

    private int trimPdfLineBreak(byte[] data, int start, int end) {
        int cursor = end;
        while (cursor > start && (data[cursor - 1] == '\n' || data[cursor - 1] == '\r')) {
            cursor--;
        }
        return cursor;
    }

    private int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            boolean matched = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private void renderColorChoices(LinearLayout row, int[] selectedColor) {
        renderColorChoices(row, selectedColor, null);
    }

    private void renderColorChoices(LinearLayout row, int[] selectedColor, Runnable onSelect) {
        row.removeAllViews();
        for (int color : COURSE_COLORS) {
            TextView swatch = new TextView(this);
            boolean selected = color == selectedColor[0];
            swatch.setText(selected ? "\u2713" : "");
            swatch.setTextColor(Color.WHITE);
            swatch.setTextSize(18);
            swatch.setTypeface(dengXian(Typeface.BOLD));
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(colorSwatchBackground(color, selected));
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
        List<Integer> usedColors = new ArrayList<>();
        for (Course course : courses) {
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

    private boolean rebalanceCourseColorsIfNeeded() {
        boolean changed = false;
        for (int day = 0; day < DAYS.length; day++) {
            List<Course> dayCourses = new ArrayList<>();
            for (Course course : courses) {
                if (!course.temporary && course.day == day) {
                    dayCourses.add(course);
                }
            }
            Collections.sort(dayCourses, (left, right) -> {
                int result = Integer.compare(left.period, right.period);
                if (result == 0) result = Integer.compare(left.endPeriod, right.endPeriod);
                if (result == 0) result = left.name.compareTo(right.name);
                return result;
            });

            List<Integer> usedColors = new ArrayList<>();
            for (Course course : dayCourses) {
                int safe = safeColor(course.color);
                if (courseColorTooCloseToAny(safe, usedColors)) {
                    safe = bestCourseColor(usedColors);
                }
                if (course.color != safe) {
                    course.color = safe;
                    changed = true;
                }
                usedColors.add(safe);
            }
        }
        return changed;
    }

    private boolean courseColorTooCloseToAny(int color, List<Integer> usedColors) {
        for (int used : usedColors) {
            if (courseColorDistance(color, used) < 30f) {
                return true;
            }
        }
        return false;
    }

    private void upsertCourse(Course draft) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == draft.id) {
                courses.set(i, draft.copy());
                return;
            }
        }
        courses.add(draft.copy());
    }

    private boolean hasCourseConflict(Course draft) {
        for (Course course : courses) {
            if (course.temporary || course.id == draft.id || course.day != draft.day) {
                continue;
            }
            if (periodsOverlap(course, draft) && weeksOverlap(course.weeks, draft.weeks)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasImportedCourseDuplicate(Course draft) {
        for (Course course : courses) {
            if (course.day == draft.day
                    && course.period == draft.period
                    && course.endPeriod == draft.endPeriod
                    && sameCourseText(course.name, draft.name)
                    && TimetableRules.sameWeekExpression(course.weeks, draft.weeks)
                    && sameCourseText(course.room, draft.room)
                    && sameCourseText(course.teacher, draft.teacher)) {
                return true;
            }
        }
        return false;
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

    private boolean removeExpiredCoursesIfNeeded(boolean showToast) {
        if (storageLocked) {
            return false;
        }
        int currentWeek = currentTeachingWeek();
        if (currentWeek <= 0) {
            return false;
        }
        int removed = 0;
        for (int i = courses.size() - 1; i >= 0; i--) {
            Course course = courses.get(i);
            if (!course.temporary && isCourseExpiredByTeachingWeek(course, currentWeek)) {
                courses.remove(i);
                removed++;
            }
        }
        if (removed <= 0) {
            return false;
        }
        saveCourses();
        if (showToast) {
            Toast.makeText(this, "已自动删除 " + removed + " 门上课周已结束的课程", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private boolean isCourseExpiredByTeachingWeek(Course course, int currentWeek) {
        TimetableRules.WeekParseResult result = TimetableRules.parseWeeks(course.weeks);
        if (!result.valid || !result.explicit) {
            return false;
        }
        boolean[] weeks = result.weeks;
        int maxWeek = Math.max(currentWeek, calendarTotalWeeks() + 1);
        for (int week = currentWeek; week <= maxWeek && week < weeks.length; week++) {
            if (weeks[week]) {
                return false;
            }
        }
        return true;
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
        if (today < start) {
            return 0;
        }
        if (today > calendarEndMillis()) {
            return calendarTotalWeeks() + 1;
        }
        return (int) ((today - start) / MILLIS_PER_WEEK) + 1;
    }

    private int calendarTotalWeeks() {
        long start = calendarStartMillis();
        long end = calendarEndMillis();
        if (end < start) {
            return 20;
        }
        return Math.max(1, (int) ((end - start) / MILLIS_PER_WEEK) + 1);
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
            return "时间未设置";
        }
        return periodTimes.get(index);
    }

    private int currentSchoolDay() {
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SUNDAY ? 6 : dayOfWeek - Calendar.MONDAY;
    }

    private String selectedDayDateLabel() {
        Calendar calendar = Calendar.getInstance();
        int currentWeek = currentTeachingWeek();
        if (currentWeek >= 1 && currentWeek <= calendarTotalWeeks()) {
            calendar.setTimeInMillis(courseDateMillisForWeek(selectedDay, currentWeek));
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, selectedDay - currentSchoolDay());
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
        spinner.setPopupBackgroundDrawable(elevatedCardBackground(cardColor(), dp(12)));
        return spinner;
    }

    private void styleSpinnerText(View view, boolean dropdown) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView text = (TextView) view;
        text.setTextColor(primaryTextColor());
        text.setTextSize(dropdown ? 15 : 16);
        text.setTypeface(dengXian(Typeface.NORMAL));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMinHeight(dp(dropdown ? 46 : 52));
        text.setPadding(dp(14), 0, dp(dropdown ? 14 : 42), 0);
        text.setBackground(dropdown
                ? buttonBackground(cardColor(), 0)
                : elevatedCardBackground(cardColor(), dp(12)));
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTypeface(dengXian(Typeface.BOLD));
        label.setTextColor(secondaryTextColor());
        label.setPadding(0, dp(8), 0, dp(4));
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
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));
        editText.setBackground(elevatedCardBackground(cardColor(), dp(12)));
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

    private android.graphics.drawable.StateListDrawable interactiveSurfaceBackground(int color, int radius) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                roundedSurface(pressedSurfaceColor(color), radius, borderColor())
        );
        states.addState(new int[]{}, roundedSurface(color, radius, borderColor()));
        return states;
    }

    private android.graphics.drawable.StateListDrawable interactiveButtonBackground(int color, int radius) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                roundedSurface(pressedButtonColor(color), radius, Color.TRANSPARENT)
        );
        states.addState(new int[]{}, roundedSurface(color, radius, Color.TRANSPARENT));
        return states;
    }

    private android.graphics.drawable.StateListDrawable interactiveGlassSurfaceBackground(int color, int radius) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int pressed = withAlpha(
                mixColor(color, isDarkMode ? Color.WHITE : Color.BLACK, isDarkMode ? 0.10f : 0.04f),
                Math.min(230, Color.alpha(color) + 32)
        );
        states.addState(
                new int[]{android.R.attr.state_pressed},
                roundedSurface(pressed, radius, glassBorderColor())
        );
        states.addState(new int[]{}, roundedSurface(color, radius, glassBorderColor()));
        return states;
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

    private GradientDrawable cardBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), borderColor());
        return drawable;
    }

    private GradientDrawable activeCourseBackground(int color, int radius) {
        int start = mixColor(color, Color.WHITE, isDarkMode ? 0.06f : 0.10f);
        int end = mixColor(color, Color.BLACK, isDarkMode ? 0.10f : 0.04f);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable courseCardBackground(int color, int radius) {
        int surface = mixColor(cardColor(), color, isDarkMode ? 0.08f : 0.04f);
        int stroke = mixColor(borderColor(), color, isDarkMode ? 0.20f : 0.12f);
        return roundedSurface(surface, radius, stroke);
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

    private int pressedSurfaceColor(int color) {
        return mixColor(color, isDarkMode ? Color.WHITE : Color.BLACK, isDarkMode ? 0.08f : 0.05f);
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
        return isDarkMode ? Color.rgb(105, 178, 255) : Color.rgb(30, 104, 210);
    }

    private int todayColor() {
        return isDarkMode ? Color.rgb(73, 87, 207) : Color.rgb(76, 99, 229);
    }

    private void loadCourses() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String encrypted = prefs.getString(COURSES_KEY, null);
        String raw;
        boolean migrateLegacy = false;
        boolean encryptedSource = encrypted != null;
        try {
            if (encryptedSource) {
                raw = decrypt(encrypted);
            } else {
                raw = prefs.getString(LEGACY_COURSES_KEY, "[]");
                migrateLegacy = !raw.equals("[]");
            }
        } catch (Exception error) {
            courses.clear();
            storageLocked = true;
            Toast.makeText(this, "课表数据解密失败，已阻止覆盖原数据", Toast.LENGTH_LONG).show();
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
                course.day = clamp(object.optInt("day", 0), 0, DAYS.length - 1);
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
                saveCourses();
            }
            if (invalidWeekCount > 0) {
                Toast.makeText(
                        this,
                        "有 " + invalidWeekCount + " 门课程的上课周格式无法识别，请打开课程并修正；这些课程不会安排通知",
                        Toast.LENGTH_LONG
                ).show();
            }
        } catch (Exception ignored) {
            courses.clear();
            storageLocked = encryptedSource;
            Toast.makeText(this, "课表数据格式异常，已忽略损坏内容", Toast.LENGTH_LONG).show();
        }
    }

    private void saveCourses() {
        if (storageLocked) {
            Toast.makeText(this, "加密数据未解锁，已拒绝覆盖保存", Toast.LENGTH_LONG).show();
            return;
        }
        JSONArray array = new JSONArray();
        try {
            for (Course course : courses) {
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
            Toast.makeText(this, "课表序列化失败", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encrypted = encrypt(array.toString());
            boolean committed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(COURSES_KEY, encrypted)
                    .remove(LEGACY_COURSES_KEY)
                    .commit();
            if (!committed) {
                Toast.makeText(this, "课表写入失败，请检查设备存储状态", Toast.LENGTH_LONG).show();
            } else {
                scheduleClassReminderNotifications();
            }
        } catch (Exception error) {
            Toast.makeText(this, "课表加密保存失败", Toast.LENGTH_LONG).show();
        }
    }

    private String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] iv = cipher.getIV();
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("data", Base64.encodeToString(cipherText, Base64.NO_WRAP));
        return envelope.toString();
    }

    private String decrypt(String encrypted) throws Exception {
        JSONObject envelope = new JSONObject(encrypted);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(envelope.getString("data"), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(keySpec);
        return keyGenerator.generateKey();
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

    private Typeface dengXian(int style) {
        return Typeface.create("DengXian", style);
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

    private static class PdfTextItem {
        final double x;
        final double y;
        final String text;

        PdfTextItem(double x, double y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }
    }

    private static class PdfLiteral {
        final byte[] bytes;
        final int nextIndex;

        PdfLiteral(byte[] bytes, int nextIndex) {
            this.bytes = bytes;
            this.nextIndex = nextIndex;
        }
    }

    private static class AcademicCalendar {
        String name;
        long startMillis;
        long endMillis;
        int totalWeeks;
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

    private static class BoundedFrameLayout extends FrameLayout {
        private int maxHeightPx;

        BoundedFrameLayout(android.content.Context context) {
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

    private static class ImportException extends Exception {
        ImportException(String message) {
            super(message);
        }
    }

    private static class Course {
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

        String periodLabel() {
            if (endPeriod > period) {
                return "第" + period + "-" + endPeriod + "节";
            }
            return "第" + period + "节";
        }

        String periodBadgeText() {
            if (endPeriod > period) {
                return "第\n" + period + "-" + endPeriod + "\n节";
            }
            return "第\n" + period + "\n节";
        }

        String secondaryText() {
            List<String> parts = new ArrayList<>();
            if (invalidWeeks) {
                parts.add("⚠ 上课周格式需修正");
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
            return parts.isEmpty() ? "暂无上课周、地点和老师信息" : join(parts, " · ");
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

