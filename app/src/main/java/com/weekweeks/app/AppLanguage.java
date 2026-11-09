package com.weekweeks.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * In-app UI language. "system" leaves the configuration untouched, so Android picks the
 * resource set from the device language (default {@code values} is Simplified Chinese).
 * Other choices override the locale of every Activity and of contexts used by receivers.
 */
final class AppLanguage {
    static final String PREFS_NAME = "week_weeks";
    static final String KEY = "ui_language";
    static final String SYSTEM = "system";
    static final String ZH_HANS = "zh_CN";
    static final String ZH_HANT = "zh_TW";
    static final String EN = "en";
    static final String[] IDS = {SYSTEM, ZH_HANS, ZH_HANT, EN};

    private AppLanguage() { }

    static String current(Context context) {
        String id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, SYSTEM);
        for (String known : IDS) if (known.equals(id)) return id;
        return SYSTEM;
    }

    /** Returns null when the app should follow the device language. */
    static Locale locale(String id) {
        if (ZH_HANS.equals(id)) return new Locale("zh", "CN");
        if (ZH_HANT.equals(id)) return new Locale("zh", "TW");
        if (EN.equals(id)) return Locale.ENGLISH;
        return null;
    }

    /** Applies the saved choice to {@code config}; no-op in system mode. Also syncs {@link UiLang}. */
    static void applyTo(Configuration config, Context base) {
        Locale locale = locale(current(base));
        if (locale == null) {
            UiLang.set(UiLang.fromLocale(primaryLocale(config)));
            return;
        }
        UiLang.set(UiLang.fromLocale(locale));
        if (Build.VERSION.SDK_INT >= 24) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.locale = locale;
        }
        config.setLayoutDirection(locale);
    }

    /** Context for code outside an Activity (receivers, notifications). */
    static Context wrap(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        applyTo(config, base);
        if (locale(current(base)) == null) return base;
        return base.createConfigurationContext(config);
    }

    @SuppressWarnings("deprecation")
    private static Locale primaryLocale(Configuration config) {
        if (Build.VERSION.SDK_INT >= 24) {
            return config.getLocales().isEmpty() ? Locale.getDefault() : config.getLocales().get(0);
        }
        return config.locale != null ? config.locale : Locale.getDefault();
    }

    static boolean save(Context context, String id) {
        boolean known = false;
        for (String candidate : IDS) if (candidate.equals(id)) known = true;
        if (!known) return false;
        return SafePreferences.commit(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                new PreferenceTransaction().put(KEY, id));
    }

    /** Display names; languages are shown in their own script so they stay recognizable. */
    static String[] names(Context context) {
        return new String[]{
                context.getString(R.string.language_system),
                "简体中文",
                "繁體中文",
                "English"
        };
    }

    static String currentName(Context context) {
        String id = current(context);
        String[] names = names(context);
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return names[i];
        return names[0];
    }
}
