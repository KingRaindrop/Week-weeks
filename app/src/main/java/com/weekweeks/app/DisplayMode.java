package com.weekweeks.app;

/** A presentation preference. Stored timetable data and reminders are independent of it. */
enum DisplayMode {
    TIMETABLE("timetable", true, false),
    AGENDA("agenda", false, true),
    COMBINED("combined", true, true);

    static final String PREF_KEY = "display_mode";
    final String storedValue;
    private final boolean courses, events;

    DisplayMode(String storedValue, boolean courses, boolean events) {
        this.storedValue = storedValue;
        this.courses = courses;
        this.events = events;
    }

    boolean showsCourses() { return courses; }
    boolean showsEvents() { return events; }

    static DisplayMode fromStoredValue(String value) {
        for (DisplayMode mode : values()) if (mode.storedValue.equals(value)) return mode;
        return COMBINED;
    }
}
