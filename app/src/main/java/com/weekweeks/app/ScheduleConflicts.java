package com.weekweeks.app;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Cross-type warnings use the actual local date and clock minutes, never a card's minimum height. */
final class ScheduleConflicts {
    static final class Hit {
        final int date, minutes, start, end;
        final String name;
        Hit(int date,String name,int minutes,int start,int end) { this.date=date; this.name=name; this.minutes=minutes; this.start=start; this.end=end; }
    }
    private ScheduleConflicts() { }
    /** -1 = no overlap; zero = an event with no end starts during the course. */
    static int overlap(int a,int b,int start,int end) {
        if(a<0 || b<=a) return -1;
        if(end==PersonalEvent.NO_END) return start>=a && start<b ? 0 : -1;
        int minutes=Math.min(b,end)-Math.max(a,start); return minutes>0?minutes:-1;
    }
    static List<Hit> find(List<Course> lessons,List<PersonalEvent> events,List<String> periods,boolean editingCourse) {
        List<Hit> hits=new ArrayList<>();
        if(lessons.isEmpty() || events.isEmpty()) return hits;
        TreeMap<Integer,List<Course>> days=new TreeMap<>();
        for(Course lesson:lessons) if(!lesson.temporary && !lesson.invalidWeeks) {
            List<Course> day=days.get(lesson.actualDate); if(day==null){day=new ArrayList<>();days.put(lesson.actualDate,day);} day.add(lesson);
        }
        if(days.isEmpty()) return hits;
        for(PersonalEvent series:events) for(PersonalEvent event:EventSeries.between(series,days.firstKey(),days.lastKey())) {
            int date=ScheduleDay.of(event); List<Course> day=days.get(date); if(day==null) continue;
            for(Course c:day) {
                if(c.period<1 || c.endPeriod>periods.size()) continue;
                int start=c.customStartMinutes>=0?c.customStartMinutes:StoredTimetableSnapshot.clockMinutes(periods.get(c.period-1).split("-")[0]);
                int end=c.customEndMinutes>=0?c.customEndMinutes:StoredTimetableSnapshot.clockMinutes(periods.get(c.endPeriod-1).split("-")[1]);
                int minutes=overlap(start,end,event.startMinutes,event.endMinutes);
                if(minutes>=0) hits.add(new Hit(date,editingCourse?event.title:c.name,minutes,
                        Math.max(start,event.startMinutes),minutes==0?-1:Math.min(end,event.endMinutes)));
            }
        }
        java.util.Collections.sort(hits,(a,b)-> {int d=Integer.compare(a.date,b.date);return d!=0?d:Integer.compare(a.start,b.start);});
        return hits;
    }
}
