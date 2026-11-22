package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Weekly masks, including all seven days and weekdays. The cutoff is inclusive. */
final class EventSeries {
    static final int ONCE=0, DAILY=1, WORKDAYS=2, WEEKLY=3;
    private EventSeries() { }
    static int mask(PersonalEvent e) { return e.repeat == DAILY ? 127 : e.repeat == WORKDAYS ? 31 : e.weekdays; }
    static boolean scheduled(PersonalEvent e,int date) {
        int start=ScheduleDay.of(e);
        return e.repeat==ONCE ? date==start : date>=start && date<=(e.until==0?ScheduleDay.LAST:e.until)
                && (mask(e) & (1<<ScheduleDay.weekday(date)))!=0;
    }
    static PersonalEvent occurrence(PersonalEvent e,int source) {
        if (!scheduled(e,source)) return null;
        PersonalEvent value = e.changes.get(source);
        if (value==null && e.changes.containsKey(source)) return null;
        PersonalEvent item=(value==null?e:value).copyFields();
        if(value==null) ScheduleDay.set(item,source);
        item.id=e.id; item.sourceDate=e.repeat==ONCE?0:source;
        item.repeat=ONCE; item.weekdays=0; item.until=0;
        return item;
    }
    static List<PersonalEvent> between(PersonalEvent e,int from,int to) {
        List<PersonalEvent> result=new ArrayList<>();
        if(e.repeat==ONCE) { int date=ScheduleDay.of(e); if(date>=from && date<=to) result.add(e); return result; }
        int first=Math.max(from,ScheduleDay.of(e)), last=Math.min(to,e.until==0?ScheduleDay.LAST:e.until);
        if(first<=last) {
            Calendar c=ScheduleDay.calendar(first);
            for(int day=first;day<=last;c.add(Calendar.DATE,1),day=ScheduleDay.of(c)) {
                if((mask(e)&(1<<((c.get(Calendar.DAY_OF_WEEK)+5)%7)))!=0 && !e.changes.containsKey(day)) {
                    PersonalEvent item=e.copyFields(); ScheduleDay.set(item,day); item.sourceDate=day;
                    item.repeat=ONCE; item.weekdays=0; item.until=0; result.add(item);
                }
            }
        }
        for(Map.Entry<Integer,PersonalEvent> ch:e.changes.entrySet()) if(ch.getValue()!=null) {
            int date=ScheduleDay.of(ch.getValue());
            if(date>=from && date<=to) { PersonalEvent occurrence=occurrence(e,ch.getKey()); if(occurrence!=null) result.add(occurrence); }
        }
        return result;
    }
    /** Next normal occurrence; jumps straight to the requested date, skipping individual edits. */
    static PersonalEvent normalFrom(PersonalEvent e,int date,boolean reverse) {
        int first=ScheduleDay.of(e), last=e.until==0?ScheduleDay.LAST:e.until;
        int day=reverse?Math.min(last,date):Math.max(first,date);
        if(mask(e)==0 || day<first || day>last) return null;
        Calendar c=ScheduleDay.calendar(day);
        for(;day>=first && day<=last;c.add(Calendar.DATE,reverse?-1:1),day=ScheduleDay.of(c)) {
            if((mask(e)&(1<<((c.get(Calendar.DAY_OF_WEEK)+5)%7)))!=0 && !e.changes.containsKey(day)) return occurrence(e,day);
        }
        return null;
    }
    /** My events lists one row per series, showing the next active occurrence or its latest past one. */
    static PersonalEvent representative(PersonalEvent e,long now) {
        if(e.repeat==ONCE) return e;
        int today=ScheduleDay.at(now);
        PersonalEvent next=normalFrom(e,today,false);
        if(next!=null && next.isPast(now)) next=normalFrom(e,ScheduleDay.shift(today,1),false);
        PersonalEvent past=normalFrom(e,today,true);
        for(int source:e.changes.keySet()) {
            PersonalEvent p=occurrence(e,source); if(p==null) continue;
            if(!p.isPast(now) && (next==null || p.startMillis()<next.startMillis())) next=p;
            if(p.isPast(now) && (past==null || p.startMillis()>past.startMillis())) past=p;
        }
        return next!=null?next:past;
    }
    static String validate(PersonalEvent e) {
        if(e.repeat<0 || e.repeat>WEEKLY || e.weekdays<0 || e.weekdays>127 || (e.repeat==WEEKLY && e.weekdays==0))
            return UiLang.t("请选择重复的星期", "請選擇重複的星期", "Select at least one weekday");
        if(e.repeat!=ONCE && e.until!=0 && (!ScheduleDay.valid(e.until) || e.until<ScheduleDay.of(e)))
            return UiLang.t("截止日期不能早于开始日期", "截止日期不能早於開始日期", "The end date cannot precede the start date");
        if(e.repeat!=ONCE && !hasScheduledDate(e)) return UiLang.t("所选日期范围内没有重复日程", "所選日期範圍內沒有重複日程", "No selected weekday falls in this date range");
        return null;
    }
    private static boolean hasScheduledDate(PersonalEvent e) {
        int day=ScheduleDay.of(e), last=e.until==0?ScheduleDay.LAST:e.until;
        for(int i=0;i<7 && day<=last;i++,day=ScheduleDay.shift(day,1)) if(scheduled(e,day)) return true;
        return false;
    }
}
