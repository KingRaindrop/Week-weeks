# Behavioral regression harness: compile the actual scheduler and both receivers against
# deterministic in-memory Android service doubles. Does not test Android IPC or Keystore.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or newer.' }
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$out = Join-Path $root ('build\reminder-regression-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = @{
'android/annotation/SuppressLint.java' = @'
package android.annotation;
public @interface SuppressLint { String[] value(); }
'@
'android/Manifest.java' = @'
package android;
public class Manifest { public static class permission { public static final String POST_NOTIFICATIONS="post"; } }
'@
'android/os/Build.java' = @'
package android.os;
public class Build { public static class VERSION { public static int SDK_INT=33; } }
'@
'android/os/SystemClock.java' = @'
package android.os;
public class SystemClock { public static long elapsedRealtimeNanos() { return System.nanoTime(); } }
'@
'android/content/pm/PackageManager.java' = @'
package android.content.pm;
public class PackageManager { public static final int PERMISSION_GRANTED=0; }
'@
'android/net/Uri.java' = @'
package android.net;
public class Uri { public static Uri parse(String value) { return new Uri(); } }
'@
'android/util/Base64.java' = @'
package android.util;
public class Base64 {
 public static final int NO_WRAP=2, URL_SAFE=8;
 public static String encodeToString(byte[] b,int f){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
'@
'android/content/Intent.java' = @'
package android.content;
import java.util.*;
public class Intent {
 public static final String ACTION_BOOT_COMPLETED="android.intent.action.BOOT_COMPLETED", ACTION_MY_PACKAGE_REPLACED="android.intent.action.MY_PACKAGE_REPLACED";
 public static final int FLAG_ACTIVITY_NEW_TASK=1, FLAG_ACTIVITY_CLEAR_TOP=2, FLAG_ACTIVITY_SINGLE_TOP=4;
 private final Map<String,Object> extras=new HashMap<>(); private String action;
 public Intent() {} public Intent(Context c,Class<?> cls) {} public Intent(String action) { this.action=action; }
 public String getAction(){return action;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}
 public Intent putExtra(String k,int v){extras.put(k,v);return this;}
 public Intent putExtra(String k,long v){extras.put(k,v);return this;}
 public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}
 public boolean hasExtra(String k){return extras.containsKey(k);}
 public void removeExtra(String k){extras.remove(k);}
 public String getStringExtra(String k){return (String)extras.get(k);}
 public int getIntExtra(String k,int d){Object v=extras.get(k);return v instanceof Number?((Number)v).intValue():d;}
 public long getLongExtra(String k,long d){Object v=extras.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public Intent setFlags(int v){return this;} public Intent setData(android.net.Uri v){return this;}
}
'@
'android/content/SharedPreferences.java' = @'
package android.content;
import java.util.*;
public class SharedPreferences {
 private final Map<String,Object> data=new HashMap<>();
 public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
 public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
 public long getLong(String k,long d){return ((Number)data.getOrDefault(k,d)).longValue();}
 public Editor edit(){return new Editor();}
 public class Editor {
  private final Map<String,Object> pending=new HashMap<>();
  public Editor putString(String k,String v){pending.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  public Editor putLong(String k,long v){pending.put(k,v);return this;}
  public boolean commit(){data.putAll(pending);return true;}
 }
}
'@
'android/content/Context.java' = @'
package android.content;
import android.app.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public static final String ALARM_SERVICE="alarm", NOTIFICATION_SERVICE="notification";
 public final SharedPreferences prefs=new SharedPreferences();
 public final AlarmManager alarms=new AlarmManager();
 public final NotificationManager notifications=new NotificationManager();
 public int notificationPermission=0;
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
 public Object getSystemService(String name){return ALARM_SERVICE.equals(name)?alarms:notifications;}
 public int checkSelfPermission(String p){return notificationPermission;}
 /** Mirrors res/values/strings_view.xml (Simplified, the default) for the ids the checks use. */
 public String getString(int id){
  switch(id){
   case com.weekweeks.app.R.string.view_reminder_default_event: return "日程";
   case com.weekweeks.app.R.string.view_reminder_default_course: return "下一节课";
   case com.weekweeks.app.R.string.view_reminder_title_event: return "日程提醒";
   case com.weekweeks.app.R.string.view_reminder_title_course: return "课程提醒";
   case com.weekweeks.app.R.string.view_channel_name: return "提醒通知";
   case com.weekweeks.app.R.string.view_channel_description: return "课程与个人日程的开始提醒";
   default: return "";
  }
 }
}
'@
'android/content/BroadcastReceiver.java' = @'
package android.content;
public abstract class BroadcastReceiver { public abstract void onReceive(Context c,Intent i); }
'@
'android/app/PendingIntent.java' = @'
package android.app;
import android.content.*; import java.util.*;
public class PendingIntent {
 public static final int FLAG_NO_CREATE=1, FLAG_IMMUTABLE=2, FLAG_UPDATE_CURRENT=4;
 private static final Map<Context,Map<Integer,PendingIntent>> all=new IdentityHashMap<>();
 public final Context context; public final int code; public Intent intent;
 private PendingIntent(Context c,int code,Intent i){context=c;this.code=code;intent=i;}
 public static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){
  Map<Integer,PendingIntent> map=all.computeIfAbsent(c,k->new HashMap<>());
  if((flags&FLAG_NO_CREATE)!=0)return map.get(code);
  PendingIntent p=map.computeIfAbsent(code,k->new PendingIntent(c,code,i));p.intent=i;return p;
 }
 public static PendingIntent getActivity(Context c,int code,Intent i,int flags){return new PendingIntent(c,code,i);}
 public void cancel(){all.get(context).remove(code);}
}
'@
'android/app/AlarmManager.java' = @'
package android.app;
import java.util.*;
public class AlarmManager {
 public static final int RTC_WAKEUP=0;
 public static final String ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";
 public boolean allowed=true, fail=false; public int exactCalls=0, fallbackCalls=0;
 public final Map<Integer,Long> times=new HashMap<>();
 public final Map<Integer,PendingIntent> pending=new HashMap<>();
 public boolean canScheduleExactAlarms(){return allowed;}
 public void setExactAndAllowWhileIdle(int type,long at,PendingIntent p){if(fail)throw new SecurityException();exactCalls++;times.put(p.code,at);pending.put(p.code,p);}
 public void setAndAllowWhileIdle(int type,long at,PendingIntent p){if(fail)throw new SecurityException();fallbackCalls++;times.put(p.code,at);pending.put(p.code,p);}
 public void cancel(PendingIntent p){times.remove(p.code);pending.remove(p.code);}
 public void simulateSystemCancellation(){times.clear();pending.clear();}
}
'@
'android/app/Notification.java' = @'
package android.app;
import android.content.Context;
public class Notification {
 public static final String CATEGORY_REMINDER="reminder";
 public static final int DEFAULT_SOUND=1,DEFAULT_VIBRATE=2,PRIORITY_HIGH=1,VISIBILITY_PUBLIC=1,VISIBILITY_PRIVATE=0;
 public String title, text; public PendingIntent contentIntent;
 public static class BigTextStyle { public BigTextStyle bigText(String s){return this;} }
 public static class Builder {
  private final Notification n=new Notification();
  public Builder(Context c){} public Builder(Context c,String channel){}
  public Builder setSmallIcon(int v){return this;} public Builder setContentTitle(String s){n.title=s;return this;}
  public Builder setContentText(String s){n.text=s;return this;} public Builder setStyle(BigTextStyle s){return this;}
  public Builder setContentIntent(PendingIntent p){n.contentIntent=p;return this;} public Builder setAutoCancel(boolean v){return this;}
  public Builder setColor(int v){return this;} public Builder setCategory(String s){return this;}
  public Builder setDefaults(int v){return this;} public Builder setPriority(int v){return this;}
  public Builder setVisibility(int v){return this;} public Builder setPublicVersion(Notification p){return this;} public Notification build(){return n;}
 }
}
'@
'android/app/NotificationChannel.java' = @'
package android.app;
public class NotificationChannel {
 public String name, description; public int importance; public boolean vibration;
 public NotificationChannel(String id,String name,int importance){this.name=name;this.importance=importance;}
 public void setName(CharSequence s){name=s.toString();}
 public void setDescription(String s){description=s;} public void enableVibration(boolean b){vibration=b;}
}
'@
'android/app/NotificationManager.java' = @'
package android.app;
public class NotificationManager {
 public static final int IMPORTANCE_HIGH=4;
 public int count=0; public Notification last;
 public NotificationChannel channel;
 public NotificationChannel getNotificationChannel(String id){return channel;}
 public void createNotificationChannel(NotificationChannel c){channel=c;}
 public void notify(int id,Notification n){count++;last=n;}
}
'@
'org/json/JSONObject.java' = @'
package org.json;
import java.util.*;
// Object-graph codec double, NOT a JSON parser. Only scheduling behavior is under test.
public class JSONObject {
 private static final Map<String,Map<String,Object>> saved=new HashMap<>();
 private final Map<String,Object> data;
 public JSONObject(){data=new HashMap<>();}
 public JSONObject(String s){if(!saved.containsKey(s))throw new IllegalArgumentException("bad payload");data=new HashMap<>(saved.get(s));}
 public JSONObject put(String k,Object v){data.put(k,v);return this;}
 public long optLong(String k,long d){Object v=data.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public int optInt(String k,int d){return (int)optLong(k,d);}
 public String optString(String k,String d){return (String)data.getOrDefault(k,d);}
 public JSONArray optJSONArray(String k){return (JSONArray)data.get(k);}
 public String toString(){String key="{"+UUID.randomUUID()+"}";saved.put(key,new HashMap<>(data));return key;}
}
'@
'org/json/JSONArray.java' = @'
package org.json;
import java.util.*;
public class JSONArray {
 private final List<Object> data=new ArrayList<>();
 public JSONArray(){} public JSONArray(String s){throw new IllegalArgumentException("legacy codec not modeled");}
 public JSONArray put(Object v){data.add(v);return this;} public int length(){return data.size();}
 public JSONObject optJSONObject(int i){return data.get(i) instanceof JSONObject?(JSONObject)data.get(i):null;}
 public long optLong(int i,long d){return data.get(i) instanceof Number?((Number)data.get(i)).longValue():d;}
}
'@
'com/weekweeks/app/SecureStorage.java' = @'
package com.weekweeks.app;
final class SecureStorage { SecureStorage(String alias){} String encrypt(String s){return s;} String decrypt(String s){return s;} }
'@
'com/weekweeks/app/BuildConfig.java' = @'
package com.weekweeks.app;
final class BuildConfig { static boolean ENABLE_TEST_COURSE=true; }
'@
'com/weekweeks/app/MainActivity.java' = @'
package com.weekweeks.app;
import android.content.Context;
class MainActivity {
 static final String EXTRA_SHOW_REMINDER_POPUP="show", EXTRA_REMINDER_COURSE_NAME="name", EXTRA_REMINDER_COURSE_TIME="time", EXTRA_REMINDER_COURSE_ROOM="room", EXTRA_REMINDER_LEAD_MINUTES="lead";
 static int accentColorFor(Context c){return 0;}
}
'@
'com/weekweeks/app/AppLanguage.java' = @'
package com.weekweeks.app;
import android.content.Context;
/** Test double: the real class only rewrites the locale, which these checks do not exercise. */
final class AppLanguage { static Context wrap(Context base){return base;} }
'@
'com/weekweeks/app/R.java' = @'
package com.weekweeks.app;
public class R {
 public static class drawable { public static final int ic_launcher_foreground=1; }
 public static class string {
  public static final int view_reminder_default_event=1, view_reminder_default_course=2,
   view_reminder_title_event=3, view_reminder_title_course=4,
   view_channel_name=5, view_channel_description=6;
 }
}
'@
'com/weekweeks/app/ReminderRegressionTest.java' = @'
package com.weekweeks.app;
import android.app.*; import android.content.*; import android.os.Build; import java.util.*;
public final class ReminderRegressionTest {
 private static int checks;
 private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 private static ReminderScheduler.Entry entry(int code,long... times){return new ReminderScheduler.Entry(code,"Math","08:00","Room",10,times);}
 private static Context scheduled(int code,long... times){Context c=new Context();ReminderScheduler.replaceSchedule(c,List.of(entry(code,times)));return c;}
 private static Intent alarm(Context c,int code){return c.alarms.pending.get(code).intent;}
 private static void off(Context c){c.prefs.edit().putBoolean("notifications_enabled",false).commit();}
 private static void deliver(Context c,Intent i){new ClassReminderReceiver().onReceive(c,i);}
 private static void boot(Context c,String action){new ReminderBootReceiver().onReceive(c,new Intent(action));}
 private static void timingChecks(){
   long now=System.currentTimeMillis(), start=now+300_000L;
   check(ReminderTiming.startAt(now-1_200_000L,25)==start,"start comes from trigger plus lead, not delivery");
   check(ReminderTiming.startAt(-1L,25)==-1L&&ReminderTiming.startAt(now,-1)==-1L,"unknown and disabled timing rejected");
   check(ReminderTiming.startAt(Long.MAX_VALUE,1)==-1L,"start overflow rejected");
   check("5 分钟后开始".equals(ReminderTiming.statusText(true,start,-1L,now)),"late popup recalculates event lead");
   check("距离上课还有 5 分钟".equals(ReminderTiming.statusText(false,start,-1L,now)),"late course popup recalculates lead");
   check("1 分钟后开始".equals(ReminderTiming.statusText(true,now+1,-1L,now)),"positive sub-minute lead rounds up");
   check("现在开始".equals(ReminderTiming.statusText(true,now,-1L,now)),"exact start event wording");
   check("现在上课".equals(ReminderTiming.statusText(false,now,-1L,now)),"exact start course wording");
   check("已开始".equals(ReminderTiming.statusText(true,now-1,-1L,now)),"event without end is started not finished");
   check("已结束".equals(ReminderTiming.statusText(true,now-600_000,now,now)),"event end boundary");
   check("已下课".equals(ReminderTiming.statusText(false,now-600_000,now,now)),"course end boundary");
   check("日程提醒".equals(ReminderTiming.statusText(true,-1,-1,now))&&"课程提醒".equals(ReminderTiming.statusText(false,-1,-1,now)),"legacy popup neutral wording");
   Calendar clock=Calendar.getInstance();clock.set(2026,8,24,10,0,0);clock.set(Calendar.MILLISECOND,0);long ten=clock.getTimeInMillis();
   check(ReminderTiming.endAt(ten,"10:00–11:30")==ten+5_400_000L,"event range end recovered");
   check(ReminderTiming.endAt(ten,"10:00-11:30")==ten+5_400_000L,"course range end recovered");
   check(ReminderTiming.endAt(ten,"10:00")==-1&&ReminderTiming.endAt(ten,"09:00-11:00")==-1&&ReminderTiming.endAt(ten,"10:00-09:00")==-1,"unknown inconsistent or reversed end never invented");
   long first=now+600_000L;
   Context c=scheduled(7,first,first+600_000L);Intent timed=alarm(c,7);
   check(timed.getLongExtra(ClassReminderReceiver.EXTRA_START_AT,-1)==first+600_000L,"scheduled course carries occurrence start");
   deliver(c,timed);
   check(c.notifications.last.contentIntent.intent.getLongExtra(ClassReminderReceiver.EXTRA_START_AT,-1)==first+600_000L,"notification open carries original occurrence not next occurrence");
   Intent late=new Intent().putExtra(ClassReminderReceiver.EXTRA_KIND,ReminderScheduler.KIND_EVENT)
       .putExtra(ClassReminderReceiver.EXTRA_REMINDER_MINUTES,25).putExtra(ReminderScheduler.EXTRA_TRIGGER_AT,now-1_200_000L);
   c=new Context();ClassReminderReceiver.sendDebugNotification(c,late);
   check(c.notifications.last.text.startsWith("5 分钟后开始"),"legacy pending alarm recalculates delayed delivery using original trigger");
   check(c.notifications.last.contentIntent.intent.getLongExtra(ClassReminderReceiver.EXTRA_START_AT,-1)==start,"legacy pending alarm forwards recovered start");
   Intent ended=new Intent().putExtra(ClassReminderReceiver.EXTRA_START_AT,now-600_000L).putExtra(ClassReminderReceiver.EXTRA_END_AT,now-1L);
   c=new Context();ClassReminderReceiver.sendDebugNotification(c,ended);
   check("已下课".equals(c.notifications.last.text),"delayed delivery after end does not claim future start");
   check(c.notifications.last.contentIntent.intent.getLongExtra(ClassReminderReceiver.EXTRA_END_AT,-1)==now-1L,"open intent preserves known end");
   c=new Context();ClassReminderReceiver.sendDebugNotification(c,new Intent());
   check("课程提醒".equals(c.notifications.last.text),"missing timing falls back to neutral notification");
   check("提醒通知".equals(c.notifications.channel.name)&&c.notifications.channel.description.contains("日程"),"new channel covers courses and events");
   c.notifications.channel.name="课程提醒";c.notifications.channel.importance=1;c.notifications.channel.vibration=false;
   ClassReminderReceiver.ensureChannel(c, c.notifications);
   check("提醒通知".equals(c.notifications.channel.name)&&c.notifications.channel.importance==1&&!c.notifications.channel.vibration,"existing channel renamed without resetting preferences");
  }
  public static void main(String[] args){
  long first=System.currentTimeMillis()+600_000L, second=first+600_000L;
  Context c=scheduled(7,first,second); Intent old=alarm(c,7);
  ReminderScheduler.replaceSchedule(c,List.of(entry(7,first,second)));
  int calls=c.alarms.exactCalls; deliver(c,old);
  check(c.notifications.count==0,"old generation must not notify");check(c.alarms.exactCalls==calls,"old generation must not continue chain");
  c=scheduled(7,first);Intent missing=alarm(c,7);missing.putExtra(ReminderScheduler.EXTRA_REQUEST_CODE,999);deliver(c,missing);
  check(c.notifications.count==0,"unknown requestCode must not notify");
  c=scheduled(7,first);Intent noGeneration=alarm(c,7);noGeneration.removeExtra(ReminderScheduler.EXTRA_GENERATION);deliver(c,noGeneration);
  check(c.notifications.count==0,"missing generation is not a debug bypass");
  c=scheduled(0,first);Intent noCode=alarm(c,0);noCode.removeExtra(ReminderScheduler.EXTRA_REQUEST_CODE);deliver(c,noCode);
  check(c.notifications.count==0,"missing requestCode must not default to valid zero");
  c=scheduled(0,first);deliver(c,alarm(c,0));check(c.notifications.count==1,"explicit requestCode zero remains valid");
  c=scheduled(7,first,second);Intent disabled=alarm(c,7);off(c);calls=c.alarms.exactCalls;deliver(c,disabled);
  check(c.notifications.count==0&&c.alarms.exactCalls==calls,"disabled application blocks notification and continuation");
  c=scheduled(7,first);Intent cleared=alarm(c,7);ReminderScheduler.clear(c);deliver(c,cleared);
  check(c.notifications.count==0,"cleared schedule invalidates queued delivery");
  c=scheduled(7,first,second);Intent valid=alarm(c,7);deliver(c,valid);
  check(c.notifications.count==1,"current delivery notifies");check(c.alarms.times.get(7)==second,"current delivery schedules next trigger");
  c=scheduled(7,first);Intent last=alarm(c,7);calls=c.alarms.exactCalls;deliver(c,last);
  check(c.notifications.count==1,"last occurrence still notifies");check(c.alarms.exactCalls==calls,"last occurrence needs no later alarm");
  c=scheduled(7,first,second);c.notificationPermission=-1;deliver(c,alarm(c,7));
  check(c.notifications.count==0,"denied POST_NOTIFICATIONS blocks notification");check(c.alarms.times.get(7)==second,"permission denial cannot break continuation");
  c=scheduled(7,first,second);c.alarms.fail=true;deliver(c,alarm(c,7));
  check(c.notifications.count==1,"continuation failure must not suppress valid current notification");
  check(!c.prefs.getBoolean("class_reminder_last_submission_ok",true),"continuation failure remains observable");
  c=new Context();deliver(c,null);deliver(c,new Intent());check(c.notifications.count==0,"null and unversioned intents fail closed");
  c=scheduled(7,first);Intent corrupt=alarm(c,7);c.prefs.edit().putString("class_reminder_schedule_encrypted_v1","corrupt").commit();deliver(c,corrupt);
  check(c.notifications.count==0,"corrupt schedule rejects delivery");
  c=new Context();Intent debug=new Intent().putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME,"Debug");
  check(ClassReminderReceiver.sendDebugNotification(c,debug)&&c.notifications.count==1,"explicit debug entry works without generation");
  check(c.alarms.exactCalls==0,"debug notification must not change production schedule");
  BuildConfig.ENABLE_TEST_COURSE=false;
  check(!ClassReminderReceiver.sendDebugNotification(c,debug)&&c.notifications.count==1,"release cannot use debug bypass");BuildConfig.ENABLE_TEST_COURSE=true;
  off(c);check(!ClassReminderReceiver.sendDebugNotification(c,debug),"debug respects application switch");
  c=new Context();c.notificationPermission=-1;check(!ClassReminderReceiver.sendDebugNotification(c,debug),"debug reports permission denial");
  c=new Context();String token=ClassReminderReceiver.popupToken(c);
  check(token.length()>=32&&token.equals(ClassReminderReceiver.popupToken(c)),"popup token is stable per install");
  check(!token.equals(ClassReminderReceiver.popupToken(new Context())),"popup token differs between installs");
  check(ClassReminderReceiver.isTrustedPopup(c,new Intent().putExtra(ClassReminderReceiver.EXTRA_POPUP_TOKEN,token)),"own notification popup is trusted");
  check(!ClassReminderReceiver.isTrustedPopup(c,new Intent()),"popup without token is rejected");
  check(!ClassReminderReceiver.isTrustedPopup(c,new Intent().putExtra(ClassReminderReceiver.EXTRA_POPUP_TOKEN,"forged")),"forged popup token is rejected");
  check(!ClassReminderReceiver.isTrustedPopup(c,null),"null popup intent is rejected");
  c=scheduled(7,first);c.alarms.simulateSystemCancellation();calls=c.alarms.exactCalls;
  boot(c,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
  check(c.alarms.exactCalls==calls+1&&c.alarms.times.get(7)==first,"grant broadcast restores future alarm without activity");
  c=scheduled(7,first);c.alarms.simulateSystemCancellation();c.alarms.allowed=false;calls=c.alarms.exactCalls;
  boot(c,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
  check(c.alarms.exactCalls==calls&&c.alarms.fallbackCalls==0,"grant action requires permission still granted");
  c=scheduled(7,first);c.alarms.simulateSystemCancellation();off(c);calls=c.alarms.exactCalls;
  boot(c,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);boot(c,Intent.ACTION_BOOT_COMPLETED);boot(c,Intent.ACTION_MY_PACKAGE_REPLACED);
  check(c.alarms.exactCalls==calls,"all restore events respect application switch");
  c=scheduled(7,first);c.alarms.simulateSystemCancellation();c.alarms.allowed=false;boot(c,Intent.ACTION_BOOT_COMPLETED);
  check(c.alarms.fallbackCalls==1,"boot still supports inexact fallback");
  c=scheduled(7,first);c.alarms.simulateSystemCancellation();boot(c,Intent.ACTION_MY_PACKAGE_REPLACED);
  check(c.alarms.times.get(7)==first,"package update still restores alarms");
  c=new Context();ReminderScheduler.replaceSchedule(c,List.of(new ReminderScheduler.Entry(9,"Library","10:00","",30,new long[]{first},ReminderScheduler.KIND_EVENT)));
  Intent ev=alarm(c,9);check(ReminderScheduler.KIND_EVENT.equals(ev.getStringExtra(ClassReminderReceiver.EXTRA_KIND)),"event kind travels with the alarm");
  calls=c.alarms.exactCalls;deliver(c,ev);check(c.notifications.count==1&&"Library".equals(c.notifications.last.title),"event reminder notifies with its title");
  check(c.alarms.exactCalls==calls,"one-off event schedules no follow-up");
  c=new Context();ReminderScheduler.replaceSchedule(c,List.of(new ReminderScheduler.Entry(9,"Library","10:00","",30,new long[]{first},ReminderScheduler.KIND_EVENT)));
  c.alarms.simulateSystemCancellation();boot(c,Intent.ACTION_BOOT_COMPLETED);
  check(c.alarms.times.get(9)==first&&ReminderScheduler.KIND_EVENT.equals(alarm(c,9).getStringExtra(ClassReminderReceiver.EXTRA_KIND)),"boot restores event reminder with its kind");
  c=scheduled(7,first);calls=c.alarms.exactCalls;boot(c,"unknown");new ReminderBootReceiver().onReceive(c,null);
  check(c.alarms.exactCalls==calls,"unknown and null restore actions ignored");
  Build.VERSION.SDK_INT=30;boot(c,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
  check(c.alarms.exactCalls==calls,"grant action ignored before API31");Build.VERSION.SDK_INT=33;
  c=scheduled(7,first,second);c.notificationPermission=-1;c.alarms.simulateSystemCancellation();boot(c,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
  check(c.alarms.times.get(7)==first,"grant restoration independent of notification permission");
  timingChecks();
   System.out.println("ReminderRegressionTest: "+checks+" behavioral checks passed (actual scheduler + receivers; Android service doubles).");
 }
}
'@
}
$generated = @()
foreach ($entry in $sources.GetEnumerator()) {
    $path = Join-Path $out $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    Set-Content -LiteralPath $path -Value $entry.Value -Encoding UTF8
    $generated += $path
}
# Windows PowerShell UTF8 includes a BOM; javac rejects it. Rewrite generated test-only
# files with the explicit UTF8-no-BOM encoder. No production source is touched.
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($entry in $sources.GetEnumerator()) {
    [System.IO.File]::WriteAllText((Join-Path $out $entry.Key), $entry.Value, $utf8)
}
$production = @('TimetableRules.java','ReminderScheduler.java','ClassReminderReceiver.java','ReminderBootReceiver.java','PersonalEvent.java','ReminderTiming.java','UiLang.java') |
    ForEach-Object { Join-Path $root ('app\src\main\java\com\weekweeks\app\' + $_) }
& $javac -encoding UTF-8 --release 17 -d $out @generated @production
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java -cp $out com.weekweeks.app.ReminderRegressionTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
[xml] $manifest = Get-Content -LiteralPath (Join-Path $root 'app\src\main\AndroidManifest.xml') -Raw
$ns = New-Object System.Xml.XmlNamespaceManager($manifest.NameTable)
$ns.AddNamespace('android','http://schemas.android.com/apk/res/android')
$registration = $manifest.SelectSingleNode('//receiver[@android:name=".ReminderBootReceiver"]/intent-filter/action[@android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"]', $ns)
if ($null -eq $registration) { throw 'Grant broadcast is not registered in AndroidManifest.xml.' }
Write-Host 'Grant broadcast manifest registration passed.'
