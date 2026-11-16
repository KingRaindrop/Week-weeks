package com.weekweeks.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Rebuilds local wall-clock reminders from full source data after time or time-zone changes. */
final class ReminderSourceRebuilder {
    private ReminderSourceRebuilder() { }

    static void rebuild(Context context) { rebuild(context, System.currentTimeMillis()); }

    static void rebuild(Context context, long now) {
        if (!ReminderScheduler.notificationsEnabled(context)) {
            ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.APP_REMINDERS_OFF);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(StoredTimetableSnapshot.PREFS_NAME, Context.MODE_PRIVATE);
        if (!AcademicCalendarStore.initializeLegacyZone(prefs)) {
            ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return;
        }
        StoredTimetableSnapshot snapshot = StoredTimetableSnapshot.load(prefs);
        ReminderPlanBuilder.CourseDetails details = courseDetails(AcademicCalendarStore.startMillis(prefs),
                AcademicCalendarStore.endMillis(prefs), snapshot.periodTimes);
        List<ReminderScheduler.Entry> entries = ReminderPlanBuilder.build(snapshot.courses.items,
                snapshot.events.items, now, details);
        ReminderScheduler.replaceSchedule(context, entries, snapshot.courses.readable, snapshot.events.readable);
    }

    /** Same period span and lead-time rules used by the activity, independent of Android services. */
    static ReminderPlanBuilder.CourseDetails courseDetails(long termStart, long termEnd, List<String> periodTimes) {
        final List<String> periods = new ArrayList<>(periodTimes);
        final int totalWeeks = termEnd < termStart ? 20 : Math.max(1, AgendaDates.week(termStart, termEnd));
        return new ReminderPlanBuilder.CourseDetails() {
            @Override public long[] triggerTimes(Course course, long now) {
                TimetableRules.WeekParseResult weeks = TimetableRules.parseWeeks(course.weeks);
                if (!weeks.valid || periods.isEmpty()) return new long[0];
                String[] start = periods.get(clamp(course.period, 1, periods.size()) - 1).split("-");
                int minutes = course.customStartMinutes >= 0 ? course.customStartMinutes
                        : start.length == 2 ? StoredTimetableSnapshot.clockMinutes(start[0]) : -1;
                return TimetableRules.reminderTriggerTimes(termStart, termEnd, totalWeeks, course.day,
                        minutes, leadMinutes(course), weeks.explicit ? weeks.weeks : null, now);
            }
            @Override public String timeLabel(Course course) {
                if (course.customStartMinutes >= 0 && course.customEndMinutes >= 0)
                    return PersonalEvent.formatTime(course.customStartMinutes) + "-" + PersonalEvent.formatTime(course.customEndMinutes);
                if (periods.isEmpty()) return "";
                String[] start = periods.get(clamp(course.period, 1, periods.size()) - 1).split("-");
                String[] end = periods.get(clamp(course.endPeriod, 1, periods.size()) - 1).split("-");
                return start.length == 2 && end.length == 2 ? start[0] + "-" + end[1]
                        : periods.get(clamp(course.period, 1, periods.size()) - 1);
            }
            @Override public int leadMinutes(Course course) {
                return course.period == 1 || course.period == 5 || course.period == 9 ? 30 : 10;
            }
        };
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
