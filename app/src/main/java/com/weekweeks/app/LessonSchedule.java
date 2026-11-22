package com.weekweeks.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies date-specific edits to the original occurrences, never to the other teaching weeks. */
final class LessonSchedule {
    static final class Change {
        final int source, target, first, last;
        final String room;
        Change(int source, int target, int first, int last, String room) {
            this.source = source; this.target = target; this.first = first; this.last = last; this.room = room;
        }
    }
    private final long startMillis;
    final int start, end;
    private final int[] mondays;
    LessonSchedule(long termStart, long termEnd) {
        startMillis = termStart; start = ScheduleDay.at(termStart); end = ScheduleDay.at(termEnd);
        int weeks = Math.min(TimetableRules.MAX_WEEK, Math.max(0, AgendaDates.week(termStart, termEnd)));
        mondays = new int[weeks];
        for (int i=0;i<weeks;i++) mondays[i] = ScheduleDay.at(TimetableRules.courseDateMillisForWeek(termStart,i+1,0));
    }
    /** Result contains the original date and actual date, even after moving across weeks. */
    List<Course> between(Course series, int from, int to) {
        List<Course> out = new ArrayList<>();
        if (series.temporary || from > end || to < start) return out;
        TimetableRules.WeekParseResult rule = TimetableRules.parseWeeks(series.weeks);
        int firstWeek = Math.max(1, AgendaDates.week(startMillis, ScheduleDay.millis(Math.max(start,from))));
        int lastWeek = Math.min(mondays.length, AgendaDates.week(startMillis, ScheduleDay.millis(Math.min(end,to))));
        for (int w=firstWeek; w<=lastWeek; w++) {
            if (rule.explicit && !series.invalidWeeks && !rule.weeks[w]) continue;
            int source = ScheduleDay.shift(mondays[w-1],series.day);
            if (source < start || source > end || source < from || source > to || series.changes.containsKey(source)) continue;
            out.add(project(series,source,null));
        }
        for (Change change : series.changes.values()) {
            if (change.target != 0 && change.target >= from && change.target <= to && active(series,change.source,rule)
                    && change.target >= start && change.target <= end) out.add(project(series,change.source,change));
        }
        return out;
    }
    boolean active(Course series,int source) { return active(series,source,TimetableRules.parseWeeks(series.weeks)); }
    private boolean active(Course series,int source,TimetableRules.WeekParseResult rule) {
        if (source < start || source > end || ScheduleDay.weekday(source) != series.day) return false;
        int week = AgendaDates.week(startMillis, ScheduleDay.millis(source));
        return week>=1 && week<=mondays.length && (series.invalidWeeks || (rule.valid && (!rule.explicit || rule.weeks[week])));
    }
    static Course project(Course series, int source, Change change) {
        Course c = series.copyFields(); c.sourceDate=source; c.actualDate=change==null?source:change.target;
        if (change!=null && change.target!=0) {
            c.day=ScheduleDay.weekday(change.target); c.period=change.first; c.endPeriod=change.last; c.room=change.room;
        }
        return c;
    }
}
