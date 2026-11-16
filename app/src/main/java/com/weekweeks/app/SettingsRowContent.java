package com.weekweeks.app;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps the current value readable when a settings row becomes narrow or text grows. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic row with required title/value children.
final class SettingsRowContent extends LinearLayout {
    private final TextView title, value;
    private boolean stacked, initialized;

    SettingsRowContent(Context context, TextView title, TextView value) {
        super(context);
        this.title = title;
        this.value = value;
        setGravity(Gravity.CENTER_VERTICAL);
        addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(value, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float density = getResources().getDisplayMetrics().density;
        boolean next = MeasureSpec.getSize(widthSpec) < 240 * density
                || getResources().getConfiguration().fontScale > 1.15f;
        if (!initialized || next != stacked) {
            initialized = true;
            stacked = next;
            setOrientation(stacked ? VERTICAL : HORIZONTAL);
            title.setLayoutParams(new LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1f));
            LayoutParams valueParams = new LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1f);
            if (stacked) valueParams.topMargin = Math.round(3 * density);
            else valueParams.leftMargin = Math.round(12 * density);
            value.setLayoutParams(valueParams);
            value.setGravity((stacked ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
