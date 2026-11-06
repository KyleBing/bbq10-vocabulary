package cn.kylebing.bbq10vocab;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "vocab_prefs";
    private static final String KEY_DB = "active_db";
    private static final String KEY_LIVE_SPELL = "live_spell";
    private static final String KEY_IME_SUGGEST = "ime_suggest";

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

    public static final class DbNames {
        public static final String DEFAULT = "cet4.db";
        public static final String IMPORTED = "imported.db";
    }
}
