package com.weekweeks.app;

import java.util.Locale;

/**
 * Display language for pure-Java code that has no Android Context (rules, parsers, timing
 * labels). Android code should use string resources instead. Kept in sync with the
 * resource locale by {@link AppLanguage}; defaults to Simplified Chinese so unit tests and
 * the default resource set agree.
 */
final class UiLang {
    static final int HANS = 0;
    static final int HANT = 1;
    static final int EN = 2;

    private static volatile int current = HANS;

    private UiLang() { }

    static int current() {
        return current;
    }

    static void set(int lang) {
        current = lang == HANT || lang == EN ? lang : HANS;
    }

    /** Mirrors Android resource resolution: values-en, values-zh-rTW/HK, else default (zh-Hans). */
    static int fromLocale(Locale locale) {
        if (locale == null) return HANS;
        String language = locale.getLanguage();
        if ("en".equals(language)) return EN;
        if ("zh".equals(language)) {
            String script = locale.getScript();
            String country = locale.getCountry();
            if ("Hant".equals(script) || "TW".equals(country) || "HK".equals(country) || "MO".equals(country)) {
                return HANT;
            }
        }
        return HANS;
    }

    /** Picks the text for the current language. */
    static String t(String hans, String hant, String en) {
        int lang = current;
        return lang == EN ? en : lang == HANT ? hant : hans;
    }

    static boolean isEnglish() {
        return current == EN;
    }
}
