package com.weekweeks.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class OccurrenceRulesTest {
    private static final int MON=20300304,TUE=20300305,WED=20300306, NEXT_TUE=20300312;
    private static LessonSchedule term(){return new LessonSchedule(ScheduleDay.millis(MON),ScheduleDay.millis(20300331));}
    private static Course lesson(){Course c=new Course();c.id=123;c.name="Algebra";c.room="A";c.day=1;c.period=1;c.endPeriod=1;return c;}
    private static PersonalEvent event(int date){PersonalEvent e=new PersonalEvent();e.id=412;e.title="Study";ScheduleDay.set(e,date);e.startMinutes=510;e.endMinutes=570;e.remindMinutes=10;return e;}
    @Test public void moveAndCancelOnlyOriginalDateEvenAcrossWeeks(){
        Course c=lesson(); c.changes.put(TUE,new LessonSchedule.Change(TUE,NEXT_TUE,2,3,"B"));
        assertTrue(term().between(c,TUE,TUE).isEmpty()); List<Course> both=term().between(c,NEXT_TUE,NEXT_TUE);
        assertEquals(2,both.size()); assertEquals(NEXT_TUE,both.get(0).sourceDate);assertEquals("A",both.get(0).room);
        assertEquals(TUE,both.get(1).sourceDate);assertEquals(2,both.get(1).period);assertEquals("B",both.get(1).room);
        c.changes.put(TUE,new LessonSchedule.Change(TUE,0,0,0,""));assertEquals(1,term().between(c,NEXT_TUE,NEXT_TUE).size());
        assertFalse(term().active(c,WED)); c.weeks="2"; assertFalse(term().active(c,TUE));
    }
    @Test public void effectiveLessonAlarmsReplaceOldRoomTimeAndStop(){
        Course c=lesson(); c.changes.put(TUE,new LessonSchedule.Change(TUE,WED,2,2,"B"));
        c.changes.put(NEXT_TUE,new LessonSchedule.Change(NEXT_TUE,0,0,0,""));
        ReminderPlanBuilder.CourseDetails details=ReminderSourceRebuilder.courseDetails(ScheduleDay.millis(MON),ScheduleDay.millis(20300331),Arrays.asList("08:00-09:00","10:00-11:00"));
        List<ReminderScheduler.Entry> entries=ReminderPlanBuilder.build(Collections.singletonList(c),Collections.emptyList(),ScheduleDay.millis(MON),details);
        assertEquals(2,entries.size());assertEquals(ScheduleDay.time(20300319,450),entries.get(0).nextAfter(0));
        assertEquals("B",entries.get(1).room);assertEquals("10:00-11:00",entries.get(1).time);assertEquals(ScheduleDay.time(WED,590),entries.get(1).nextAfter(0));
    }
    @Test public void repeatMasksCutoffAndMovedOccurrence(){PersonalEvent e=event(MON);e.repeat=EventSeries.WORKDAYS;e.until=20300310;
        assertEquals(5,EventSeries.between(e,MON,20300312).size());e.repeat=EventSeries.DAILY;
        assertEquals(7,EventSeries.between(e,MON,20300312).size());e.repeat=EventSeries.WEEKLY;e.weekdays=1|4;
        PersonalEvent moved=event(20300320);moved.title="One change";e.changes.put(WED,moved);
        assertEquals(1,EventSeries.between(e,MON,20300312).size());PersonalEvent actual=EventSeries.between(e,20300320,20300320).get(0);
        assertEquals(WED,actual.sourceDate);assertEquals("One change",actual.title);assertEquals(MON,ScheduleDay.of(e));
        e.changes.put(MON,null);assertTrue(EventSeries.between(e,MON,20300312).isEmpty());
    }
    @Test public void yearlyDistanceNeedsNoSeriesMaterializationAndSingleReminderCanDiffer(){PersonalEvent e=event(MON);e.repeat=EventSeries.DAILY;e.changes.put(TUE,null);
        PersonalEvent moved=event(WED);moved.title="Moved";moved.remindMinutes=0;e.changes.put(MON,moved);
        List<ReminderScheduler.Entry> alarms=ReminderPlanBuilder.build(Collections.emptyList(),Collections.singletonList(e),ScheduleDay.millis(MON),null);
        assertEquals(2,alarms.size());assertEquals(ScheduleDay.time(WED,500),alarms.get(0).nextAfter(ScheduleDay.millis(MON)));
        assertEquals(ScheduleDay.time(WED,510),alarms.get(1).nextAfter(ScheduleDay.millis(MON)));
        assertEquals(ScheduleDay.time(20600101,500),alarms.get(0).nextAfter(ScheduleDay.millis(20600101)));
        assertEquals(0,alarms.get(0).triggerTimes.length);e.until=TUE;assertEquals(-1,RepeatAlarm.from(e).next(ScheduleDay.millis(MON),10));
    }
    @Test public void springGapIsSkippedWithoutShiftingLaterOccurrences(){TimeZone old=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));PersonalEvent e=event(20250308);e.repeat=EventSeries.DAILY;e.startMinutes=150;e.endMinutes=210;e.remindMinutes=0;
        assertEquals(ScheduleDay.time(20250310,150),RepeatAlarm.from(e).next(ScheduleDay.time(20250308,150),0));
    }finally{TimeZone.setDefault(old);}}
    @Test public void checkEffectiveDatesAndReportExactOverlapNotCardHeight(){Course c=lesson();c.changes.put(TUE,new LessonSchedule.Change(TUE,WED,1,1,"B"));
        PersonalEvent e=event(TUE);e.repeat=EventSeries.DAILY;e.until=WED;e.showInTimetable=false;
        List<ScheduleConflicts.Hit> hits=ScheduleConflicts.find(term().between(c,TUE,WED),Collections.singletonList(e),Collections.singletonList("08:00-09:00"),false);
        assertEquals(1,hits.size());assertEquals(30,hits.get(0).minutes);assertEquals(WED,hits.get(0).date);assertEquals("Algebra",hits.get(0).name);
        assertEquals(-1,ScheduleConflicts.overlap(480,540,540,600));assertEquals(0,ScheduleConflicts.overlap(480,540,500,-1));assertEquals(-1,ScheduleConflicts.overlap(480,540,540,-1));
    }
    @Test public void emptySelectionAndImpossibleCutoffAreRejected(){PersonalEvent e=event(TUE);e.repeat=EventSeries.WEEKLY;assertNotNull(e.validate());e.weekdays=1;e.until=WED;assertNotNull(e.validate());e.until=20300311;assertNull(e.validate());}
}
