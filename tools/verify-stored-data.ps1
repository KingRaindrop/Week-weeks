# Runs the actual loader against controlled preferences, crypto and JSON object-graph doubles.
# This tests completeness/identity validation, not JSON syntax, Keystore or Android persistence.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or newer.' }
$out = Join-Path $root ('build\stored-data-regression-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sources = @{
'android/content/SharedPreferences.java' = @'
package android.content;
import java.util.*;
public final class SharedPreferences {
 private final Map<String,Object> values=new HashMap<>();
 public String getString(String key,String fallback){return values.containsKey(key)?(String)values.get(key):fallback;}
 public void fixture(String key,Object value){values.put(key,value);}
}
'@
'com/weekweeks/app/SecureStorage.java' = @'
package com.weekweeks.app;
/** Identity encryption double; production Keystore is covered by device checks. */
final class SecureStorage { SecureStorage(String alias){} String decrypt(String value){return value;} }
'@
'org/json/JSONObject.java' = @'
package org.json;
import java.util.*;
/** Controlled object graph, intentionally not a JSON parser. */
public final class JSONObject {
 public static final Object NULL=new Object();
 private final Map<String,Object> values=new HashMap<>();
 public JSONObject put(String key,Object value){values.put(key,value);return this;}
 public boolean has(String key){return values.containsKey(key);}
 public boolean isNull(String key){return !values.containsKey(key)||values.get(key)==NULL||values.get(key)==null;}
 public Object opt(String key){return values.get(key);}
 public Object get(String key){if(!values.containsKey(key))throw new IllegalArgumentException();return values.get(key);}
 public long optLong(String key,long fallback){Object v=values.get(key);return v instanceof Number?((Number)v).longValue():fallback;}
 public int optInt(String key,int fallback){return (int)optLong(key,fallback);}
 public String optString(String key,String fallback){Object v=values.get(key);return v==null||v==NULL?fallback:String.valueOf(v);}
 public boolean optBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
}
'@
'org/json/JSONArray.java' = @'
package org.json;
import java.util.*;
public final class JSONArray {
 private static final Map<String,List<Object>> graphs=new HashMap<>();
 private final List<Object> values;
 public JSONArray(){values=new ArrayList<>();}
 public JSONArray(String encoded){if("[]".equals(encoded)){values=new ArrayList<>();return;}if(!graphs.containsKey(encoded))throw new IllegalArgumentException("Unknown fixture graph");values=new ArrayList<>(graphs.get(encoded));}
 public JSONArray put(Object value){values.add(value);return this;}
 public int length(){return values.size();}
 public JSONObject optJSONObject(int i){return values.get(i) instanceof JSONObject?(JSONObject)values.get(i):null;}
 public JSONObject getJSONObject(int i){JSONObject value=optJSONObject(i);if(value==null)throw new IllegalArgumentException("Not an object");return value;}
 public String optString(int i,String fallback){Object value=values.get(i);return value==null||value==JSONObject.NULL?fallback:String.valueOf(value);}
 public Object get(int i){return values.get(i);}
 public String toString(){String key="[fixture-"+UUID.randomUUID()+"]";graphs.put(key,new ArrayList<>(values));return key;}
}
'@
'com/weekweeks/app/StoredDataRegressionTest.java' = @'
package com.weekweeks.app;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;
public final class StoredDataRegressionTest {
 private static int checks;
 private static void check(boolean valid,String message){checks++;if(!valid)throw new AssertionError(message);}
 private static JSONObject course(long id){return new JSONObject().put("id",id).put("name","Audit course "+id).put("day",0).put("period",1).put("endPeriod",2).put("weeks","1-16");}
 private static JSONObject event(long id){return new JSONObject().put("id",id).put("title","Audit event "+id).put("year",2030).put("month",1).put("day",8).put("start",600).put("end",660).put("remind",15);}
 private static StoredTimetableSnapshot.Courses courses(JSONArray rows,boolean encrypted){SharedPreferences p=new SharedPreferences();p.fixture(encrypted?StoredTimetableSnapshot.COURSES_KEY:StoredTimetableSnapshot.LEGACY_COURSES_KEY,rows.toString());return StoredTimetableSnapshot.readCourses(p,11,null);}
 private static StoredTimetableSnapshot.Events events(JSONArray rows){SharedPreferences p=new SharedPreferences();p.fixture(StoredTimetableSnapshot.EVENTS_KEY,rows.toString());return StoredTimetableSnapshot.readEvents(p);}
 private static void rejectsCourse(JSONArray rows,String reason){for(boolean encrypted:new boolean[]{true,false}){StoredTimetableSnapshot.Courses result=courses(rows,encrypted);check(!result.readable&&result.items.isEmpty()&&!result.legacyNeedsMigration,reason+" encrypted="+encrypted);}}
 private static void rejectsEvent(JSONArray rows,String reason){StoredTimetableSnapshot.Events result=events(rows);check(!result.readable&&result.items.isEmpty(),reason);}
 public static void main(String[] args){
  rejectsEvent(new JSONArray().put(event(1)).put(new JSONObject()),"invalid event must lock entire store rather than publish savable subset");
  rejectsEvent(new JSONArray().put(event(1)).put(JSONObject.NULL),"non-object event must not vanish");
  rejectsEvent(new JSONArray().put(event(1)).put(event(2).put("day",32)),"bad event date must not vanish");
  rejectsEvent(new JSONArray().put(event(1)).put(event(1).put("title","Other event")),"duplicate event ID must not expose ambiguous editing/deletion");
  rejectsEvent(new JSONArray().put(event(0)),"zero event ID cannot be edited safely");
  rejectsEvent(new JSONArray().put(event(1).put("start",600.5)),"fractional event clock must not silently truncate");
  rejectsEvent(new JSONArray().put(event(1).put("title",new JSONArray())),"non-text event title must not stringify");
  JSONArray manyEvents=new JSONArray();for(int n=1;n<=PersonalEvent.MAX_EVENTS+1;n++)manyEvents.put(event(n));
  rejectsEvent(manyEvents,"overflow events must not become a writable truncated subset");
  rejectsCourse(new JSONArray().put(course(1)).put(course(2).put("name","")),"unnamed course must not vanish");
  rejectsCourse(new JSONArray().put(course(1)).put(course(1).put("name","Other course")),"duplicate course ID must not expose ambiguous editing/deletion");
  rejectsCourse(new JSONArray().put(course(0)),"zero course ID is reserved for unsaved draft");
  rejectsCourse(new JSONArray().put(course(1).put("day",7)),"invalid weekday must not silently move to Sunday");
  rejectsCourse(new JSONArray().put(course(1).put("period",12).put("endPeriod",12)),"out-of-range period must not silently move to last period");
  rejectsCourse(new JSONArray().put(course(1).put("period",1.5)),"fractional course period must not truncate");
  rejectsCourse(new JSONArray().put(course(1).put("weeks","1周,".repeat(20)+"30周")),"oversized stored weeks must not lose later occurrences");
  JSONArray manyCourses=new JSONArray();for(int n=1;n<=StoredTimetableSnapshot.MAX_COURSES+1;n++)manyCourses.put(course(n));
  rejectsCourse(manyCourses,"overflow courses must not become a migrated truncated subset");
  SharedPreferences wrongType=new SharedPreferences();wrongType.fixture(StoredTimetableSnapshot.COURSES_KEY,1);wrongType.fixture(StoredTimetableSnapshot.EVENTS_KEY,1);
  check(!StoredTimetableSnapshot.readCourses(wrongType,11,null).readable,"wrong preference type locks courses");
  check(!StoredTimetableSnapshot.readEvents(wrongType).readable,"wrong preference type locks events");
  SharedPreferences emptyCipher=new SharedPreferences();emptyCipher.fixture(StoredTimetableSnapshot.EVENTS_KEY,"");
  check(!StoredTimetableSnapshot.readEvents(emptyCipher).readable,"present empty ciphertext is not a valid empty event source");
  StoredTimetableSnapshot.Courses good=courses(new JSONArray().put(course(1)).put(course(2).put("weeks","bad legacy weeks")),true);
  check(good.readable&&good.items.size()==2&&good.invalidWeekCount==1&&good.items.get(1).invalidWeeks,"repairable invalid week expression remains present and editable");
  check(events(new JSONArray().put(event(1)).put(event(2).put("showInTimetable",false))).items.size()==2,"valid and hidden events remain readable");
  JSONObject noId=new JSONObject().put("name","Legacy no id").put("day",1).put("period",1);
  StoredTimetableSnapshot.Courses legacy=courses(new JSONArray().put(noId).put(course(1)),false);
  check(legacy.readable&&legacy.legacyNeedsMigration&&legacy.items.size()==2&&legacy.items.get(0).id!=0&&legacy.items.get(0).id!=1,"legacy missing IDs migrate without colliding with explicit IDs");
  SharedPreferences blankPeriod=new SharedPreferences();blankPeriod.fixture(StoredTimetableSnapshot.PERIOD_TIMES_KEY,new JSONArray().put("08:00-08:45").put("").put("10:00-10:45").toString());
  check(StoredTimetableSnapshot.readPeriodTimes(blankPeriod).equals(Arrays.asList(StoredTimetableSnapshot.DEFAULT_PERIOD_TIMES)),"blank period must not renumber later period times");
  JSONArray manyPeriods=new JSONArray();for(int n=0;n<21;n++)manyPeriods.put(String.format(java.util.Locale.ROOT,"%02d:00-%02d:30",n,n));
  SharedPreferences overflowPeriod=new SharedPreferences();overflowPeriod.fixture(StoredTimetableSnapshot.PERIOD_TIMES_KEY,manyPeriods.toString());
  check(StoredTimetableSnapshot.readPeriodTimes(overflowPeriod).equals(Arrays.asList(StoredTimetableSnapshot.DEFAULT_PERIOD_TIMES)),"overflow period list must not silently truncate");
  check(StoredTimetableSnapshot.readCourses(new SharedPreferences(),11,null).readable&&StoredTimetableSnapshot.readEvents(new SharedPreferences()).readable,"absent stores are genuinely empty");
  System.out.println("StoredDataRegressionTest: "+checks+" checks passed (actual loader; controlled preferences/crypto/JSON graph doubles).");
 }
}
'@
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$generated = @()
foreach ($entry in $sources.GetEnumerator()) {
    $path = Join-Path $out $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [System.IO.File]::WriteAllText($path, $entry.Value, $utf8)
    $generated += $path
}
$names = @('StoredTimetableSnapshot','Course','CourseColorRules','CoursePalette','ImportPlanner','PdfCourseParser','PdfDocumentStreams','ImportException','PersonalEvent','TimetableRules','UiLang')
$production = $names | ForEach-Object { Join-Path $root ('app\src\main\java\com\weekweeks\app\' + $_ + '.java') }
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 --release 17 -d $out @generated @production
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp $out com.weekweeks.app.StoredDataRegressionTest
exit $LASTEXITCODE
