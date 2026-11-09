package com.weekweeks.app;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

/** Android adapter for {@link PreferenceTransaction}. */
final class SafePreferences {
    private SafePreferences() {
    }

    static boolean commit(SharedPreferences prefs, PreferenceTransaction transaction) {
        return transaction.commit(new PreferenceTransaction.Store() {
            @Override
            public Map<String, ?> getAll() {
                return prefs.getAll();
            }

            @Override
            @SuppressLint("ApplySharedPref") // the commit result decides success and rollback
            @SuppressWarnings("unchecked")
            public boolean write(Map<String, Object> puts, Set<String> removes) {
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, Object> entry : puts.entrySet()) {
                    Object value = entry.getValue();
                    String key = entry.getKey();
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    } else if (value instanceof Set) {
                        editor.putStringSet(key, (Set<String>) value);
                    } else {
                        throw new IllegalArgumentException("Unsupported preference type for " + key);
                    }
                }
                for (String key : removes) {
                    editor.remove(key);
                }
                return editor.commit();
            }
        });
    }
}
