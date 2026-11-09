package com.weekweeks.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/**
 * UI font preset. "system" (the default) leaves every view on the platform font, so the
 * app follows the font chosen in system settings. Other presets ship as assets and are
 * applied to a whole window by {@link #install(View)}.
 */
final class AppFonts {
    static final String PREFS_NAME = "week_weeks";
    static final String KEY = "ui_font";
    static final String SIZE_KEY = "ui_font_size_percent";
    static final String SYSTEM = "system";
    static final String OPPO_SANS = "oppo_sans";
    static final String[] IDS = {SYSTEM, OPPO_SANS};
    /** Simplified names; kept for tests. Use {@link #label(Context, int)} for display. */
    static final String[] NAMES = {"跟随系统", "OPPO Sans"};
    private static final String OPPO_SANS_ASSET = "fonts/oppo_sans.ttf";

    private static String loadedId;
    private static Typeface regular, bold;

    private AppFonts() { }

    static String current(Context context) {
        String id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, SYSTEM);
        for (String known : IDS) if (known.equals(id)) return id;
        return SYSTEM;
    }

    static String currentName(Context context) {
        String id = current(context);
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return label(context, i);
        return label(context, 0);
    }

    /** Localized display name of preset {@code index}; {@link #NAMES} stays for compatibility. */
    static String label(Context context, int index) {
        return index == 0 ? context.getString(R.string.view_font_system) : NAMES[index];
    }

    static String[] names(Context context) {
        String[] result = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) result[i] = label(context, i);
        return result;
    }

    static int sizePercent(Context context) {
        return FontSizeRules.normalize(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(SIZE_KEY, FontSizeRules.DEFAULT));
    }

    /** Called once with the unscaled Activity base, not with an already wrapped context. */
    static Context withSize(Context base) {
        int percent = sizePercent(base);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        // Resources can retain an Activity override during AppCompat recreation. Read the
        // actual user setting instead of deriving the baseline from already-scaled resources.
        float systemScale = android.provider.Settings.System.getFloat(base.getContentResolver(),
                android.provider.Settings.System.FONT_SCALE, 1f);
        config.fontScale = FontSizeRules.combinedScale(systemScale, percent);
        AppLanguage.applyTo(config, base);
        return base.createConfigurationContext(config);
    }

    static boolean save(Context context, String id, int percent) {
        boolean known = false;
        for (String candidate : IDS) if (candidate.equals(id)) known = true;
        if (!known) return false;
        return SafePreferences.commit(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                new PreferenceTransaction().put(KEY, id).put(SIZE_KEY, FontSizeRules.normalize(percent)));
    }

    /** Typeface for code that sets fonts explicitly; follows the platform font in system mode. */
    static Typeface typeface(Context context, int style) {
        return typeface(context, current(context), style);
    }

    /** Resolves a draft font without changing the persisted font selection. */
    static Typeface typeface(Context context, String id, int style) {
        if (!OPPO_SANS.equals(id) || !load(context, id)) return Typeface.create(Typeface.DEFAULT, style);
        return (style & Typeface.BOLD) != 0 ? bold : regular;
    }

    private static final Object PREVIEW_FONT = new Object();

    /** Explicit preview is exempt from the saved-font global layout listener. */
    static void previewTypeface(TextView view, String id) {
        view.setTag(PREVIEW_FONT);
        view.setTypeface(typeface(view.getContext(), id, Typeface.NORMAL));
    }

    /** Loads the selected preset; returns false for system mode or when the asset cannot be read. */
    private static boolean load(Context context) {
        return load(context, current(context));
    }

    private static synchronized boolean load(Context context, String id) {
        if (SYSTEM.equals(id)) return false;
        if (id.equals(loadedId)) return regular != null;
        loadedId = id;
        regular = null;
        bold = null;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                // Variable font: pick real weights instead of synthesizing bold.
                regular = new Typeface.Builder(context.getAssets(), OPPO_SANS_ASSET)
                        .setFontVariationSettings("'wght' 400").build();
                bold = new Typeface.Builder(context.getAssets(), OPPO_SANS_ASSET)
                        .setFontVariationSettings("'wght' 700").build();
            } else {
                regular = Typeface.createFromAsset(context.getAssets(), OPPO_SANS_ASSET);
                bold = Typeface.create(regular, Typeface.BOLD);
            }
        } catch (RuntimeException failed) {
            regular = null;
            bold = null;
        }
        if (regular == null || bold == null) {
            regular = null;
            bold = null;
            return false;
        }
        return true;
    }

    /**
     * Applies the preset to every text view in the window, including views added or
     * restyled later. Does nothing in system mode.
     */
    static void install(View root) {
        if (root == null || !load(root.getContext())) return;
        apply(root);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> apply(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /** Dialog variant; call after show() so the window exists. Returns the dialog for chaining. */
    static <T extends android.app.Dialog> T install(T dialog) {
        if (dialog != null && dialog.getWindow() != null) install(dialog.getWindow().getDecorView());
        return dialog;
    }

    private static void apply(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getTag() == PREVIEW_FONT) return;
            Typeface current = text.getTypeface();
            // Keep code-specific monospace text; replace only fonts that are not already ours.
            if (current != regular && current != bold && current != Typeface.MONOSPACE) {
                text.setTypeface(current != null && current.isBold() ? bold : regular);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }
}
