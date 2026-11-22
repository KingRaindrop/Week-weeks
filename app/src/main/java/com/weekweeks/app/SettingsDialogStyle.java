package com.weekweeks.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Shared settings presentation. Dialog ownership, validation and callbacks remain with callers. */
final class SettingsDialogStyle {
    private static final int PANEL_WIDTH_DP = 430;
    private static final int DIALOG_INSET_DP = 14;
    private static final int[] ENABLED = {android.R.attr.state_enabled};
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final WeakHashMap<AlertDialog, WeakReference<DialogBinding>> BINDINGS = new WeakHashMap<>();

    final UiSemanticPalette colors;
    private final Context context;
    private final boolean dark;

    static SettingsDialogStyle of(Context context) { return new SettingsDialogStyle(context); }

    private SettingsDialogStyle(Context context) {
        this.context = context;
        dark = MainActivity.isDarkModeFor(context);
        colors = UiSemanticPalette.of(dark, MainActivity.accentColorFor(context));
    }

    MaterialAlertDialogBuilder builder() {
        int inset = dp(DIALOG_INSET_DP);
        // Let Material wrap the background and install its matching outside-touch inset handler.
        return new MaterialAlertDialogBuilder(context)
                .setBackground(round(colors.background, 26))
                .setBackgroundInsetStart(inset).setBackgroundInsetEnd(inset)
                .setBackgroundInsetTop(inset).setBackgroundInsetBottom(inset);
    }

    LinearLayout body() {
        LinearLayout body = column();
        body.setPadding(dp(16), dp(4), dp(16), dp(12));
        return body;
    }

    LinearLayout group() {
        LinearLayout group = column();
        group.setBackground(round(colors.surface, 18));
        group.setPadding(dp(12), dp(12), dp(12), dp(12));
        return group;
    }

    TextView caption(CharSequence value) { return text(value, 13, colors.secondary, false); }

    TextView heading(CharSequence value) {
        TextView heading = text(value, 12, colors.secondary, true);
        heading.setPadding(0, dp(8), 0, dp(7));
        ViewCompat.setAccessibilityHeading(heading, true);
        return heading;
    }

    void field(EditText input) {
        StateListDrawable background = new StateListDrawable();
        background.addState(DISABLED, round(withAlpha(colors.inset, 150), 12));
        GradientDrawable focused = round(colors.inset, 12);
        focused.setStroke(Math.max(1, dp(1)), colors.accent);
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable normal = round(colors.inset, 12);
        normal.setStroke(Math.max(1, dp(1)), colors.divider);
        background.addState(new int[0], normal);
        ViewCompat.setBackgroundTintList(input, null);
        input.setBackground(background);
        input.setTextColor(enabledText(colors.text));
        input.setHintTextColor(colors.secondary);
        input.setTextSize(15);
        input.setTypeface(AppFonts.typeface(context, Typeface.NORMAL));
        input.setMinHeight(dp(48));
        input.setMinimumHeight(dp(48));
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
    }

    void action(Button button, boolean primary) {
        styleAction(button, primary, false);
    }

    private void styleAction(Button button, boolean primary, boolean textOnly) {
        if (button == null) return;
        int fill = textOnly ? Color.TRANSPARENT : primary ? colors.accent : colors.inset;
        int label = primary ? colors.onAccent : colors.accent;
        StateListDrawable content = new StateListDrawable();
        content.addState(DISABLED, round(textOnly ? Color.TRANSPARENT : colors.inset, 12));
        content.addState(new int[0], round(fill, 12));
        ViewCompat.setBackgroundTintList(button, null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(
                primary ? colors.onAccent : colors.accent, dark ? 44 : 28)), content, round(Color.WHITE, 12)));
        button.setTextColor(enabledText(label));
        button.setTextSize(primary ? 14 : 13);
        button.setTypeface(AppFonts.typeface(context, Typeface.BOLD));
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(64));
        button.setMinimumWidth(dp(64));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setElevation(0);
        button.setStateListAnimator(null);
        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params != null && params.height > 0) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            button.setLayoutParams(params);
        }
    }

    ScrollView scrollBody(View body) {
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = availableWindow(this).height();
                View root = getRootView();
                View top = root.findViewById(androidx.appcompat.R.id.topPanel);
                View buttons = root.findViewById(androidx.appcompat.R.id.buttonPanel);
                float scale = Math.max(1f, getResources().getConfiguration().fontScale);
                int titleHeight = chromeHeight(top, dp(32 + 30 * scale));
                int buttonHeight = chromeHeight(buttons, dp(28 + 26 * scale));
                int budget = Math.max(0, available - dp(DIALOG_INSET_DP * 2 + 24)
                        - titleHeight - buttonHeight);
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                    budget = Math.min(budget, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(budget, MeasureSpec.AT_MOST));
            }
        };
        scroll.setTag("settings:dialog_scroll");
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** Apply after show(); never replaces host button, item, show or dismiss listeners. */
    void apply(AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        View decor = window.getDecorView();
        decor.setTag("settings:dialog");
        // The window background and its Material insets must stay paired with the touch handler.
        for (int id : new int[]{androidx.appcompat.R.id.parentPanel, androidx.appcompat.R.id.topPanel,
                androidx.appcompat.R.id.contentPanel, androidx.appcompat.R.id.customPanel,
                androidx.appcompat.R.id.buttonPanel}) {
            View panel = decor.findViewById(id);
            if (panel != null) panel.setBackgroundColor(Color.TRANSPARENT);
        }
        TextView title = decor.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) {
            title.setTag("settings:dialog_title");
            title.setTextColor(colors.text);
            title.setTextSize(22);
            title.setTypeface(AppFonts.typeface(context, Typeface.BOLD));
            title.setSingleLine(false);
            title.setMaxLines(Integer.MAX_VALUE);
            title.setEllipsize(null);
            ViewCompat.setAccessibilityHeading(title, true);
        }
        TextView message = decor.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(colors.text);
            message.setTextSize(15);
            message.setTypeface(AppFonts.typeface(context, Typeface.NORMAL));
        }
        action(dialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
        action(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
        styleAction(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false, true);
        ListView list = dialog.getListView();
        if (list != null) {
            list.setBackground(round(colors.surface, 18));
            list.setClipToOutline(true);
            list.setDivider(new InsetDrawable(new ColorDrawable(colors.divider), dp(12), 0, dp(12), 0));
            list.setDividerHeight(Math.max(1, dp(.5f)));
            list.setSelector(new RippleDrawable(ColorStateList.valueOf(withAlpha(colors.accent, dark ? 44 : 24)),
                    new ColorDrawable(Color.TRANSPARENT), new ColorDrawable(Color.WHITE)));
            styleChoiceAdapter(list);
        }
        int softInput = window.getAttributes().softInputMode;
        window.setSoftInputMode((softInput & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WeakReference<DialogBinding> previous = BINDINGS.get(dialog);
        if (previous != null && previous.get() != null) previous.get().dispose();
        DialogBinding binding = new DialogBinding(this, dialog, decor);
        BINDINGS.put(dialog, new WeakReference<>(binding));
        binding.attach();
    }

    private void fitWidth(Window window, View decor) {
        int available = availableWindow(decor).width();
        // 430dp is the visible card; the extra 28dp belongs to Material's untouched inset wrapper.
        int width = Math.min(available, dp(PANEL_WIDTH_DP + DIALOG_INSET_DP * 2));
        if (width > 0 && window.getAttributes().width != width)
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void styleChoiceRow(View row) {
        if (row instanceof TextView) {
            TextView text = (TextView) row;
            text.setTextColor(enabledText(colors.text));
            text.setTextSize(15);
            text.setTypeface(AppFonts.typeface(context, Typeface.NORMAL));
            text.setSingleLine(false);
            text.setMaxLines(Integer.MAX_VALUE);
            text.setEllipsize(null);
            text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            text.setMinHeight(dp(48));
            text.setMinimumHeight(dp(48));
            text.setPaddingRelative(dp(16), dp(12), dp(16), dp(12));
            if (text instanceof CheckedTextView) {
                ColorStateList indicator = new ColorStateList(
                        new int[][]{DISABLED, {android.R.attr.state_checked}, new int[0]},
                        new int[]{withAlpha(colors.secondary, 100), colors.accent, colors.secondary});
                ((CheckedTextView) text).setCheckMarkTintList(indicator);
                // Material's choice layouts use a start compound drawable instead of a checkmark.
                TextViewCompat.setCompoundDrawableTintList(text, indicator);
            }
            ViewGroup.LayoutParams params = text.getLayoutParams();
            if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                text.setLayoutParams(params);
            }
        } else if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int i = 0; i < group.getChildCount(); i++) styleChoiceRow(group.getChildAt(i));
        }
    }

    private void styleChoiceAdapter(ListView list) {
        ListAdapter adapter = list.getAdapter();
        if (adapter == null) return;
        if (adapter instanceof ChoiceAdapter) adapter = ((ChoiceAdapter) adapter).delegate;
        // setAdapter clears checked positions and selection; retain the ListView's complete state.
        Parcelable state = list.onSaveInstanceState();
        list.setAdapter(new ChoiceAdapter(this, adapter));
        if (state != null) list.onRestoreInstanceState(state);
    }

    /** Style before ListView measures each row, including recycled and initially offscreen rows. */
    private static final class ChoiceAdapter implements WrapperListAdapter {
        private final SettingsDialogStyle style;
        private final ListAdapter delegate;

        ChoiceAdapter(SettingsDialogStyle style, ListAdapter delegate) {
            this.style = style;
            this.delegate = delegate;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View row = delegate.getView(position, convertView, parent);
            // Scrap rows have no parent during ListView's height measurement. Resolve their
            // start/end drawables now so the choice indicator contributes its full height.
            row.setLayoutDirection(parent.getLayoutDirection());
            style.styleChoiceRow(row);
            return row;
        }

        @Override public ListAdapter getWrappedAdapter() { return delegate; }
        @Override public int getCount() { return delegate.getCount(); }
        @Override public Object getItem(int position) { return delegate.getItem(position); }
        @Override public long getItemId(int position) { return delegate.getItemId(position); }
        @Override public boolean hasStableIds() { return delegate.hasStableIds(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public boolean areAllItemsEnabled() { return delegate.areAllItemsEnabled(); }
        @Override public boolean isEnabled(int position) { return delegate.isEnabled(position); }
        @Override public int getItemViewType(int position) { return delegate.getItemViewType(position); }
        @Override public int getViewTypeCount() { return delegate.getViewTypeCount(); }
        @Override public void registerDataSetObserver(DataSetObserver observer) {
            delegate.registerDataSetObserver(observer);
        }
        @Override public void unregisterDataSetObserver(DataSetObserver observer) {
            delegate.unregisterDataSetObserver(observer);
        }
    }

    private Rect availableWindow(View view) {
        Rect available = new Rect();
        if (view != null && view.isAttachedToWindow()) view.getWindowVisibleDisplayFrame(available);
        Activity activity = activity(context);
        if (available.width() > 0 && available.height() > 0) return available;
        if (activity != null) {
            View decor = activity.getWindow().getDecorView();
            decor.getWindowVisibleDisplayFrame(available);
            if (available.width() > 0 && available.height() > 0) return available;
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                available.set(metrics.getBounds());
                android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                available.left += insets.left;
                available.top += insets.top;
                available.right -= insets.right;
                available.bottom -= insets.bottom;
                return available;
            }
        }
        available.set(0, 0, context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
        return available;
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static int chromeHeight(View chrome, int fallback) {
        if (chrome == null) return fallback;
        if (chrome.getVisibility() == View.GONE) return 0;
        int measured = chrome.getMeasuredHeight();
        return measured > 0 ? measured : fallback;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private TextView text(CharSequence value, float sp, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(AppFonts.typeface(context, bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        return text;
    }

    private ColorStateList enabledText(int color) {
        return new ColorStateList(new int[][]{ENABLED, new int[0]},
                new int[]{color, withAlpha(colors.secondary, 130)});
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** An additional tree observer leaves the host dialog's lifecycle listeners intact. */
    private static final class DialogBinding implements ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
        private final SettingsDialogStyle style;
        private final AlertDialog dialog;
        private final View decor;
        private ViewTreeObserver observer;
        private boolean disposed;

        DialogBinding(SettingsDialogStyle style, AlertDialog dialog, View decor) {
            this.style = style; this.dialog = dialog; this.decor = decor;
        }

        void attach() {
            observer = decor.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
            decor.addOnAttachStateChangeListener(this);
            update();
        }

        private void update() {
            if (disposed) return;
            Window window = dialog.getWindow();
            if (window != null) style.fitWidth(window, decor);
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
            decor.removeOnAttachStateChangeListener(this);
            WeakReference<DialogBinding> current = BINDINGS.get(dialog);
            if (current != null && current.get() == this) BINDINGS.remove(dialog);
        }

        @Override public void onGlobalLayout() { update(); }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { dispose(); }
    }
}
