package com.weekweeks.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared read-only decoding for the activity and background reminder rebuilds. */
final class StoredTimetableSnapshot {
    static final String PREFS_NAME = "week_weeks";
    static final String COURSES_KEY = "courses_encrypted_v2";
    static final String EVENTS_KEY = "personal_events_encrypted_v1";
    static final String LEGACY_COURSES_KEY = "courses";
    static final String PERIOD_TIMES_KEY = "period_times_v1";
    static final int MAX_PERIODS = 20;
    static final int MAX_COURSES = 200;
    static final int MAX_FIELD_LENGTH = 60;
    static final String[] DEFAULT_PERIOD_TIMES = {
            "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
            "14:30-15:15", "15:25-16:10", "16:20-17:05", "17:15-18:00",
            "19:30-20:15", "20:25-21:10", "21:20-22:05"
    };
    private static final SecureStorage STORAGE = new SecureStorage("week_weeks_courses_key");

    static final class Courses {
        final List<Course> items;
        final boolean readable, encryptedSource, legacyNeedsMigration;
        final int invalidWeekCount;
        final String error; // null, "decrypt", or "parse"
        Courses(List<Course> items, boolean readable, boolean encryptedSource,
                boolean legacyNeedsMigration, int invalidWeekCount, String error) {
            this.items = items; this.readable = readable; this.encryptedSource = encryptedSource;
            this.legacyNeedsMigration = legacyNeedsMigration; this.invalidWeekCount = invalidWeekCount;
            this.error = error;
        }
    }

    static final class Events {
        final List<PersonalEvent> items;
        final boolean readable;
        Events(List<PersonalEvent> items, boolean readable) { this.items = items; this.readable = readable; }
    }

    final Courses courses;
    final Events events;
    final List<String> periodTimes;

    private StoredTimetableSnapshot(Courses courses, Events events, List<String> periodTimes) {
        this.courses = courses; this.events = events; this.periodTimes = periodTimes;
    }

    static StoredTimetableSnapshot load(SharedPreferences prefs) {
        List<String> periods = readPeriodTimes(prefs);
        return new StoredTimetableSnapshot(readCourses(prefs, periods.size(), new int[]{Course.DEFAULT_COLOR}),
                readEvents(prefs), periods);
    }

    static Courses readCourses(SharedPreferences prefs, int periodCount, int[] palette) {
        boolean encryptedSource = false;
        String raw;
        try {
            String encrypted = prefs.getString(COURSES_KEY, null);
            encryptedSource = encrypted != null;
            raw = encryptedSource ? STORAGE.decrypt(encrypted) : prefs.getString(LEGACY_COURSES_KEY, "[]");
        } catch (Exception failed) {
            return new Courses(new ArrayList<>(), false, encryptedSource, false, 0, "decrypt");
        }
        List<Course> items = new ArrayList<>();
        int invalidWeeks = 0;
        int[] colors = palette == null || palette.length == 0 ? new int[]{Course.DEFAULT_COLOR} : palette;
        int maxPeriod = Math.min(MAX_PERIODS, Math.max(1, periodCount));
        try {
            JSONArray array = new JSONArray(raw);
            if (array.length() > MAX_COURSES) throw new IllegalArgumentException("Course capacity exceeded");
            Set<Long> ids = new HashSet<>();
            // Reserve explicit IDs first. Legacy rows without IDs need stable identities
            // that cannot collide with a later row or change during a background rebuild.
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                if (object.has("id")) {
                    long id = number(object, "id", 0L);
                    if (id == 0L || !ids.add(id)) throw new IllegalArgumentException("Invalid course identity");
                }
            }
            long fallbackId = 1L;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                Course course = new Course();
                if (object.has("id")) course.id = number(object, "id", 0L);
                else {
                    while (ids.contains(fallbackId)) fallbackId++;
                    course.id = fallbackId; ids.add(fallbackId++);
                }
                course.day = integer(object, "day", 0);
                course.period = integer(object, "period", 1);
                course.endPeriod = integer(object, "endPeriod", course.period);
                if (course.day < 0 || course.day > 6 || course.period < 1 || course.period > maxPeriod
                        || course.endPeriod < course.period || course.endPeriod > maxPeriod)
                    throw new IllegalArgumentException("Invalid course placement");
                course.name = text(object, "name", MAX_FIELD_LENGTH);
                if (course.name.isEmpty()) throw new IllegalArgumentException("Missing course name");
                course.weeks = text(object, "weeks", MAX_FIELD_LENGTH);
                course.invalidWeeks = !TimetableRules.parseWeeks(course.weeks).valid;
                if (course.invalidWeeks) invalidWeeks++;
                course.room = text(object, "room", MAX_FIELD_LENGTH);
                course.teacher = text(object, "teacher", MAX_FIELD_LENGTH);
                course.color = safeColor(object.optInt("color", colors[i % colors.length]), colors);
                items.add(course);
            }
            return new Courses(items, true, encryptedSource, !encryptedSource && !"[]".equals(raw), invalidWeeks, null);
        } catch (Exception failed) {
            return new Courses(new ArrayList<>(), false, encryptedSource, false, 0, "parse");
        }
    }

    static Events readEvents(SharedPreferences prefs) {
        List<PersonalEvent> items = new ArrayList<>();
        try {
            String encrypted = prefs.getString(EVENTS_KEY, null);
            if (encrypted == null) return new Events(items, true);
            JSONArray array = new JSONArray(STORAGE.decrypt(encrypted));
            if (array.length() > PersonalEvent.MAX_EVENTS) throw new IllegalArgumentException("Event capacity exceeded");
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                PersonalEvent event = new PersonalEvent();
                event.id = number(o, "id", 0L);
                if (event.id == 0L || !ids.add(event.id)) throw new IllegalArgumentException("Invalid event identity");
                event.year = integer(o, "year", 0); event.month = integer(o, "month", 0); event.day = integer(o, "day", 0);
                event.startMinutes = integer(o, "start", -1);
                event.endMinutes = integer(o, "end", PersonalEvent.NO_END);
                event.remindMinutes = integer(o, "remind", PersonalEvent.NO_REMINDER);
                event.title = text(o, "title", PersonalEvent.MAX_TITLE);
                event.location = text(o, "location", PersonalEvent.MAX_LOCATION);
                event.note = text(o, "note", PersonalEvent.MAX_NOTE);
                if (o.has("showInTimetable") && !(o.opt("showInTimetable") instanceof Boolean))
                    throw new IllegalArgumentException("Invalid event visibility");
                event.showInTimetable = o.optBoolean("showInTimetable", true);
                if (event.validate() != null) throw new IllegalArgumentException("Invalid event values");
                items.add(event);
            }
            return new Events(items, true);
        } catch (Exception failed) {
            return new Events(new ArrayList<>(), false);
        }
    }

    static List<String> readPeriodTimes(SharedPreferences prefs) {
        try {
            String encoded = prefs.getString(PERIOD_TIMES_KEY, null);
            if (encoded != null) {
                JSONArray array = new JSONArray(encoded);
                if (array.length() > MAX_PERIODS) throw new IllegalArgumentException("Period capacity exceeded");
                List<String> periods = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    Object value = array.get(i);
                    if (!(value instanceof String)) throw new IllegalArgumentException("Invalid period time");
                    periods.add(((String) value).trim());
                }
                if (validatePeriods(periods)) return periods;
            }
        } catch (Exception invalid) { /* Use the same defaults as the activity. */ }
        return new ArrayList<>(Arrays.asList(DEFAULT_PERIOD_TIMES));
    }

    static boolean validatePeriods(List<String> periods) {
        if (periods == null || periods.isEmpty() || periods.size() > MAX_PERIODS) return false;
        int previousEnd = -1;
        for (String value : periods) {
            if (value == null) return false;
            String[] parts = value.split("-");
            if (parts.length != 2) return false;
            int start = clockMinutes(parts[0]), end = clockMinutes(parts[1]);
            if (start < 0 || end <= start || previousEnd > start) return false;
            previousEnd = end;
        }
        return true;
    }

    static int clockMinutes(String value) {
        if (value == null) return -1;
        String[] parts = value.split(":");
        if (parts.length != 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
            return hour < 0 || hour > 23 || minute < 0 || minute > 59 ? -1 : hour * 60 + minute;
        } catch (NumberFormatException invalid) { return -1; }
    }

    private static String text(JSONObject object, String key, int maxLength) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        if (!(value instanceof String)) throw new IllegalArgumentException("Invalid text field");
        String trimmed = ((String) value).trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("Text field exceeds limit");
        return trimmed;
    }

    private static long number(JSONObject object, String key, long fallback) throws Exception {
        if (!object.has(key)) return fallback;
        Object value = object.get(key);
        if (!(value instanceof Number) && !(value instanceof String))
            throw new IllegalArgumentException("Invalid integer field");
        // optInt/optLong silently truncate fractions and overflow. Storage must not
        // silently change identities, calendar dates or course/event start times.
        return Long.parseLong(value.toString());
    }

    private static int integer(JSONObject object, String key, int fallback) throws Exception {
        long value = number(object, key, fallback);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Integer field out of range");
        return (int) value;
    }

    private static int safeColor(int color, int[] palette) {
        int closest = palette[0], best = Integer.MAX_VALUE;
        for (int allowed : palette) {
            if (color == allowed) return color;
            int red = ((color >> 16) & 255) - ((allowed >> 16) & 255);
            int green = ((color >> 8) & 255) - ((allowed >> 8) & 255);
            int blue = (color & 255) - (allowed & 255);
            int distance = red * red + green * green + blue * blue;
            if (distance < best) { best = distance; closest = allowed; }
        }
        return closest;
    }
}
