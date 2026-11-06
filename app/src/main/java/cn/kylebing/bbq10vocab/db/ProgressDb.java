package cn.kylebing.bbq10vocab.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/**
 * Global learning progress keyed by word text, independent of which book DB is open.
 * Switching / restoring dictionaries must never wipe this file.
 */
public final class ProgressDb {
    public static final String FILE_NAME = "user_progress.db";
    public static final String ATTACH_ALIAS = "up";

    private ProgressDb() {
    }

    public static File file(Context context) {
        return context.getApplicationContext().getDatabasePath(FILE_NAME);
    }

    public static synchronized void ensureExists(Context context) {
        Context app = context.getApplicationContext();
        File dest = file(app);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dest, null);
        try {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS progress ("
                            + "word TEXT PRIMARY KEY,"
                            + "status INTEGER NOT NULL DEFAULT 0,"
                            + "seen INTEGER NOT NULL DEFAULT 0,"
                            + "known_count INTEGER NOT NULL DEFAULT 0,"
                            + "unknown_count INTEGER NOT NULL DEFAULT 0,"
                            + "updated_at INTEGER NOT NULL DEFAULT 0,"
                            + "box INTEGER NOT NULL DEFAULT 0,"
                            + "due_at INTEGER NOT NULL DEFAULT 0,"
                            + "flag INTEGER NOT NULL DEFAULT 0)");
            addColumnIfMissing(db, "progress", "flag", "INTEGER NOT NULL DEFAULT 0");
        } finally {
            db.close();
        }
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String col, String def) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean found = false;
        try {
            while (c.moveToNext()) {
                if (col.equals(c.getString(1))) {
                    found = true;
                    break;
                }
            }
        } finally {
            c.close();
        }
        if (!found) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + col + " " + def);
        }
    }

    public static void attachTo(SQLiteDatabase bookDb, Context context) {
        ensureExists(context);
        if (isAttached(bookDb)) {
            return;
        }
        String path = file(context).getAbsolutePath().replace("'", "''");
        bookDb.execSQL("ATTACH DATABASE '" + path + "' AS " + ATTACH_ALIAS);
    }

    public static boolean isAttached(SQLiteDatabase bookDb) {
        Cursor c = bookDb.rawQuery("PRAGMA database_list", null);
        try {
            while (c.moveToNext()) {
                if (ATTACH_ALIAS.equals(c.getString(1))) {
                    return true;
                }
            }
        } finally {
            c.close();
        }
        return false;
    }

    /**
     * One-way migrate old per-book progress (by word_id) into global progress (by word).
     * Existing global rows are kept (INSERT OR IGNORE). Then clear book-local progress.
     */
    public static void migrateLegacyFromBook(SQLiteDatabase bookDb) {
        if (!isAttached(bookDb)) {
            return;
        }
        Cursor c = null;
        try {
            c = bookDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='progress'", null);
            if (!c.moveToFirst()) {
                return;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        int n = 0;
        Cursor count = bookDb.rawQuery("SELECT COUNT(*) FROM progress", null);
        try {
            if (count.moveToFirst()) {
                n = count.getInt(0);
            }
        } finally {
            count.close();
        }
        if (n <= 0) {
            return;
        }

        bookDb.execSQL(
                "INSERT OR IGNORE INTO " + ATTACH_ALIAS + ".progress"
                        + "(word,status,seen,known_count,unknown_count,updated_at,box,due_at,flag) "
                        + "SELECT w.word, p.status, p.seen, p.known_count, p.unknown_count, p.updated_at,"
                        + "IFNULL(p.box,0), IFNULL(p.due_at,0), 0 "
                        + "FROM progress p JOIN words w ON w.id=p.word_id "
                        + "WHERE w.word IS NOT NULL AND w.word!=''");
        bookDb.execSQL("DELETE FROM progress");
    }
}
