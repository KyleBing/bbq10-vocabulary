package cn.kylebing.bbq10vocab;

import android.app.Application;

import cn.kylebing.bbq10vocab.db.DbHelper;
import cn.kylebing.bbq10vocab.db.WordDao;

public class VocabApp extends Application {
    private WordDao dao;

    @Override
    public void onCreate() {
        super.onCreate();
        dao = new WordDao(DbHelper.get(this));
    }

    public WordDao dao() {
        return dao;
    }

    public synchronized void reloadDao() {
        dao = new WordDao(DbHelper.reopen(this));
    }
}
