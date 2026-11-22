package com.weekweeks.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

/** A scrollable review of every overlap. Only rows on screen need labels or views. */
@android.annotation.SuppressLint("ViewConstructor") // Created by the editors with their live conflict results; never inflated from XML.
final class ConflictListView extends ListView {
    private final Rect visibleFrame = new Rect();

    static String title(Context context, List<ScheduleConflicts.Hit> hits) {
        if (hits.size() == 1) return context.getString(R.string.conflict_title);
        return context.getResources().getQuantityString(R.plurals.conflict_count, hits.size(), hits.size());
    }

    ConflictListView(Context context, List<ScheduleConflicts.Hit> hits, int textColor, Bundle anchor) {
        super(context);
        setTag("schedule:conflicts");
        setDivider(null);
        setClipToPadding(false);
        setScrollbarFadingEnabled(false);
        setPadding(dp(22), dp(2), dp(22), dp(2));
        setAdapter(new BaseAdapter() {
            @Override public int getCount() { return hits.size(); }
            @Override public ScheduleConflicts.Hit getItem(int position) { return hits.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) recycled;
                if (row == null) {
                    row = new TextView(context);
                    row.setTextSize(14);
                    row.setTextColor(textColor);
                    row.setTypeface(AppFonts.typeface(context, Typeface.NORMAL));
                    row.setPadding(0, dp(8), dp(3), dp(8));
                    row.setLineSpacing(dp(2), 1f);
                }
                ScheduleConflicts.Hit hit = getItem(position);
                String date = ScheduleDay.label(hit.date), time = PersonalEvent.formatTime(hit.start);
                row.setText(hit.end < 0 ? context.getString(R.string.conflict_point, date, time, hit.name)
                        : context.getString(R.string.conflict_row, date, time + "–" + PersonalEvent.formatTime(hit.end), hit.name, hit.minutes));
                return row;
            }
        });
        if (anchor != null) setSelectionFromTop(Math.min(hits.size() - 1, anchor.getInt("index")), anchor.getInt("offset"));
    }

    Bundle anchor() {
        Bundle saved = new Bundle();
        saved.putInt("index", getFirstVisiblePosition());
        View row = getChildAt(0);
        saved.putInt("offset", row == null ? 0 : row.getTop() - getPaddingTop());
        return saved;
    }

    @Override protected void onMeasure(int width, int height) {
        getWindowVisibleDisplayFrame(visibleFrame);
        int screen = getResources().getDisplayMetrics().heightPixels;
        int available = visibleFrame.height() > 0 ? visibleFrame.height() : screen;
        int limit = Math.max(dp(48), Math.min(screen / 2, available - dp(200)));
        if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED) limit = Math.min(limit, MeasureSpec.getSize(height));
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
