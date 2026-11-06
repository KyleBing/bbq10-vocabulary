package cn.kylebing.bbq10vocab.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Streams import line-by-line into SQLite so large jsonl files do not need to
 * sit fully in memory (important on Android 4.3 / BB10).
 */
public class BookImporter {
    public static class Result {
        public final File dbFile;
        public final String title;
        public final int count;

        public Result(File dbFile, String title, int count) {
            this.dbFile = dbFile;
            this.title = title;
            this.count = count;
        }
    }

    public static Result importUri(Context context, Uri uri, String displayName) throws Exception {
        String name = displayName == null ? "" : displayName;
        String lower = name.toLowerCase(Locale.US);
        String path = uri.getLastPathSegment();
        if (path != null) {
            lower = (lower + " " + path).toLowerCase(Locale.US);
        }

        if (lower.endsWith(".db") || lower.contains(".db") || looksLikeSqlite(uri, name)) {
            return importReadyDb(context, uri, name);
        }

        InputStream in = openReadable(context, uri, name);
        if (in == null) {
            throw new IOExceptionFriendly("无法打开文件（权限不足）。请改用 App 内「切换词库」选内置词库。");
        }

        String title = cleanTitle(name);
        File tmp = new File(context.getCacheDir(), "import_tmp.db");
        if (tmp.exists()) {
            tmp.delete();
        }

        int count;
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(tmp, null);
        try {
            DbHelper.ensureSchema(db);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 8 * 1024);
            try {
                count = streamImport(db, reader, title);
            } finally {
                reader.close();
            }
        } finally {
            db.close();
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }

        if (count <= 0) {
            tmp.delete();
            throw new IOExceptionFriendly("文件里没有可用词条（支持 db / jsonl / tsv / txt）");
        }
        return new Result(tmp, title, count);
    }

    private static Result importReadyDb(Context context, Uri uri, String displayName) throws Exception {
        File tmp = new File(context.getCacheDir(), "import_tmp.db");
        if (tmp.exists()) {
            tmp.delete();
        }
        InputStream in = openReadable(context, uri, displayName);
        if (in == null) {
            throw new IOExceptionFriendly("无法打开数据库文件（权限不足）。请改用 App 内切换内置词库。");
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }

        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                tmp.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        int count;
        String title;
        try {
            DbHelper.ensureSchema(db);
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM words", null);
            try {
                c.moveToFirst();
                count = c.getInt(0);
            } finally {
                c.close();
            }
            if (count <= 0) {
                throw new IOExceptionFriendly("数据库里没有词条");
            }
            title = cleanTitle(displayName);
            android.database.Cursor m = db.rawQuery(
                    "SELECT value FROM meta WHERE key='book_title'", null);
            try {
                if (m.moveToFirst()) {
                    String t = m.getString(0);
                    if (t != null && t.length() > 0) {
                        title = t;
                    }
                } else {
                    DbHelper.setMeta(db, "book_title", title);
                }
            } finally {
                m.close();
            }
        } finally {
            db.close();
        }
        return new Result(tmp, title, count);
    }

    private static int streamImport(SQLiteDatabase db, BufferedReader reader, String title)
            throws Exception {
        String first = null;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0) {
                first = line;
                break;
            }
        }
        if (first == null) {
            return 0;
        }

        boolean jsonl = first.startsWith("{");
        int count = 0;
        db.beginTransaction();
        try {
            count += insertLine(db, first, jsonl) ? 1 : 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                if (insertLine(db, line, jsonl)) {
                    count++;
                    if (count % 200 == 0) {
                        // yield a bit; keep transaction but avoid huge pending journal spikes
                        db.yieldIfContendedSafely();
                    }
                }
            }
            DbHelper.setMeta(db, "book_title", title);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    private static boolean insertLine(SQLiteDatabase db, String line, boolean jsonl) throws Exception {
        Entry e = jsonl ? parseJsonLine(line) : parseTsvOrTxtLine(line);
        if (e == null || e.word == null || e.word.length() == 0) {
            return false;
        }
        db.execSQL(
                "INSERT INTO words(word,us,uk,trans,example_en,example_cn) VALUES(?,?,?,?,?,?)",
                new Object[]{e.word, e.us, e.uk, e.trans, e.exampleEn, e.exampleCn});
        return true;
    }

    private static Entry parseJsonLine(String line) throws Exception {
        JSONObject o = new JSONObject(line);
        Entry e = new Entry();
        e.word = o.optString("word", "").trim();
        if (e.word.length() == 0) {
            e.word = o.optString("headWord", "").trim();
        }
        if (e.word.length() == 0) {
            return null;
        }
        e.us = o.optString("us", "").trim();
        e.uk = o.optString("uk", "").trim();
        e.trans = compactTrans(o.optJSONArray("translations"));
        if (e.trans.length() == 0) {
            e.trans = o.optString("trans", "").trim();
        }
        JSONArray sentences = o.optJSONArray("sentences");
        if (sentences != null && sentences.length() > 0) {
            JSONObject s = sentences.getJSONObject(0);
            e.exampleEn = s.optString("sentence", s.optString("sContent", "")).trim();
            e.exampleCn = s.optString("translation", s.optString("sCn", "")).trim();
        }
        return e;
    }

    private static String compactTrans(JSONArray arr) throws Exception {
        if (arr == null || arr.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.getJSONObject(i);
            String translation = t.optString("translation", "").trim();
            String type = t.optString("type", "").trim();
            if (translation.length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if (type.length() > 0) {
                sb.append(type).append(". ");
            }
            sb.append(translation);
        }
        return sb.toString();
    }

    private static Entry parseTsvOrTxtLine(String line) {
        if (line.startsWith("#") || line.startsWith("word\t")) {
            return null;
        }
        Entry e = new Entry();
        String[] parts = line.split("\t");
        if (parts.length >= 2) {
            e.word = parts[0].trim();
            if (parts.length >= 5 && looksLikePhone(parts[1])) {
                e.us = parts[1].trim();
                e.uk = parts[2].trim();
                e.trans = parts[3].trim();
                if (parts.length > 5) {
                    e.exampleEn = parts[parts.length - 2].trim();
                    e.exampleCn = parts[parts.length - 1].trim();
                }
            } else {
                e.trans = parts[1].trim();
                if (parts.length >= 4) {
                    e.exampleEn = parts[2].trim();
                    e.exampleCn = parts[3].trim();
                } else if (parts.length == 3) {
                    e.exampleEn = parts[2].trim();
                }
            }
        } else {
            int sp = line.indexOf(' ');
            if (sp <= 0) {
                return null;
            }
            e.word = line.substring(0, sp).trim();
            e.trans = line.substring(sp + 1).trim();
        }
        if (e.word.length() == 0) {
            return null;
        }
        return e;
    }

    private static boolean looksLikePhone(String s) {
        if (s == null || s.length() == 0) {
            return false;
        }
        return s.indexOf('/') >= 0 || s.indexOf('ˈ') >= 0 || s.indexOf('ˌ') >= 0
                || s.indexOf('ə') >= 0 || s.indexOf('\'') >= 0;
    }

    /** Prefer content resolver, then file:// path, then common Download folders. */
    public static InputStream openReadable(Context context, Uri uri, String displayName) {
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in != null) {
                return in;
            }
        } catch (SecurityException se) {
            // fall through to path attempts
        } catch (Exception ignored) {
        }

        String path = uri.getPath();
        if (path != null) {
            try {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    return new java.io.FileInputStream(f);
                }
            } catch (Exception ignored) {
            }
            // file:///storage/... sometimes encoded
            try {
                String decoded = Uri.decode(path);
                File f = new File(decoded);
                if (f.exists() && f.canRead()) {
                    return new java.io.FileInputStream(f);
                }
            } catch (Exception ignored) {
            }
        }

        String base = displayName == null ? "" : displayName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf(':'));
        if (slash >= 0 && slash + 1 < base.length()) {
            base = base.substring(slash + 1);
        }
        if (base.length() > 0) {
            String[] roots = new String[]{
                    "/sdcard/Download/",
                    "/sdcard/Downloads/",
                    "/storage/emulated/0/Download/",
                    "/storage/sdcard0/Download/",
                    "/mnt/sdcard/Download/",
            };
            for (int i = 0; i < roots.length; i++) {
                try {
                    File f = new File(roots[i] + base);
                    if (f.exists() && f.canRead()) {
                        return new java.io.FileInputStream(f);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static boolean looksLikeSqlite(Uri uri, String name) {
        String s = ((name == null ? "" : name) + " " + (uri == null ? "" : uri.toString()))
                .toLowerCase(Locale.US);
        return s.contains(".db") || s.endsWith("db");
    }

    private static String cleanTitle(String displayName) {
        String title = displayName == null || displayName.length() == 0 ? "导入词库" : displayName;
        int slash = Math.max(title.lastIndexOf('/'), title.lastIndexOf(':'));
        if (slash >= 0 && slash + 1 < title.length()) {
            title = title.substring(slash + 1);
        }
        int dot = title.lastIndexOf('.');
        if (dot > 0) {
            title = title.substring(0, dot);
        }
        return title;
    }

    private static class Entry {
        String word;
        String us = "";
        String uk = "";
        String trans = "";
        String exampleEn = "";
        String exampleCn = "";
    }

    public static class IOExceptionFriendly extends Exception {
        public IOExceptionFriendly(String message) {
            super(message);
        }
    }
}
