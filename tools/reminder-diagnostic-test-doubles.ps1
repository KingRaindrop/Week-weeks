# Loaded only by verify-reminders.ps1. Android doubles for ACTUAL diagnostic/scheduler/receiver code.
# They model service outcomes and PendingIntent identity, not Binder, Doze, OEM policy or Keystore.
$sources['android/net/Uri.java'] = @'
package android.net;
public class Uri {
 private final String value; private Uri(String value){this.value=value;}
 public static Uri parse(String value){return new Uri(value);}
 public boolean equals(Object o){return o instanceof Uri && value.equals(((Uri)o).value);}
 public int hashCode(){return value.hashCode();} public String toString(){return value;}
}
'@
$sources['android/content/Intent.java'] = $sources['android/content/Intent.java'].Replace('private String action;', 'private String action; private android.net.Uri data;')
$sources['android/content/Intent.java'] = $sources['android/content/Intent.java'].Replace('public String getAction(){return action;}', 'public String getAction(){return action;} public Intent setAction(String a){action=a;return this;} public android.net.Uri getData(){return data;} public Intent(String a,android.net.Uri d){action=a;data=d;} public Intent addFlags(int f){return this;}')
$sources['android/content/Intent.java'] = $sources['android/content/Intent.java'].Replace('setData(android.net.Uri v){return this;}', 'setData(android.net.Uri v){data=v;return this;}')
# SharedPreferences failure/rollback behavior is supplied by the base harness.
$sources['android/content/Context.java'] = $sources['android/content/Context.java'].Replace('ACTIVITY_SERVICE="activity";', 'ACTIVITY_SERVICE="activity", POWER_SERVICE="power";')
$sources['android/content/Context.java'] = $sources['android/content/Context.java'].Replace('public int notificationPermission=0;', 'public int notificationPermission=0; public boolean alarmUnavailable=false,notificationUnavailable=false; public final java.util.Map<String,SharedPreferences> namespaces=new java.util.HashMap<>(); public String getPackageName(){return "com.weekweeks.app";} public String getString(int id,Object... args){return getString(id);} ')
$sources['android/content/Context.java'] = $sources['android/content/Context.java'].Replace('return prefs;', 'return "week_weeks".equals(name)?prefs:namespaces.computeIfAbsent(name,k->new SharedPreferences());')
$sources['android/content/Context.java'] = $sources['android/content/Context.java'].Replace('return ALARM_SERVICE.equals(name)?alarms:ACTIVITY_SERVICE.equals(name)?activities:notifications;', 'if(POWER_SERVICE.equals(name))return new android.os.PowerManager(); if(ACTIVITY_SERVICE.equals(name))return activities; return ALARM_SERVICE.equals(name)?(alarmUnavailable?null:alarms):(notificationUnavailable?null:notifications);')
$sources['android/app/PendingIntent.java'] = @'
package android.app;
import android.content.*; import java.util.*;
public class PendingIntent {
 public static final int FLAG_NO_CREATE=1,FLAG_IMMUTABLE=2,FLAG_UPDATE_CURRENT=4;
 private static final Map<AlarmManager,Map<String,PendingIntent>> all=new IdentityHashMap<>();
 public final Context context;public final int code;public Intent intent;private final String key;
 private PendingIntent(Context c,int code,Intent i,String key){context=c;this.code=code;intent=i;this.key=key;}
 public static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){
  String key=code+":"+i.getAction()+":"+i.getData();Map<String,PendingIntent> map=all.computeIfAbsent(c.alarms,k->new HashMap<>());
  if((flags&FLAG_NO_CREATE)!=0)return map.get(key);
  PendingIntent p=map.computeIfAbsent(key,k->new PendingIntent(c,code,i,key));p.intent=i;return p;
 }
 public static PendingIntent getActivity(Context c,int code,Intent i,int flags){return new PendingIntent(c,code,i,"activity");}
 public void cancel(){Map<String,PendingIntent> map=all.get(context.alarms);if(map!=null)map.remove(key);}
 public static void simulateForceStop(Context c){all.remove(c.alarms);c.alarms.simulateSystemCancellation();}
}
'@
$sources['android/app/AlarmManager.java'] = $sources['android/app/AlarmManager.java'].Replace('public boolean allowed=true, fail=false;', 'public boolean allowed=true, fail=false, failExactOnly=false;')
$sources['android/app/AlarmManager.java'] = $sources['android/app/AlarmManager.java'].Replace('if(fail)throw new SecurityException();exactCalls++;', 'if(fail||failExactOnly)throw new SecurityException();exactCalls++;')
$sources['android/app/NotificationChannel.java'] = @'
package android.app;
public class NotificationChannel {
 public String name,description,group;public int importance;public boolean vibration;public android.net.Uri sound=android.net.Uri.parse("default://sound");
 public NotificationChannel(String id,String name,int importance){this.name=name;this.importance=importance;}
 public void setName(CharSequence s){name=s.toString();} public void setDescription(String s){description=s;}
 public void enableVibration(boolean b){vibration=b;} public int getImportance(){return importance;}
 public String getGroup(){return group;} public android.net.Uri getSound(){return sound;} public boolean shouldVibrate(){return vibration;}
}
'@
$sources['android/app/NotificationChannelGroup.java'] = @'
package android.app;
public class NotificationChannelGroup {public boolean blocked;public boolean isBlocked(){return blocked;}}
'@
$sources['android/app/NotificationManager.java'] = @'
package android.app;
public class NotificationManager {
 public static final int IMPORTANCE_NONE=0,IMPORTANCE_DEFAULT=3,IMPORTANCE_HIGH=4,INTERRUPTION_FILTER_ALL=1,INTERRUPTION_FILTER_UNKNOWN=0;
 public int count=0,filter=1;public boolean enabled=true,failPost=false;public Notification last;
 public NotificationChannel channel;public NotificationChannelGroup group;
 public NotificationChannel getNotificationChannel(String id){return channel;}
 public NotificationChannelGroup getNotificationChannelGroup(String id){return group;}
 public int getCurrentInterruptionFilter(){return filter;}
 public void createNotificationChannel(NotificationChannel c){channel=c;}
 public void notify(int id,Notification n){if(failPost)throw new SecurityException();count++;last=n;}
 public void notify(String tag,int id,Notification n){notify(id,n);}
}
'@
$sources['androidx/core/app/NotificationManagerCompat.java'] = @'
package androidx.core.app;
import android.content.Context;
public class NotificationManagerCompat {
 private final Context c; private NotificationManagerCompat(Context c){this.c=c;}
 public static NotificationManagerCompat from(Context c){return new NotificationManagerCompat(c);}
 public boolean areNotificationsEnabled(){return c.notifications.enabled;}
}
'@
$sources['android/os/PowerManager.java'] = @'
package android.os;
public class PowerManager {public boolean isIgnoringBatteryOptimizations(String pkg){return false;}}
'@
$sources['android/provider/Settings.java'] = @'
package android.provider;
public class Settings {
 public static final String ACTION_APP_NOTIFICATION_SETTINGS="notifications",EXTRA_APP_PACKAGE="package",ACTION_CHANNEL_NOTIFICATION_SETTINGS="channel",EXTRA_CHANNEL_ID="channel_id",ACTION_REQUEST_SCHEDULE_EXACT_ALARM="exact",ACTION_APPLICATION_DETAILS_SETTINGS="app";
}
'@
$sources['android/text/format/DateFormat.java'] = @'
package android.text.format;
import android.content.Context;
public class DateFormat {
 public static java.text.DateFormat getDateFormat(Context c){return new java.text.SimpleDateFormat("yyyy-MM-dd");}
 public static java.text.DateFormat getTimeFormat(Context c){return new java.text.SimpleDateFormat("HH:mm");}
}
'@
# Generate resource identifiers from the dedicated XML instead of maintaining a parallel ID list.
[xml]$diagResources = Get-Content -LiteralPath (Join-Path $root 'app/src/main/res/values/strings_reminder_diagnostics.xml') -Raw
$diagFields = ''; $diagId = 1000
foreach ($resource in $diagResources.resources.string) {
    $diagFields += 'public static final int ' + $resource.name + '=' + $diagId + ';'; $diagId++
}
$sources['com/weekweeks/app/R.java'] = $sources['com/weekweeks/app/R.java'].Replace('public static class string {', 'public static class string {' + $diagFields)
$sources['com/weekweeks/app/ReminderDiagnosticRegressionTest.java'] = @'
package com.weekweeks.app;
import android.app.*;import android.content.*;import android.os.Build;import android.os.SystemClock;import java.util.*;
import static com.weekweeks.app.ReminderDiagnosticRules.Code;
public class ReminderDiagnosticRegressionTest {
 static int checks;static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 static SharedPreferences diagnostics(Context c){return c.getSharedPreferences("week_weeks_reminder_diagnostics_v1",0);}
 static void received(Context c,Intent i){new ClassReminderReceiver().onReceive(c,i);}
 static void expect(Context c,String stage,Code code){check(code.name().equals(diagnostics(c).getString(stage+"_code","")),stage+" expected "+code);}
 static Context production(boolean exact){Context c=new Context();c.alarms.allowed=exact;long now=System.currentTimeMillis();ReminderScheduler.replaceSchedule(c,List.of(new ReminderScheduler.Entry(7,"Private course","08:00","Private room",10,new long[]{now+600_000L,now+1_200_000L})));return c;}
 static Intent dueTest(Context c){Intent i=c.alarms.pending.get(1).intent;long now=System.currentTimeMillis()-1L,elapsed=SystemClock.elapsedRealtime()-1L;diagnostics(c).edit().putLong("pending_test_at",now).putLong("pending_test_elapsed",elapsed).commit();i.putExtra("diagnostic_test_at",now).putExtra("diagnostic_test_elapsed",elapsed);return i;}
 static void monotonicTests(){
  long initialElapsed=SystemClock.elapsedMillis;
  Context c=new Context();ReminderDiagnostics.Outcome outcome=ReminderDiagnostics.scheduleTest(c,60_000L);
  check(c.alarms.types.get(1)==AlarmManager.ELAPSED_REALTIME_WAKEUP,"one-minute diagnostic uses elapsed time instead of mutable wall clock");
  long deadline=diagnostics(c).getLong("pending_test_elapsed",0);
  check(deadline==initialElapsed+60_000L&&c.alarms.times.get(1)==deadline,"alarm deadline is sixty elapsed seconds away");
  check(diagnostics(c).getLong("pending_test_at",0)==outcome.triggerAt,"original wall time remains stable broadcast metadata");
  Intent pending=c.alarms.pending.get(1).intent;
  // A future original wall time represents rolling the wall clock back after scheduling.
  long futureWall=System.currentTimeMillis()+3L*24*60*60_000L;
  diagnostics(c).edit().putLong("pending_test_at",futureWall).commit();pending.putExtra("diagnostic_test_at",futureWall);
  received(c,pending);expect(c,"test_receive",Code.TEST_INVALID);
  long displayBefore=System.currentTimeMillis()+60_000L;
  long displayed=ReminderDiagnostics.snapshot(c).pendingTestAt;
  check(displayed>=displayBefore&&displayed<=System.currentTimeMillis()+60_000L,"report projects remaining elapsed time onto current wall clock");
  check(diagnostics(c).getLong("pending_test_at",0)==futureWall,"report cannot mutate pending broadcast identity");
  SystemClock.elapsedMillis=deadline;received(c,pending);expect(c,"test_receive",Code.RECEIVE_ACCEPTED);
  check(c.notifications.count==1,"wall-clock rollback cannot postpone due diagnostic");
  check(diagnostics(c).getLong("pending_test_elapsed",0)==0,"consuming a test clears monotonic metadata");
  received(c,pending);check(c.notifications.count==1,"replayed monotonic test cannot post twice");
  SystemClock.elapsedMillis=initialElapsed;c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);
  pending=c.alarms.pending.get(1).intent;deadline=diagnostics(c).getLong("pending_test_elapsed",0);
  // A past original wall time represents advancing the wall clock after scheduling.
  long pastWall=System.currentTimeMillis()-3L*24*60*60_000L;
  diagnostics(c).edit().putLong("pending_test_at",pastWall).commit();pending.putExtra("diagnostic_test_at",pastWall);
  received(c,pending);expect(c,"test_receive",Code.TEST_INVALID);
  check(c.notifications.count==0&&diagnostics(c).getLong("pending_test_elapsed",0)==deadline,"wall-clock advance neither posts nor expires a future elapsed test");
  SystemClock.elapsedMillis=deadline;received(c,pending);expect(c,"test_receive",Code.RECEIVE_ACCEPTED);
  check(c.notifications.count==1,"wall-clock advance does not discard a due diagnostic");
  SystemClock.elapsedMillis=initialElapsed;c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);
  pending=c.alarms.pending.get(1).intent;deadline=diagnostics(c).getLong("pending_test_elapsed",0);
  SystemClock.elapsedMillis=deadline;
  pending.putExtra("diagnostic_test_elapsed",deadline+1);received(c,pending);expect(c,"test_receive",Code.TEST_INVALID);
  pending.putExtra("diagnostic_test_elapsed",deadline);received(c,pending);expect(c,"test_receive",Code.RECEIVE_ACCEPTED);
  SystemClock.elapsedMillis=initialElapsed;c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);
  pending=c.alarms.pending.get(1).intent;deadline=diagnostics(c).getLong("pending_test_elapsed",0);
  SystemClock.elapsedMillis=deadline+24L*60*60_000L+1;
  received(c,pending);expect(c,"test_receive",Code.TEST_EXPIRED);
  check(c.notifications.count==0&&diagnostics(c).getLong("pending_test_elapsed",0)==0,"elapsed expiry clears a test even when original wall time is still in the future");
  SystemClock.elapsedMillis=initialElapsed;c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);
  pending=dueTest(c);diagnostics(c).edit().remove("pending_test_elapsed").commit();pending.removeExtra("diagnostic_test_elapsed");
  received(c,pending);expect(c,"test_receive",Code.RECEIVE_ACCEPTED);check(c.notifications.count==1,"legacy wall-clock pending test remains deliverable");
  SystemClock.elapsedMillis=0;c=new Context();check(ReminderDiagnostics.scheduleTest(c,60_000L).accepted(),"zero uptime is a valid scheduling origin");
  SystemClock.elapsedMillis=Long.MAX_VALUE-1;c=new Context();check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.INVALID_DELAY&&c.alarms.pending.isEmpty(),"overflowing elapsed deadline cannot schedule");
  SystemClock.elapsedMillis=initialElapsed;
 }
 public static void main(String[] args){
  Build.VERSION.SDK_INT=36;BuildConfig.ENABLE_TEST_COURSE=false;
  monotonicTests();
  Context c=production(true);expect(c,"schedule",Code.SCHEDULE_EXACT);
  received(c,c.alarms.pending.get(7).intent);expect(c,"receive",Code.RECEIVE_ACCEPTED);expect(c,"post",Code.POST_SUBMITTED);
  c=production(false);expect(c,"schedule",Code.SCHEDULE_INEXACT);check(!ReminderDiagnostics.snapshot(c).exactAlarmAllowed,"reports inexact capability");
  c=new Context();c.alarms.failExactOnly=true;ReminderScheduler.replaceSchedule(c,List.of(new ReminderScheduler.Entry(8,"","","",0,new long[]{System.currentTimeMillis()+60_000L})));expect(c,"schedule",Code.SCHEDULE_INEXACT);
  c=production(true);c.alarms.fail=true;received(c,c.alarms.pending.get(7).intent);expect(c,"schedule",Code.SCHEDULE_SECURITY_ERROR);check(c.notifications.count==1,"valid current reminder posts despite next submission failure");
  c=production(true);c.notificationPermission=-1;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.PERMISSION_DENIED);check(c.alarms.exactCalls==2,"permission denial retains chain");
  c=production(true);c.notifications.enabled=false;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.NOTIFICATIONS_BLOCKED);
  c=production(true);ClassReminderReceiver.ensureChannel(c,c.notifications);c.notifications.channel.importance=0;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.CHANNEL_BLOCKED);check(c.notifications.channel.importance==0,"does not reset blocked channel");
  c=production(true);ClassReminderReceiver.ensureChannel(c,c.notifications);c.notifications.channel.group="group";c.notifications.group=new NotificationChannelGroup();c.notifications.group.blocked=true;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.CHANNEL_BLOCKED);
  c=production(true);ClassReminderReceiver.ensureChannel(c,c.notifications);c.notifications.channel.sound=null;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.POST_SUBMITTED_QUIET);
  c=production(true);c.notifications.failPost=true;received(c,c.alarms.pending.get(7).intent);expect(c,"post",Code.POST_SECURITY_ERROR);
  c=production(true);Intent stale=c.alarms.pending.get(7).intent;ReminderScheduler.replaceSchedule(c,List.of(new ReminderScheduler.Entry(7,"","","",0,new long[]{System.currentTimeMillis()+90_000L})));received(c,stale);expect(c,"receive",Code.RECEIVE_STALE);
  c=production(true);c.prefs.edit().putString("class_reminder_schedule_encrypted_v1","corrupt").commit();check(!ReminderDiagnostics.snapshot(c).scheduleReadable,"corrupt store visible");received(c,new Intent().putExtra(ReminderScheduler.EXTRA_REQUEST_CODE,7).putExtra(ReminderScheduler.EXTRA_GENERATION,2L));expect(c,"receive",Code.STORE_FAILED);
  c=production(true);String stored=c.prefs.getString("class_reminder_schedule_encrypted_v1","");int productionCalls=c.alarms.exactCalls;
  check(ReminderDiagnostics.sendImmediateTest(c).accepted(),"immediate test available in release");expect(c,"test_post",Code.POST_SUBMITTED);check(c.alarms.exactCalls==productionCalls,"immediate test does not schedule");
  long before=System.currentTimeMillis();ReminderDiagnostics.Outcome outcome=ReminderDiagnostics.scheduleTest(c,60_000L);check(outcome.code==Code.SCHEDULE_EXACT&&outcome.triggerAt>=before+60_000L,"60s real AlarmManager request");check(c.alarms.times.get(1)==diagnostics(c).getLong("pending_test_elapsed",0),"elapsed test time submitted to alarm service");
  Intent early=c.alarms.pending.get(1).intent;received(c,early);expect(c,"test_receive",Code.TEST_INVALID);check(diagnostics(c).getLong("pending_test_at",0)>0,"early invalid delivery cannot consume test");
  Intent due=dueTest(c);int count=c.notifications.count;received(c,due);expect(c,"test_receive",Code.RECEIVE_ACCEPTED);expect(c,"test_post",Code.POST_SUBMITTED);check(c.notifications.count==count+1,"background test submits notification through receiver");check(diagnostics(c).getLong("pending_test_at",0)==0,"valid test consumed");received(c,due);check(c.notifications.count==count+1,"duplicate test cannot post");
  check(stored.equals(c.prefs.getString("class_reminder_schedule_encrypted_v1","")),"all tests preserve encrypted production payload");check(c.alarms.pending.containsKey(7),"tests do not cancel production alarm");
  c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);Intent expired=c.alarms.pending.get(1).intent;long expiredAt=System.currentTimeMillis()-25L*60*60_000L;diagnostics(c).edit().remove("pending_test_elapsed").putLong("pending_test_at",expiredAt).commit();expired.removeExtra("diagnostic_test_elapsed");expired.putExtra("diagnostic_test_at",expiredAt);received(c,expired);expect(c,"test_receive",Code.TEST_EXPIRED);check(diagnostics(c).getLong("pending_test_at",0)==0,"expired legacy test no longer reported pending");check(c.notifications.count==0,"expired legacy test cannot post");
   c=new Context();c.alarms.allowed=false;check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.SCHEDULE_INEXACT,"test supports inexact fallback");
  c=new Context();c.alarms.failExactOnly=true;check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.SCHEDULE_INEXACT,"test handles exact permission race");
  c=new Context();c.notificationPermission=-1;check(!ReminderDiagnostics.scheduleTest(c,60_000L).accepted()&&c.alarms.pending.isEmpty(),"denied permission does not claim test scheduled");expect(c,"test_schedule",Code.PERMISSION_DENIED);
  c=new Context();ReminderDiagnostics.scheduleTest(c,60_000L);Intent replaced=dueTest(c);ReminderDiagnostics.scheduleTest(c,60_000L);received(c,replaced);expect(c,"test_receive",Code.TEST_INVALID);check(c.notifications.count==0,"replaced token rejected");
  check(ReminderDiagnostics.cancelTest(c).code==Code.TEST_CANCELED,"cancel pending test");received(c,replaced);check(c.notifications.count==0,"canceled test cannot post");check(diagnostics(c).getLong("pending_test_elapsed",0)==0,"cancel removes elapsed deadline");
  c=production(true);ReminderDiagnostics.scheduleTest(c,60_000L);new ReminderBootReceiver().onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));check(diagnostics(c).getLong("pending_test_at",0)==0,"boot invalidates old test");expect(c,"test_schedule",Code.TEST_CANCELED);expect(c,"schedule",Code.SCHEDULE_EXACT);
  c=production(false);ReminderDiagnostics.scheduleTest(c,60_000L);new ReminderBootReceiver().onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));expect(c,"schedule",Code.SCHEDULE_INEXACT);check(diagnostics(c).getLong("pending_test_at",0)==0,"package replacement invalidates old test");
  c=production(false);ReminderDiagnostics.Outcome waiting=ReminderDiagnostics.scheduleTest(c,60_000L);Intent waitingIntent=c.alarms.pending.get(1).intent;
  c.alarms.allowed=true;new ReminderBootReceiver().onReceive(c,new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
  check(diagnostics(c).getLong("pending_test_at",0)==waiting.triggerAt,"granting exact permission keeps pending diagnostic test");
  check(c.alarms.pending.get(1)!=null&&c.alarms.pending.get(1).intent==waitingIntent,"permission grant does not cancel diagnostic alarm");expect(c,"schedule",Code.SCHEDULE_EXACT);
  received(c,dueTest(c));expect(c,"test_receive",Code.RECEIVE_ACCEPTED);check(c.notifications.count==1,"waiting diagnostic still notifies after permission grant");
  c=production(false);waiting=ReminderDiagnostics.scheduleTest(c,60_000L);new ReminderBootReceiver().onReceive(c,new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
  check(diagnostics(c).getLong("pending_test_at",0)==waiting.triggerAt,"permission race leaves pending test unchanged");expect(c,"schedule",Code.SCHEDULE_INEXACT);
  c=new Context();c.alarms.fail=true;check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.SCHEDULE_SECURITY_ERROR,"test scheduling failure recorded");check(diagnostics(c).getLong("pending_test_at",0)==0,"failed submission clears pending test");
  c=new Context();diagnostics(c).commitAllowed=false;check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.STORE_FAILED,"test refuses unsaved token");check(c.alarms.pending.isEmpty(),"failed persistence never schedules");
  c=new Context();diagnostics(c).failAtCommit=2;check(ReminderDiagnostics.scheduleTest(c,60_000L).code==Code.STORE_FAILED,"token-write failure is reported");
  check(c.alarms.pending.isEmpty(),"token-write failure submits no alarm");
  check(diagnostics(c).getLong("pending_test_at",0)==0&&diagnostics(c).getLong("pending_test_elapsed",0)==0&&diagnostics(c).getString("pending_test_token","").isEmpty(),"failed token write cannot leave a phantom pending diagnostic");
  check(ReminderDiagnostics.snapshot(c).pendingTestAt==0,"diagnostic report does not promise a nonexistent alarm");
  c=production(true);check(!ReminderDiagnostics.report(c).contains("Private"),"report omits course names and locations");
  System.out.println("ReminderDiagnosticRegressionTest: "+checks+" checks passed (actual diagnostics + scheduler + receivers, service doubles; NOT device/API36 certification).");
 }
}
'@
