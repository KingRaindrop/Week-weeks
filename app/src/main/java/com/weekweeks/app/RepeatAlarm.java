package com.weekweeks.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** On-demand alarm chain: no end date does not require storing years of future alarms. */
final class RepeatAlarm {
    final int start, until, mask, minute, endMinute;
    final Set<Integer> excluded;
    RepeatAlarm(int start,int until,int mask,int minute,int endMinute,Set<Integer> excluded) {
        this.start=start; this.until=until; this.mask=mask; this.minute=minute; this.endMinute=endMinute;
        this.excluded=new HashSet<>(excluded);
    }
    static RepeatAlarm from(PersonalEvent series) {
        return new RepeatAlarm(ScheduleDay.of(series),series.until,EventSeries.mask(series),series.startMinutes,series.endMinutes,series.changes.keySet());
    }
    long next(long after,int lead) {
        int day=Math.max(start,ScheduleDay.at(after + lead*60000L)), last=until==0?ScheduleDay.LAST:until;
        java.util.Calendar c=ScheduleDay.calendar(day);
        for(;day<=last;c.add(java.util.Calendar.DATE,1),day=ScheduleDay.of(c)) {
            if((mask & (1<<((c.get(java.util.Calendar.DAY_OF_WEEK)+5)%7)))==0 || excluded.contains(day)) continue;
            long at=ScheduleDay.time(day,minute);
            if(at<=0 || (endMinute>=0 && ScheduleDay.time(day,endMinute)<=at)) continue;
            long trigger=at-lead*60000L; if(trigger>after) return trigger;
        }
        return -1;
    }
    JSONObject encode() throws Exception {
        JSONArray skips=new JSONArray(); for(int d:excluded) skips.put(d);
        return new JSONObject().put("start",start).put("until",until).put("mask",mask).put("minute",minute).put("end",endMinute).put("skips",skips);
    }
    static RepeatAlarm decode(JSONObject o) {
        if(o==null) return null;
        Set<Integer> skips=new HashSet<>(); JSONArray a=o.optJSONArray("skips");
        if(a!=null) for(int i=0;i<a.length();i++) skips.add((int)a.optLong(i,0));
        int start=o.optInt("start",0), until=o.optInt("until",0), mask=o.optInt("mask",0), minute=o.optInt("minute",-1), end=o.optInt("end",-1);
        if(!ScheduleDay.valid(start) || (until!=0 && (!ScheduleDay.valid(until)||until<start)) || mask<=0 || mask>127
                || minute<0 || minute>=1440 || (end!=-1 && (end<=minute || end>=1440))) throw new IllegalArgumentException("Invalid repeat alarm");
        return new RepeatAlarm(start,until,mask,minute,end,skips);
    }
}
