package cn.kylebing.bbq10vocab.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.kylebing.bbq10vocab.Prefs;

public class DbHelper {
    public static final String ASSET_DB = "cet4.db";
    public static final int STATUS_UNSEEN = 0;
    public static final int STATUS_UNKNOWN = 1;
    public static final int STATUS_KNOWN = 2;
    /** progress.flag: parked for later reinforced practice */
    public static final int FLAG_NONE = 0;
    public static final int FLAG_STASH = 1;

    /** Compact status cell for mosaic / progress map. */
    public static final byte CELL_UNSEEN = 0;
    public static final byte CELL_UNKNOWN = 1;
    public static final byte CELL_KNOWN = 2;
    public static final byte CELL_STASH = 3;

    public static byte statusCell(int status, int flag) {
        if (flag == FLAG_STASH) {
            return CELL_STASH;
        }
        if (status == STATUS_KNOWN) {
            return CELL_KNOWN;
        }
        if (status == STATUS_UNKNOWN) {
            return CELL_UNKNOWN;
        }
        return CELL_UNSEEN;
    }

    /** Leitner intervals in milliseconds. */
    public static final long[] BOX_INTERVAL_MS = new long[]{
            0L,
            10L * 60L * 1000L,
            24L * 60L * 60L * 1000L,
            3L * 24L * 60L * 60L * 1000L,
            7L * 24L * 60L * 60L * 1000L,
            14L * 24L * 60L * 60L * 1000L,
            30L * 24L * 60L * 60L * 1000L
    };

    /** After「不会」, wait before it re-enters the queue (avoids immediate loop). */
    public static final long UNKNOWN_RETRY_MS = 3L * 60L * 1000L;
    /** After「暂存」, park longer — reinforce later, not next card. */
    public static final long STASH_RETRY_MS = 30L * 60L * 1000L;

    private static SQLiteDatabase sDb;

    public static synchronized SQLiteDatabase get(Context context) {
        if (sDb != null && sDb.isOpen()) {
            return sDb;
        }
        Context app = context.getApplicationContext();
        File dest = app.getDatabasePath(Prefs.activeDbName(app));
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!dest.exists()) {
            copyAsset(app, ASSET_DB, dest);
            Prefs.setActiveDbName(app, Prefs.DbNames.DEFAULT);
        }
        sDb = SQLiteDatabase.openDatabase(
                dest.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE);
        ensureSchema(sDb);
        ensureMeta(sDb, "四级");
        ProgressDb.attachTo(sDb, app);
        ProgressDb.migrateLegacyFromBook(sDb);
        return sDb;
    }

    public static synchronized void close() {
        if (sDb != null) {
            try {
                sDb.close();
            } catch (Exception ignored) {
            }
            sDb = null;
        }
    }

    public static synchronized SQLiteDatabase reopen(Context context) {
        close();
        return get(context);
    }

    public static synchronized void restoreDefault(Context context) {
        // Do not wipe cet4.db or progress — only switch active book back to 四级.
        switchToBuiltIn(context, BuiltInBooks.CET4, false);
    }

    /**
     * Switch to a packaged book. Keeps local progress if that db already exists,
     * unless forceReplace is true.
     */
    public static synchronized void switchToBuiltIn(Context context, BuiltInBooks.Item book,
                                                    boolean forceReplace) {
        Context app = context.getApplicationContext();
        close();
        File dest = app.getDatabasePath(book.dbFileName);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (forceReplace && dest.exists()) {
            dest.delete();
        }
        if (!dest.exists()) {
            copyAsset(app, book.assetPath, dest);
        }
        Prefs.setActiveDbName(app, book.dbFileName);
        sDb = SQLiteDatabase.openDatabase(
                dest.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE);
        ensureSchema(sDb);
        ensureMeta(sDb, book.title);
        setMeta(sDb, "book_title", book.title);
        // Never touch user_progress.db — learning history is global by word text.
        ProgressDb.attachTo(sDb, app);
        ProgressDb.migrateLegacyFromBook(sDb);
    }

    public static synchronized void switchToImported(Context context, File importedFile, String title) {
        Context app = context.getApplicationContext();
        close();
        File dest = app.getDatabasePath(Prefs.DbNames.IMPORTED);
        if (dest.exists()) {
            dest.delete();
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!importedFile.renameTo(dest) && !copyFile(importedFile, dest)) {
            throw new RuntimeException("无法安装导入的词库（写入失败）");
        }
        if (importedFile.exists()) {
            importedFile.delete();
        }
        Prefs.setActiveDbName(app, Prefs.DbNames.IMPORTED);
        sDb = SQLiteDatabase.openDatabase(
                dest.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE);
        ensureSchema(sDb);
        String t = title == null || title.length() == 0 ? "导入词库" : title;
        ensureMeta(sDb, t);
        setMeta(sDb, "book_title", t);
        ProgressDb.attachTo(sDb, app);
        ProgressDb.migrateLegacyFromBook(sDb);
    }

    public static void ensureSchema(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS words ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "word TEXT NOT NULL,"
                        + "us TEXT NOT NULL DEFAULT '',"
                        + "uk TEXT NOT NULL DEFAULT '',"
                        + "trans TEXT NOT NULL DEFAULT '',"
                        + "example_en TEXT NOT NULL DEFAULT '',"
                        + "example_cn TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_word ON words(word)");
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS progress ("
                        + "word_id INTEGER PRIMARY KEY,"
                        + "status INTEGER NOT NULL DEFAULT 0,"
                        + "seen INTEGER NOT NULL DEFAULT 0,"
                        + "known_count INTEGER NOT NULL DEFAULT 0,"
                        + "unknown_count INTEGER NOT NULL DEFAULT 0,"
                        + "updated_at INTEGER NOT NULL DEFAULT 0,"
                        + "box INTEGER NOT NULL DEFAULT 0,"
                        + "due_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS meta ("
                        + "key TEXT PRIMARY KEY,"
                        + "value TEXT NOT NULL DEFAULT '')");
        addColumnIfMissing(db, "progress", "box", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(db, "progress", "due_at", "INTEGER NOT NULL DEFAULT 0");
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

    public static void ensureMeta(SQLiteDatabase db, String defaultTitle) {
        Cursor c = db.rawQuery("SELECT value FROM meta WHERE key='book_title'", null);
        try {
            if (!c.moveToFirst()) {
                ContentValues v = new ContentValues();
                v.put("key", "book_title");
                v.put("value", defaultTitle);
                db.insert("meta", null, v);
            }
        } finally {
            c.close();
        }
    }

    public static void setMeta(SQLiteDatabase db, String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value);
        int n = db.update("meta", v, "key=?", new String[]{key});
        if (n == 0) {
            db.insert("meta", null, v);
        }
    }

    private static void copyAsset(Context context, String assetPath, File dest) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getAssets().open(assetPath);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("copy dictionary failed: " + assetPath, e);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static boolean copyFile(File src, File dest) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new java.io.FileInputStream(src);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}
