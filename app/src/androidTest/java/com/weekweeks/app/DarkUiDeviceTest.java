package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Focused real-Activity checks. Must target the isolated audit application, never production. */
public final class DarkUiDeviceTest extends Instrumentation {
    private static final String AUDIT_PACKAGE = "com.weekweeks.app.audit";
    private static final long TIMEOUT = 10000L;
    private final List<Activity> activities = new ArrayList<>();
    private Activity current;
    private SharedPreferences preferences;
    private String themeKey, accentKey;
    private boolean hadTheme, hadAccent;
    private Object originalTheme, originalAccent;
    private boolean preferencesCaptured;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Throwable failure = null;
        try {
            check(AUDIT_PACKAGE.equals(getTargetContext().getPackageName()),
                    "Refusing non-isolated target: " + getTargetContext().getPackageName());
            String prefsName = (String) staticField("PREFS_NAME");
            themeKey = (String) staticField("THEME_KEY");
            accentKey = (String) staticField("ACCENT_KEY");
            check("week_weeks".equals(prefsName), "Unexpected application preference name");
            check("theme_mode".equals(themeKey) && "accent_preset".equals(accentKey),
                    "Unexpected theme preference keys");
            preferences = getTargetContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            hadTheme = preferences.contains(themeKey);
            hadAccent = preferences.contains(accentKey);
            originalTheme = preferences.getAll().get(themeKey);
            originalAccent = preferences.getAll().get(accentKey);
            preferencesCaptured = true;
            check(preferences.edit().putInt(themeKey, 1).putInt(accentKey, 7).commit(),
                    "Could not initialize isolated theme preferences");
            Intent launch = new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            current = startActivitySync(launch);
            activities.add(current);
            waitForIdleSync();
            ui(() -> {
                check(((Integer) field(current, "themeMode")) == 1, "Light preference not loaded");
                check(!(Boolean) field(current, "isDarkMode"), "Light Activity not actually light");
                check(((ModalCoordinator) field(current, "modalCoordinator")).canOpen(),
                        "Unexpected startup modal; cannot silently dismiss unrelated windows");
            });
            status("START isolated API " + android.os.Build.VERSION.SDK_INT
                    + "; only audit theme/accent keys will be restored; no production access");

            checkThemeTouches(1);
            checkReminderTouches();

            // Rotate only this Activity; no emulator/global rotation setting is changed.
            showAndSelect(2);
            current = rotate(current);
            assertRestoredSelection(2, 1);
            current = rotate(current);
            assertRestoredSelection(2, 1);
            ui(() -> themeDialog(current).cancel());
            waitForIdleSync();
            assertClosedAndUnchanged(1);
            status("PASS real-window ListView selection + two Activity recreations + cancel; saved light unchanged");

            showAndSelect(2);
            current = recreate(current);
            assertRestoredSelection(2, 1);
            ui(() -> check(themeDialog(current).getButton(AlertDialog.BUTTON_NEGATIVE).performClick(),
                    "Negative button did not dispatch"));
            waitForIdleSync();
            assertClosedAndUnchanged(1);
            status("PASS restored negative-button cancellation; capture/token released; no draft resurrected");

            showAndSelect(2);
            current = recreate(current);
            assertRestoredSelection(2, 1);
            current = confirmChanged(current, 2);
            ui(() -> {
                check((Boolean) field(current, "isDarkMode"), "Saved dark mode not actually loaded");
                check((Integer) field(current, "themeMode") == 2, "Activity themeMode not dark");
            });
            assertClosedAndUnchanged(2);
            status("PASS confirming restored dark choice saves preference and loads a new dark Activity");
            checkThemeTouches(2);
            checkReminderTouches();

            // Selecting the persisted mode must close without asking Activity to recreate.
            showAndSelect(2);
            Activity unchanged = current;
            ActivityMonitor noRecreation = addMonitor(MainActivity.class.getName(), null, false);
            try {
                ui(() -> check(themeDialog(current).getButton(AlertDialog.BUTTON_POSITIVE).performClick(),
                        "Same-value confirm did not dispatch"));
                waitForIdleSync();
                Activity unexpected = waitForMonitorWithTimeout(noRecreation, 1200L);
                if (unexpected != null) activities.add(unexpected);
                check(unexpected == null && noRecreation.getHits() == 0,
                        "Selecting saved mode unnecessarily recreated Activity");
                ui(() -> check(!unchanged.isFinishing() && !unchanged.isDestroyed(),
                        "Same-value confirmation destroyed its Activity"));
            } finally { removeMonitor(noRecreation); }
            assertClosedAndUnchanged(2);
            status("PASS selecting saved dark mode does not recreate and releases owned dialog/draft/modal");

            // Also check the reverse transition: restored light choice must genuinely load light.
            showAndSelect(1);
            current = recreate(current);
            assertRestoredSelection(1, 2);
            current = confirmChanged(current, 1);
            ui(() -> check(!(Boolean) field(current, "isDarkMode"), "Restored light choice did not load light"));
            assertClosedAndUnchanged(1);
            status("PASS restored dark-to-light confirmation loads light Activity");

            ui(this::checkDetachedSwatches);
            status("PASS actual renderSwatches TextViews: all 22 light + 22 dark accents; parent-cell performClick updates draft; labels >=4.5; dark selected accent unchanged. This swatch section is detached/programmatic, NOT touchscreen testing.");
        } catch (Throwable error) {
            failure = unwrap(error);
        } finally {
            try {
                ui(() -> {
                    for (Activity activity : activities)
                        if (activity != null && !activity.isDestroyed()) activity.finish();
                });
                waitForIdleSync();
                if (preferencesCaptured) {
                    SharedPreferences.Editor restore = preferences.edit();
                    restoreValue(restore, themeKey, hadTheme, originalTheme);
                    restoreValue(restore, accentKey, hadAccent, originalAccent);
                    check(restore.commit(), "Failed restoring original isolated theme/accent preferences");
                    check(preferences.contains(themeKey) == hadTheme
                                    && preferences.contains(accentKey) == hadAccent,
                            "Original preference key presence not restored");
                    check(equal(originalTheme, preferences.getAll().get(themeKey))
                                    && equal(originalAccent, preferences.getAll().get(accentKey)),
                            "Original preference values not restored");
                }
            } catch (Throwable cleanup) {
                if (failure == null) failure = unwrap(cleanup);
                else failure.addSuppressed(unwrap(cleanup));
            }
        }
        Bundle result = new Bundle();
        if (failure == null) {
            result.putString("stream", "DARK UI PASS: real-window theme lifecycle/confirm/cancel/no-op checks; 44 detached accent swatch cases; isolated preference originals restored. Actual Activity orientation changes and injected screen taps verified; no physical sensor/hardware touchscreen certification.\n");
            finish(Activity.RESULT_OK, result);
        } else {
            result.putString("stream", "DARK UI FAIL\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkThemeTouches(int saved) throws Throwable {
        setInTouchMode(true);
        showAndSelect(saved);
        AlertDialog[] shown = new AlertDialog[1];
        ui(() -> {
            shown[0] = themeDialog(current);
            if (saved == 2) checkSurfaceBounds(shown[0]);
        });
        tapTitle(shown[0]);
        ui(() -> check(shown[0].isShowing(), "Inside theme title touch dismissed dialog"));
        View[] row = new View[1];
        java.util.concurrent.CountDownLatch selected = new java.util.concurrent.CountDownLatch(1);
        android.widget.AdapterView.OnItemClickListener[] original = new android.widget.AdapterView.OnItemClickListener[1];
        ui(() -> shown[0].getListView().setSelection(0));
        waitForIdleSync();
        getUiAutomation().waitForIdle(500L, TIMEOUT);
        ui(() -> {
            ListView list = shown[0].getListView();
            status("LIST first=" + list.getFirstVisiblePosition() + " count=" + list.getChildCount()
                    + " checked=" + list.getCheckedItemPosition());
            check(list.getFirstVisiblePosition() == 0, "First theme option did not scroll into view");
            row[0] = list.getChildAt(0);
            original[0] = list.getOnItemClickListener();
            check(original[0] != null, "Theme choice callback missing");
            list.setOnItemClickListener((parent, view, position, id) -> {
                original[0].onItemClick(parent, view, position, id);
                selected.countDown();
            });
        });
        tapView(row[0]);
        check(selected.await(TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS),
                "Injected theme touch did not reach original choice callback");
        ui(() -> shown[0].getListView().setOnItemClickListener(original[0]));
        ui(() -> {
            check(shown[0].isShowing(), "Theme option touch dismissed dialog");
            check(shown[0].getListView().getCheckedItemPosition() == 0,
                    "Injected theme option touch did not update choice");
            check(preferences.getInt(themeKey, -1) == saved, "Touch saved draft prematurely");
        });
        tapOutside(shown[0]);
        ui(() -> check(!shown[0].isShowing(), "Outside touch did not cancel theme dialog"));
        assertClosedAndUnchanged(saved);
        status("PASS injected screen taps theme mode=" + saved
                + ": inside title stays; option selects; outside cancels; draft/token released");
    }

    private void checkSurfaceBounds(Dialog dialog) {
        View decor = dialog.getWindow().getDecorView();
        check(decor.getBackground() instanceof android.graphics.drawable.InsetDrawable,
                "Dark surface lost Material wrapper");
        android.graphics.drawable.InsetDrawable inset =
                (android.graphics.drawable.InsetDrawable) decor.getBackground();
        android.graphics.Rect before = new android.graphics.Rect(inset.getDrawable().getBounds());
        android.graphics.Rect outer = new android.graphics.Rect(inset.getBounds());
        check(!before.isEmpty() && before.left > outer.left && before.top > outer.top
                        && before.right < outer.right && before.bottom < outer.bottom,
                "Painted surface was not inset inside window bounds");
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
        surface.setColor(DarkUiPalette.DIALOG_SURFACE);
        float density = current.getResources().getDisplayMetrics().density;
        surface.setCornerRadius(Math.round(24 * density));
        surface.setStroke(Math.round(density), DarkUiPalette.LINE);
        DialogSurfaceStyle.apply(dialog, surface);
        check(decor.getBackground() == inset && surface.getBounds().equals(before),
                "Repeated styling changed wrapper or painted bounds");
        status("PASS dark painted bounds remain inset after repeated styling: " + before);
    }

    private void checkReminderTouches() throws Throwable {
        Dialog[] shown = new Dialog[1];
        boolean[] closed = {false};
        ui(() -> {
            shown[0] = ReminderStatusDialog.create(current, () -> closed[0] = true);
            shown[0].show();
        });
        waitForIdleSync();
        try {
            tapTitle(shown[0]);
            ui(() -> check(shown[0].isShowing(), "Inside reminder title touch dismissed dialog"));
            View[] refresh = new View[1];
            ui(() -> refresh[0] = ((AlertDialog) shown[0]).getButton(AlertDialog.BUTTON_NEUTRAL));
            tapView(refresh[0]);
            ui(() -> check(shown[0].isShowing() && !closed[0], "Reminder refresh dismissed dialog"));
            tapOutside(shown[0]);
            ui(() -> check(!shown[0].isShowing() && closed[0], "Reminder outside cancel/cleanup failed"));
            status("PASS injected screen taps reminder: title stays; refresh stays; outside closes with cleanup");
        } finally {
            ui(() -> { if (shown[0].isShowing()) shown[0].dismiss(); });
        }
    }

    private void tapTitle(Dialog dialog) throws Throwable {
        View[] title = new View[1];
        ui(() -> {
            int id = current.getResources().getIdentifier("alertTitle", "id", current.getPackageName());
            title[0] = dialog.findViewById(id);
            check(title[0] != null, "Dialog title not found");
        });
        tapView(title[0]);
    }

    private void tapView(View view) throws Throwable {
        getUiAutomation().waitForIdle(500L, TIMEOUT);
        int[] point = new int[2];
        ui(() -> {
            check(view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0,
                    "Touch target not visible/laid out");
            view.getLocationOnScreen(point);
            point[0] += view.getWidth() / 2;
            point[1] += view.getHeight() / 2;
        });
        status("TOUCH x=" + point[0] + " y=" + point[1] + " focus=" + view.hasWindowFocus()
                + " currentDestroyed=" + current.isDestroyed() + " view=" + view.getClass().getSimpleName());
        injectTap(point[0], point[1]);
    }

    private void tapOutside(Dialog dialog) throws Throwable {
        int[] point = new int[2];
        ui(() -> {
            View decor = dialog.getWindow().getDecorView();
            android.graphics.Rect padding = new android.graphics.Rect();
            check(decor.getBackground() instanceof android.graphics.drawable.InsetDrawable,
                    "Material inset wrapper was removed");
            decor.getBackground().getPadding(padding);
            check(padding.top > 2, "No top outside inset available");
            decor.getLocationOnScreen(point);
            point[0] += decor.getWidth() / 2;
            point[1] += 1;
        });
        injectTap(point[0], point[1]);
    }

    private void injectTap(int x, int y) throws Throwable {
        getUiAutomation().waitForIdle(500L, TIMEOUT);
        long time = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time,
                android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 50L,
                android.view.MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try { sendPointerSync(down); sendPointerSync(up); }
        finally { down.recycle(); up.recycle(); }
        waitForIdleSync();
        getUiAutomation().waitForIdle(500L, TIMEOUT);
    }

    private void showAndSelect(int mode) throws Throwable {
        ui(() -> call(current, "showThemeModeDialog", new Class<?>[0]));
        waitForIdleSync();
        ui(() -> {
            AlertDialog dialog = themeDialog(current);
            ListView list = dialog.getListView();
            check(list != null && list.getAdapter().getCount() == 3, "Theme list must contain three modes");
            View row = list.getChildAt(mode - list.getFirstVisiblePosition());
            check(row != null, "Theme option is not laid out in real window");
            check(list.performItemClick(row, mode, list.getAdapter().getItemId(mode)),
                    "Real ListView did not dispatch item click");
            check(list.getCheckedItemPosition() == mode, "ListView checked item differs from click");
            check(field(current, "settingsDraftCapture") != null, "No draft capture installed");
            check(!((ModalCoordinator) field(current, "modalCoordinator")).canOpen(), "Popup did not own modal token");
        });
    }

    private Activity rotate(Activity old) throws Throwable {
        int before = old.getResources().getConfiguration().orientation;
        boolean landscape = before != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int requested = landscape ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        int expected = landscape ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(() -> old.setRequestedOrientation(requested));
            Activity replacement = awaitReplacement(monitor, old);
            ui(() -> check(replacement.getResources().getConfiguration().orientation == expected,
                    "Requested orientation did not actually change configuration"));
            status("PASS actual Activity orientation changed to " + (landscape ? "landscape" : "portrait"));
            return replacement;
        } finally { removeMonitor(monitor); }
    }

    private Activity recreate(Activity old) throws Throwable {
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(old::recreate);
            return awaitReplacement(monitor, old);
        } finally { removeMonitor(monitor); }
    }

    private Activity confirmChanged(Activity old, int mode) throws Throwable {
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(() -> check(themeDialog(old).getButton(AlertDialog.BUTTON_POSITIVE).performClick(),
                    "Confirm did not dispatch"));
            Activity replacement = awaitReplacement(monitor, old);
            ui(() -> {
                check(preferences.getInt(themeKey, -1) == mode, "Confirmed mode was not saved");
                check((Integer) field(replacement, "themeMode") == mode, "Replacement loaded wrong mode");
            });
            return replacement;
        } finally { removeMonitor(monitor); }
    }

    private Activity awaitReplacement(ActivityMonitor monitor, Activity old) throws Throwable {
        Activity replacement = waitForMonitorWithTimeout(monitor, TIMEOUT);
        check(replacement != null && replacement != old, "Timed out waiting for real Activity replacement");
        activities.add(replacement);
        waitForIdleSync();
        // AppCompat may also request recreation. Observe the actual latest monitored Activity,
        // never treat a soon-destroyed intermediate instance as a successful loaded theme.
        Activity latest = monitor.getLastActivity();
        if (latest != null && latest != old && latest != replacement) {
            replacement = latest;
            activities.add(replacement);
            waitForIdleSync();
        }
        final Activity ready = replacement;
        ui(() -> check(!ready.isFinishing() && !ready.isDestroyed(), "Replacement is not a live Activity"));
        return ready;
    }

    private void assertRestoredSelection(int selected, int saved) throws Throwable {
        ui(() -> {
            AlertDialog dialog = themeDialog(current);
            check(dialog.getListView().getCheckedItemPosition() == selected, "Tentative theme choice lost on recreation");
            check(preferences.getInt(themeKey, -1) == saved, "Tentative choice was saved before confirmation");
            check((Integer) field(current, "themeMode") == saved, "Tentative choice changed loaded theme");
            check(field(current, "settingsDraftCapture") != null, "Restored popup missing capture");
            check(field(current, "pendingSettingsRestore") == null, "Restored draft was not consumed");
            Bundle state = new Bundle();
            call(current, "onSaveInstanceState", new Class<?>[]{Bundle.class}, state);
            Bundle draft = state.getBundle("settings_draft");
            check(draft != null && "theme_mode".equals(draft.getString("kind"))
                    && draft.getInt("selected", -1) == selected, "Repeated state capture lost tentative mode");
        });
    }

    private void assertClosedAndUnchanged(int saved) throws Throwable {
        ui(() -> {
            check(preferences.getInt(themeKey, -1) == saved, "Unexpected saved theme after close");
            check(field(current, "settingsDraftCapture") == null, "Closed popup retained capture");
            check(field(current, "pendingSettingsRestore") == null, "Closed popup retained pending draft");
            check(((List<?>) field(current, "ownedDialogs")).isEmpty(), "Closed popup retained owned dialog");
            check(((ModalCoordinator) field(current, "modalCoordinator")).canOpen(), "Closed popup retained modal token");
            Bundle state = new Bundle();
            call(current, "onSaveInstanceState", new Class<?>[]{Bundle.class}, state);
            check(!state.containsKey("settings_draft"), "Closed theme popup would resurrect");
        });
    }

    private void checkDetachedSwatches() throws Throwable {
        check(UiAccentPalette.COLORS.length == 22, "Expected all 22 interface presets");
        for (boolean dark : new boolean[]{false, true}) {
            for (int preset = 0; preset < UiAccentPalette.COLORS.length; preset++) {
                int accent = UiAccentPalette.COLORS[preset][dark ? 1 : 0];
                PastelCourseSheet sheet = new PastelCourseSheet(current, dark, PastelAccent.of(accent, dark));
                LinearLayout grid = new LinearLayout(current);
                grid.setOrientation(LinearLayout.VERTICAL);
                int[] colors = {0xFF347B80, 0xFFAE695C};
                String[] names = {"Audit teal", "Audit coral"};
                PastelCourseSheet.Draft draft = new PastelCourseSheet.Draft();
                draft.color = colors[0];
                call(sheet, "renderSwatches", new Class<?>[]{LinearLayout.class, int[].class, String[].class,
                        PastelCourseSheet.Draft.class}, grid, colors, names, draft);
                verifySwatchLabels(sheet, grid, dark, accent, 0, "initial " + preset);
                ViewGroup row = (ViewGroup) grid.getChildAt(0);
                check(row.getChildAt(1).performClick(), "Parent color cell click not dispatched");
                check(draft.color == colors[1] && draft.colorManuallySelected, "Color click failed to update draft");
                verifySwatchLabels(sheet, grid, dark, accent, 1, "clicked " + preset);
                if (preset == 7 || preset == 4 || preset == 1)
                    status("PASS detached " + (dark ? "dark" : "light") + " preset=" + preset
                            + " representative Coral/Orange/Teal: selected label/cell/draft");
            }
        }
    }

    private void verifySwatchLabels(PastelCourseSheet sheet, LinearLayout grid, boolean dark,
                                   int accent, int selected, String description) throws Throwable {
        ViewGroup row = (ViewGroup) grid.getChildAt(0);
        int background = (Integer) field(sheet, "sheetBg");
        int ink = (Integer) field(sheet, "ink");
        for (int index = 0; index < 2; index++) {
            ViewGroup cell = (ViewGroup) row.getChildAt(index);
            check(cell.isSelected() == (index == selected), "Cell selected state mismatch " + description);
            check(cell.getChildAt(1) instanceof TextView, "Primary color name is not a real TextView");
            TextView label = (TextView) cell.getChildAt(1);
            check(CoursePalette.contrast(label.getCurrentTextColor(), background) >= 4.5,
                    "Primary color name below 4.5 contrast " + description);
            check(label.getCurrentTextColor() == (dark && index == selected ? accent : ink),
                    "Color name ink changed unexpectedly " + description);
        }
    }

    private AlertDialog themeDialog(Activity activity) throws Throwable {
        List<?> owned = (List<?>) field(activity, "ownedDialogs");
        AlertDialog found = null;
        for (Object candidate : owned) {
            if (candidate instanceof AlertDialog && ((Dialog) candidate).isShowing()) {
                check(found == null, "Multiple primary dialogs showing");
                found = (AlertDialog) candidate;
            }
        }
        check(found != null && found.getListView() != null, "Real theme AlertDialog not showing");
        check(found.getWindow() != null && found.getWindow().getDecorView().isAttachedToWindow(),
                "Theme dialog is not an attached actual window");
        return found;
    }

    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = unwrap(error); }
        });
        if (failure[0] != null) throw failure[0];
    }
    private static Object staticField(String name) throws Throwable {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
    private static Object field(Object owner, String name) throws Throwable {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(owner, args); }
        catch (InvocationTargetException wrapped) { throw unwrap(wrapped); }
    }
    private static Throwable unwrap(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void status(String message) {
        Bundle status = new Bundle();
        status.putString("stream", "DARK UI " + message + "\n");
        sendStatus(0, status);
    }
    private static boolean equal(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private static void restoreValue(SharedPreferences.Editor editor, String key, boolean present, Object value) {
        if (!present) editor.remove(key);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else throw new AssertionError("Unexpected original preference type: " + key);
    }
}
