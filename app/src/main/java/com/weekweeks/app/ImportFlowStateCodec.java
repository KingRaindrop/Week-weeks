package com.weekweeks.app;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Versioned, bounded saved-instance-state representation; contains no Activity, callback or window. */
final class ImportFlowStateCodec {
    private ImportFlowStateCodec() { }
    static Bundle save(ImportFlowState flow) {
        if (flow == null || flow.stage() == ImportFlowState.Stage.DONE) return null;
        Bundle state = new Bundle();
        state.putInt("version", 1);
        state.putString("stage", flow.stage().name());
        state.putString("label", flow.sourceLabel);
        state.putString("uri", flow.sourceUri);
        ArrayList<Bundle> rows = new ArrayList<>();
        for (PdfCourseParser.ParsedCourse c : flow.items()) {
            Bundle row = new Bundle();
            row.putInt("day", c.day); row.putInt("start", c.period); row.putInt("end", c.endPeriod);
            row.putString("name", c.name); row.putString("weeks", c.weeks);
            row.putString("room", c.room); row.putString("teacher", c.teacher);
            rows.add(row);
        }
        state.putParcelableArrayList("rows", rows);
        ImportPlanner.Result result = flow.result();
        if (result != null) {
            state.putInt("imported", result.imported);
            state.putInt("updated", result.updated);
            ArrayList<Bundle> skips = new ArrayList<>();
            for (ImportPlanner.Skip skip : result.skipped) {
                Bundle row = new Bundle();
                row.putString("name", skip.name); row.putString("reason", skip.reason.name());
                skips.add(row);
            }
            state.putParcelableArrayList("skips", skips);
        }
        return state;
    }

    static ImportFlowState restore(Bundle state) {
        if (state == null || state.getInt("version") != 1) return null;
        try {
            ImportFlowState.Stage stage = ImportFlowState.Stage.valueOf(state.getString("stage", "DONE"));
            ArrayList<Bundle> rows = state.getParcelableArrayList("rows");
            if (rows != null && rows.size() > 200) return null;
            List<PdfCourseParser.ParsedCourse> items = new ArrayList<>();
            if (rows != null) for (Bundle row : rows) {
                PdfCourseParser.ParsedCourse c = new PdfCourseParser.ParsedCourse();
                c.day = row.getInt("day"); c.period = row.getInt("start"); c.endPeriod = row.getInt("end");
                if (c.day < 0 || c.day > 6 || c.period < 1 || c.endPeriod < c.period) return null;
                c.name = row.getString("name", ""); c.weeks = row.getString("weeks", "");
                c.room = row.getString("room", ""); c.teacher = row.getString("teacher", "");
                items.add(c);
            }
            ImportPlanner.Result result = null;
            if (state.containsKey("imported")) {
                List<ImportPlanner.Skip> skips = new ArrayList<>();
                ArrayList<Bundle> skipRows = state.getParcelableArrayList("skips");
                if (skipRows != null && skipRows.size() > 200) return null;
                if (skipRows != null) for (Bundle row : skipRows) {
                    skips.add(new ImportPlanner.Skip(ImportPlanner.SkipReason.valueOf(row.getString("reason", "EXISTS")),
                            row.getString("name", "")));
                }
                result = new ImportPlanner.Result(state.getInt("imported"), state.getInt("updated"), skips);
            }
            return ImportFlowState.restore(stage, items, state.getString("label", "PDF"), state.getString("uri"), result);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
