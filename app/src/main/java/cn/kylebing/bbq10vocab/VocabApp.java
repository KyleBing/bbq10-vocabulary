package cn.kylebing.bbq10vocab;

import android.app.Application;
import android.util.Log;

import cn.kylebing.bbq10vocab.db.DbHelper;
import cn.kylebing.bbq10vocab.db.WordDao;

public class VocabApp extends Application {
    private static final String TAG = "VocabApp";
    private WordDao dao;
    private WordTts tts;
    private String initError;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            dao = new WordDao(DbHelper.get(this));
        } catch (Throwable t) {
            Log.e(TAG, "db init failed", t);
            initError = t.getMessage() == null ? t.toString() : t.getMessage();
        }
        tts = new WordTts(this);
    }

    public WordDao dao() {
        if (dao == null) {
            dao = new WordDao(DbHelper.get(this));
            initError = null;
        }
        return dao;
    }

    public WordTts tts() {
        if (tts == null) {
            tts = new WordTts(this);
        }
        return tts;
    }

    public String initError() {
        return initError;
    }

    public synchronized void reloadDao() {
        dao = new WordDao(DbHelper.reopen(this));
        initError = null;
    }

    @Override
    public void onTerminate() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        super.onTerminate();
    }
}
