package cn.kylebing.bbq10vocab;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "vocab_prefs";
    private static final String KEY_DB = "active_db";
    private static final String KEY_LIVE_SPELL = "live_spell";
    private static final String KEY_IME_SUGGEST = "ime_suggest";
    private static final String KEY_AUTO_SPEAK = "auto_speak";
    private static final String KEY_SPEAK_WHEN = "speak_when";
    private static final String KEY_SPEAK_EXAMPLE = "speak_example";

    /** Auto TTS timing relative to showing the answer/result. */
    public static final int SPEAK_BEFORE_RESULT = 0;
    public static final int SPEAK_AFTER_RESULT = 1;

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String activeDbName(Context c) {
        return p(c).getString(KEY_DB, DbNames.DEFAULT);
    }

    public static void setActiveDbName(Context c, String name) {
        p(c).edit().putString(KEY_DB, name).commit();
    }

    public static boolean liveSpellFeedback(Context c) {
        return p(c).getBoolean(KEY_LIVE_SPELL, true);
    }

    public static void setLiveSpellFeedback(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_LIVE_SPELL, on).commit();
    }

    /** System IME candidates / soft keyboard suggestions while spelling. Default off. */
    public static boolean imeSuggest(Context c) {
        return p(c).getBoolean(KEY_IME_SUGGEST, false);
    }

    public static void setImeSuggest(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_IME_SUGGEST, on).commit();
    }

    /** Auto-speak current word during 识词 / 拼写. Default off. */
    public static boolean autoSpeak(Context c) {
        return p(c).getBoolean(KEY_AUTO_SPEAK, false);
    }

    public static void setAutoSpeak(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_AUTO_SPEAK, on).commit();
    }

    /** {@link #SPEAK_BEFORE_RESULT} or {@link #SPEAK_AFTER_RESULT}. */
    public static int speakWhen(Context c) {
        int v = p(c).getInt(KEY_SPEAK_WHEN, SPEAK_BEFORE_RESULT);
        if (v != SPEAK_BEFORE_RESULT && v != SPEAK_AFTER_RESULT) {
            return SPEAK_BEFORE_RESULT;
        }
        return v;
    }

    public static void setSpeakWhen(Context c, int when) {
        if (when != SPEAK_BEFORE_RESULT && when != SPEAK_AFTER_RESULT) {
            when = SPEAK_BEFORE_RESULT;
        }
        p(c).edit().putInt(KEY_SPEAK_WHEN, when).commit();
    }

    /** After the word, also read the English example sentence. Default off. */
    public static boolean speakExample(Context c) {
        return p(c).getBoolean(KEY_SPEAK_EXAMPLE, false);
    }

    public static void setSpeakExample(Context c, boolean on) {
        p(c).edit().putBoolean(KEY_SPEAK_EXAMPLE, on).commit();
    }

    public static final class DbNames {
        public static final String DEFAULT = "cet4.db";
        public static final String IMPORTED = "imported.db";
    }
}
