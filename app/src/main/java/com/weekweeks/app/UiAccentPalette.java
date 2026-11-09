package com.weekweeks.app;

/** Interface accents only; existing saved indices 0–7 retain their colors. Pure Java. */
final class UiAccentPalette {
    static final int CORAL_LIGHT = 0xFFF45A50;
    static final int CORAL_DARK = 0xFFFF9D98;
    static final int CORAL_INDEX = 7;
    static final String[] NAMES = {
            "蓝色", "青绿", "绿色", "紫色", "橙色", "红色", "靛青", "珊瑚",
            "暗夜", "玫瑰花园", "湖光", "日落霞光", "森林低语", "海风", "薰衣草梦", "杏茶时光"
    };
    /** Light / dark pairs: quieter pigments in light mode, softer luminous accents in dark mode. */
    static final int[][] COLORS = {
            {0xFF1E68D2, 0xFF69B2FF},
            {0xFF0E9F8A, 0xFF4FD1BC},
            {0xFF2E9E44, 0xFF6FD08A},
            {0xFF7A4FE0, 0xFFB59AFF},
            {0xFFE8722E, 0xFFFFA35C},
            {0xFFD64550, 0xFFFF8A94},
            {0xFF4050D8, 0xFF8A97FF},
            {CORAL_LIGHT, CORAL_DARK},
            {0xFF48566F, 0xFFB3C2DF}, // 暗夜：柔和石板蓝
            {0xFFA94F70, 0xFFF0ABC3}, // 玫瑰花园：低饱和玫瑰
            {0xFF337E91, 0xFF8DCDDD}, // 湖光：静谧湖蓝
            {0xFFB05C40, 0xFFF4B596}, // 日落霞光：暖陶橘
            {0xFF42765B, 0xFF98CBAA}, // 森林低语：苔叶绿
            {0xFF277C78, 0xFF8BD1C8}, // 海风：清透青碧
            {0xFF78609E, 0xFFC8B4E7}, // 薰衣草梦：柔雾紫
            {0xFF956D45, 0xFFDBBC94}  // 杏茶时光：温润茶棕
    };

    private static final String[] NAMES_HANT = {
            "藍色", "青綠", "綠色", "紫色", "橙色", "紅色", "靛青", "珊瑚",
            "暗夜", "玫瑰花園", "湖光", "日落霞光", "森林低語", "海風", "薰衣草夢", "杏茶時光"
    };
    private static final String[] NAMES_EN = {
            "Blue", "Teal", "Green", "Purple", "Orange", "Red", "Indigo", "Coral",
            "Midnight", "Rose Garden", "Lake", "Sunset", "Forest", "Sea Breeze", "Lavender", "Almond Tea"
    };

    private UiAccentPalette() { }

    /** Display name in the current {@link UiLang}; {@link #NAMES} stays Simplified. */
    static String name(int index) {
        if (index < 0 || index >= NAMES.length) return "";
        return UiLang.t(NAMES[index], NAMES_HANT[index], NAMES_EN[index]);
    }
}
