package com.weekweeks.app;

import android.content.Context;
import android.widget.LinearLayout;

/** A content-sized settings card bounded by the current, inset-safe window. */
final class AdaptiveSettingsPanel extends LinearLayout {
    AdaptiveSettingsPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
        int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
        int maxWidth = Math.round(430 * getResources().getDisplayMetrics().density);
        int width = SettingsPanelBounds.width(availableWidth, maxWidth);
        int height = SettingsPanelBounds.height(availableHeight);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
    }
}
