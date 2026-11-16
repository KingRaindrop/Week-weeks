package com.weekweeks.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Behavioral lifecycle tests, independent of Android dialogs or source-string assertions. */
public class ImportFlowStateTest {
    private PdfCourseParser.ParsedCourse course() {
        PdfCourseParser.ParsedCourse c = new PdfCourseParser.ParsedCourse();
        c.day = 1; c.period = 2; c.endPeriod = 3;
        c.name = "Math"; c.weeks = "1-16"; c.room = "A101";
        return c;
    }
    private ImportPlanner.Result result(int imported, int updated) {
        return new ImportPlanner.Result(imported, updated, new ArrayList<>());
    }
    @Test public void acceptedPayloadIsDetachedFromParserAndCallers() {
        PdfCourseParser.ParsedCourse parsed = course();
        List<PdfCourseParser.ParsedCourse> input = new ArrayList<>(Arrays.asList(parsed));
        ImportFlowState flow = new ImportFlowState(input, "Text", null);
        parsed.name = "mutated"; input.clear();
        assertEquals("Math", flow.items().get(0).name);
        List<PdfCourseParser.ParsedCourse> snapshot = flow.items();
        snapshot.get(0).room = "other"; snapshot.clear();
        assertEquals("A101", flow.items().get(0).room);
        assertTrue(flow.canSubmit());
    }
    @Test public void periodConfigurationPreservesPayloadButBlocksSubmission() {
        ImportFlowState flow = new ImportFlowState(Arrays.asList(course()), "PDF", "content://test/1");
        flow.configurePeriods();
        assertEquals(ImportFlowState.Stage.PERIODS, flow.stage());
        assertFalse(flow.canSubmit());
        assertEquals(1, flow.items().size());
        try { flow.committed(result(1, 0)); fail("Cannot commit while configuring periods"); }
        catch (IllegalStateException expected) { }
        flow.periodsSaved();
        assertTrue(flow.canSubmit());
        assertEquals("Math", flow.items().get(0).name);
    }
    @Test public void successfulCommitErasesPayloadAndCannotBeReplayed() {
        ImportFlowState flow = new ImportFlowState(Arrays.asList(course()), "PDF", "content://test/1");
        flow.committed(result(1, 0));
        assertEquals(ImportFlowState.Stage.RESULT, flow.stage());
        assertFalse(flow.canSubmit());
        assertTrue(flow.items().isEmpty());
        try { flow.committed(result(1, 0)); fail("Duplicate commit must fail"); }
        catch (IllegalStateException expected) { }
        flow.periodsSaved(); flow.configurePeriods();
        assertEquals(ImportFlowState.Stage.RESULT, flow.stage());
    }
    @Test public void firstImportContinuesToCalendarButConfiguredOrUnchangedImportEnds() {
        ImportFlowState changed = new ImportFlowState(Arrays.asList(course()), "Text", null);
        changed.committed(result(0, 1)); changed.resultClosed(false);
        assertEquals(ImportFlowState.Stage.CALENDAR, changed.stage());
        assertFalse(changed.canSubmit());
        changed.finish(); assertEquals(ImportFlowState.Stage.DONE, changed.stage());
        ImportFlowState configured = new ImportFlowState(Arrays.asList(course()), "Text", null);
        configured.committed(result(1, 0)); configured.resultClosed(true);
        assertEquals(ImportFlowState.Stage.DONE, configured.stage());
        ImportFlowState unchanged = new ImportFlowState(Arrays.asList(course()), "Text", null);
        unchanged.committed(result(0, 0)); unchanged.resultClosed(false);
        assertEquals(ImportFlowState.Stage.DONE, unchanged.stage());
    }
    @Test public void restoredCommittedStagesDiscardAnyStalePayload() {
        for (ImportFlowState.Stage stage : Arrays.asList(ImportFlowState.Stage.RESULT,
                ImportFlowState.Stage.CALENDAR, ImportFlowState.Stage.DONE)) {
            ImportFlowState flow = ImportFlowState.restore(stage, Arrays.asList(course()),
                    "PDF", "content://test/1", result(1, 0));
            assertFalse(flow.canSubmit()); assertTrue(flow.items().isEmpty());
            assertEquals(stage, flow.stage());
        }
    }
    @Test public void restoredReviewAndPeriodsKeepTextImportsWithoutUri() {
        for (ImportFlowState.Stage stage : Arrays.asList(ImportFlowState.Stage.REVIEW, ImportFlowState.Stage.PERIODS)) {
            ImportFlowState flow = ImportFlowState.restore(stage, Arrays.asList(course()), "Text", null, null);
            assertNull(flow.sourceUri); assertEquals("Text", flow.sourceLabel);
            assertEquals("Math", flow.items().get(0).name);
            assertEquals(stage == ImportFlowState.Stage.REVIEW, flow.canSubmit());
        }
    }
    @Test public void cancellationAndMalformedResultNeverAllowSubmission() {
        ImportFlowState cancelled = new ImportFlowState(Arrays.asList(course()), "Text", null);
        cancelled.finish(); assertFalse(cancelled.canSubmit()); assertTrue(cancelled.items().isEmpty());
        ImportFlowState malformed = ImportFlowState.restore(ImportFlowState.Stage.RESULT,
                Collections.emptyList(), "Text", null, null);
        assertEquals(ImportFlowState.Stage.DONE, malformed.stage());
        assertFalse(malformed.canSubmit());
    }
}
