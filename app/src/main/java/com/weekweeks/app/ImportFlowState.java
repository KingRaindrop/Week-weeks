package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI-thread state of one accepted import, independent of windows and Android lifecycle. */
final class ImportFlowState {
    enum Stage { REVIEW, PERIODS, RESULT, CALENDAR, DONE }
    private Stage stage = Stage.REVIEW;
    private List<PdfCourseParser.ParsedCourse> items;
    final String sourceLabel;
    final String sourceUri;
    private ImportPlanner.Result result;

    ImportFlowState(List<PdfCourseParser.ParsedCourse> items, String label, String uri) {
        this.items = copyItems(items);
        sourceLabel = label;
        sourceUri = uri;
    }

    Stage stage() { return stage; }
    List<PdfCourseParser.ParsedCourse> items() { return copyItems(items); }
    ImportPlanner.Result result() { return result; }
    boolean canSubmit() { return stage == Stage.REVIEW; }
    void configurePeriods() { if (stage == Stage.REVIEW) stage = Stage.PERIODS; }
    void periodsSaved() { if (stage == Stage.PERIODS) stage = Stage.REVIEW; }

    /** Call only after persistence succeeded (or a no-change import was reviewed). */
    void committed(ImportPlanner.Result value) {
        if (!canSubmit()) throw new IllegalStateException("Import is no longer awaiting confirmation");
        result = value;
        items = Collections.emptyList(); // Restoration must never replay an already committed payload.
        stage = Stage.RESULT;
    }

    void resultClosed(boolean calendarInitialized) {
        if (stage != Stage.RESULT) return;
        stage = result.changed() && !calendarInitialized ? Stage.CALENDAR : Stage.DONE;
    }
    void finish() { stage = Stage.DONE; items = Collections.emptyList(); }

    static ImportFlowState restore(Stage stage, List<PdfCourseParser.ParsedCourse> items,
                                   String label, String uri, ImportPlanner.Result result) {
        ImportFlowState flow = new ImportFlowState(items, label, uri);
        flow.stage = stage;
        flow.result = result;
        if (stage == Stage.RESULT || stage == Stage.CALENDAR || stage == Stage.DONE) {
            flow.items = Collections.emptyList();
            if (stage == Stage.RESULT && result == null) flow.stage = Stage.DONE;
        }
        return flow;
    }

    private static List<PdfCourseParser.ParsedCourse> copyItems(List<PdfCourseParser.ParsedCourse> source) {
        List<PdfCourseParser.ParsedCourse> copy = new ArrayList<>();
        for (PdfCourseParser.ParsedCourse item : source) {
            PdfCourseParser.ParsedCourse c = new PdfCourseParser.ParsedCourse();
            c.day = item.day; c.period = item.period; c.endPeriod = item.endPeriod;
            c.name = item.name; c.weeks = item.weeks; c.room = item.room; c.teacher = item.teacher;
            copy.add(c);
        }
        return copy;
    }
}
