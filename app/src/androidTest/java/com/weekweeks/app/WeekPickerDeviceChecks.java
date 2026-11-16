package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Locale;

/** Framework-only checks. Must never launch against the user's real application data. */
final class WeekPickerDeviceChecks {
    private WeekPickerDeviceChecks() { }

    static void run(Instrumentation instrumentation) throws Throwable {
        if (!"com.weekweeks.app.audit".equals(instrumentation.getTargetContext().getPackageName()))
            throw new AssertionError("Week picker checks require the isolated audit package");
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Throwable[] failure = {null};
        instrumentation.runOnMainSync(() -> {
            try {
                Calendar start = Calendar.getInstance();
                start.clear(); start.set(2026, Calendar.SEPTEMBER, 1);
                long viewed = WeekBrowseRules.selectWeek(start.getTimeInMillis(), 52, 31,
                        System.currentTimeMillis()).targetDateMillis;
                for (boolean dark : new boolean[]{false, true})
                    for (float scale : new float[]{1f, 1.5f, 2f})
                        for (String language : new String[]{"zh", "zh-TW", "en"})
                            for (int width : new int[]{280, 360, 600}) {
                                Configuration config = new Configuration(activity.getResources().getConfiguration());
                                config.fontScale = scale;
                                config.screenWidthDp = width;
                                config.setLocale(Locale.forLanguageTag(language));
                                Context context = new ContextThemeWrapper(activity.createConfigurationContext(config),
                                        com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
                                Dialog dialog = WeekPickerDialog.create(context, dark, null,
                                        start.getTimeInMillis(), 52, viewed, new WeekPickerDialog.Actions() {
                                            public void onSelect(WeekBrowseRules.Selection selection) { }
                                            public void onFindDate() { }
                                        }, null);
                                ViewGroup root = dialog.findViewById(android.R.id.content);
                                int pixels = Math.round(width * context.getResources().getDisplayMetrics().density);
                                root.measure(View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST));
                                root.layout(0, 0, pixels, root.getMeasuredHeight());
                                int[] counts = new int[2];
                                inspect(root, counts);
                                require(counts[0] == 52, "Not all calendar weeks are reachable");
                                require(counts[1] == 1, "Exactly one viewed week must be selected");
                                require(findText(root, context.getString(R.string.week_picker_current)) != null,
                                        "Current-week action missing");
                                require(findText(root, context.getString(R.string.week_picker_find_date)) != null,
                                        "Outside-semester date lookup missing");
                            }
                // Real host integration: a second title tap must not create another dialog.
                Method open = MainActivity.class.getDeclaredMethod("showWeekPickerDialog");
                open.setAccessible(true);
                java.lang.reflect.Field active = MainActivity.class.getDeclaredField("weekPickerDialog");
                active.setAccessible(true);
                open.invoke(activity);
                Dialog first = (Dialog) active.get(activity);
                require(first != null && first.isShowing(), "Week title did not open picker");
                open.invoke(activity);
                require(active.get(activity) == first, "Rapid title taps opened duplicate pickers");
                first.dismiss();
            } catch (Throwable error) { failure[0] = error; }
            finally { activity.finish(); }
        });
        if (failure[0] != null) throw failure[0];
        instrumentation.waitForIdleSync();
    }

    private static void inspect(View view, int[] counts) {
        if (view.isClickable() && view.getContentDescription() != null) {
            counts[0]++;
            if (view.isSelected()) counts[1]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) inspect(group.getChildAt(i), counts);
        }
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
