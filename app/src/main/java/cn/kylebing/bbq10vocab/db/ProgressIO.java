package cn.kylebing.bbq10vocab.db;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compact machine-readable progress backup.
 * <pre>
 * gzip(
 *   "VPRG" | fmt:u8 | appVerLen:u8 | appVer:utf8 |
 *   exportedAt:u64 | count:u32 |
 *   repeat count: wordLen:u16 | word:utf8 |
 *     status:u8 | seen:u16 | known:u16 | unknown:u16 |
 *     updated_at:u64 | box:u8 | due_at:u64 | flag:u8
 * )
 * </pre>
 */
public final class ProgressIO {
    public static final String EXT = ".vprg";
    public static final byte[] MAGIC = new byte[]{'V', 'P', 'R', 'G'};
    public static final int FMT = 1;

    private ProgressIO() {
    }

    /** Aggregate counts for overview dialogs. */
    public static final class Summary {
        public int total;
        public int known;
        public int unknown;
        public int stashed;
        public int due;

        public String toOverviewLines() {
            return "合计 " + total
                    + "\n会 " + known
                    + "  ·  不会 " + unknown
                    + "\n暂存 " + stashed
                    + "  ·  到期 " + due;
        }
    }

    public static final class Meta {
        public String appVersion;
        public long exportedAt;
        public int count;
        public final Summary summary = new Summary();
    }

    public static final class Result {
        public final File file;
        public final Meta meta;

        public Result(File file, Meta meta) {
            this.file = file;
            this.meta = meta;
        }
    }

    public static File defaultExportFile(Context context) {
        String ver = appVersion(context).replaceAll("[^0-9A-Za-z._-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String name = "vocab-progress-" + ver + "-" + stamp + EXT;
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = context.getApplicationContext().getExternalFilesDir(null);
        }
        if (dir == null) {
            dir = context.getApplicationContext().getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, name);
    }

    /** Local progress overview (does not require book DB). */
    public static Summary summarizeLocal(Context context) {
        ProgressDb.ensureExists(context);
        Summary s = new Summary();
        long now = System.currentTimeMillis();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                ProgressDb.file(context).getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY);
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "SELECT status,flag,due_at FROM progress WHERE word IS NOT NULL AND word!=''",
                    null);
            while (c.moveToNext()) {
                s.total++;
                int status = c.getInt(0);
                int flag = c.getInt(1);
                long dueAt = c.getLong(2);
                if (flag == DbHelper.FLAG_STASH) {
                    s.stashed++;
                }
                if (status == DbHelper.STATUS_KNOWN) {
                    s.known++;
                } else if (status == DbHelper.STATUS_UNKNOWN) {
                    s.unknown++;
                }
                if (dueAt > 0 && dueAt <= now) {
                    s.due++;
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
        }
        return s;
    }

    public static Result exportTo(Context context, File dest) throws Exception {
        ProgressDb.ensureExists(context);
        Meta meta = new Meta();
        meta.appVersion = appVersion(context);
        meta.exportedAt = System.currentTimeMillis();

        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        if (tmp.exists()) {
            tmp.delete();
        }

        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                ProgressDb.file(context).getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY);
        Cursor c = null;
        DataOutputStream out = null;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*) FROM progress WHERE word IS NOT NULL AND word!=''", null);
            meta.count = c.moveToFirst() ? c.getInt(0) : 0;
            c.close();
            c = null;

            c = db.rawQuery(
                    "SELECT word,status,seen,known_count,unknown_count,updated_at,box,due_at,flag "
                            + "FROM progress WHERE word IS NOT NULL AND word!='' ORDER BY word",
                    null);

            out = new DataOutputStream(new GZIPOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tmp), 8192)));
            out.write(MAGIC);
            out.writeByte(FMT);
            writeShortString(out, meta.appVersion);
            out.writeLong(meta.exportedAt);
            out.writeInt(meta.count);
            long now = meta.exportedAt;
            while (c.moveToNext()) {
                String word = c.getString(0);
                int status = c.getInt(1);
                int seen = c.getInt(2);
                int known = c.getInt(3);
                int unknown = c.getInt(4);
                long updated = c.getLong(5);
                int box = c.getInt(6);
                long due = c.getLong(7);
                int flag = c.getInt(8);
                writeUtf16Len(out, word);
                out.writeByte(clampU8(status));
                out.writeShort(clampU16(seen));
                out.writeShort(clampU16(known));
                out.writeShort(clampU16(unknown));
                out.writeLong(updated);
                out.writeByte(clampU8(box));
                out.writeLong(due);
                out.writeByte(clampU8(flag));
                tally(meta.summary, status, flag, due, now);
            }
            out.flush();
        } finally {
            if (c != null) {
                c.close();
            }
            db.close();
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }

        if (dest.exists()) {
            dest.delete();
        }
        if (!tmp.renameTo(dest)) {
            copyFile(tmp, dest);
            tmp.delete();
        }
        return new Result(dest, meta);
    }

    /** Read file meta + summary without writing to DB. */
    public static Meta previewFile(File file) throws Exception {
        InputStream in = new FileInputStream(file);
        try {
            return preview(in);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static Meta preview(InputStream input) throws Exception {
        DataInputStream data = openData(input);
        try {
            return readAll(data, null);
        } finally {
            try {
                data.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static Meta importFrom(Context context, InputStream input) throws Exception {
        ProgressDb.ensureExists(context);
        // Buffer whole gzip payload so we can preview-count then apply from memory if needed.
        // For large files stream once into apply.
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                ProgressDb.file(context).getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE);
        DataInputStream data = null;
        try {
            data = openData(input);
            db.beginTransaction();
            Meta meta = readAll(data, db);
            db.setTransactionSuccessful();
            return meta;
        } finally {
            try {
                db.endTransaction();
            } catch (Exception ignored) {
            }
            db.close();
            if (data != null) {
                try {
                    data.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static Meta importFromFile(Context context, File file) throws Exception {
        InputStream in = new FileInputStream(file);
        try {
            return importFrom(context, in);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean looksLikeProgressName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(EXT) || lower.contains("vocab-progress");
    }

    public static String formatTime(long millis) {
        if (millis <= 0) {
            return "?";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private static DataInputStream openData(InputStream input) throws Exception {
        InputStream in = new BufferedInputStream(input, 8192);
        in.mark(4);
        int b0 = in.read();
        int b1 = in.read();
        in.reset();
        if (b0 != 0x1f || b1 != 0x8b) {
            throw new IllegalArgumentException("不是进度文件（需要 " + EXT + "）");
        }
        return new DataInputStream(new GZIPInputStream(in));
    }

    /**
     * Parse records. If {@code db} non-null, upsert each row.
     */
    private static Meta readAll(DataInputStream data, SQLiteDatabase db) throws Exception {
        byte[] magic = new byte[4];
        data.readFully(magic);
        if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1]
                || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
            throw new IllegalArgumentException("进度文件头无效");
        }
        int fmt = data.readUnsignedByte();
        if (fmt != FMT) {
            throw new IllegalArgumentException("不支持的进度格式 v" + fmt);
        }

        Meta meta = new Meta();
        meta.appVersion = readShortString(data);
        meta.exportedAt = data.readLong();
        meta.count = data.readInt();
        if (meta.count < 0 || meta.count > 500000) {
            throw new IllegalArgumentException("进度条数异常: " + meta.count);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < meta.count; i++) {
            String word = readUtf16Len(data);
            int status = data.readUnsignedByte();
            int seen = data.readUnsignedShort();
            int known = data.readUnsignedShort();
            int unknown = data.readUnsignedShort();
            long updated = data.readLong();
            int box = data.readUnsignedByte();
            long due = data.readLong();
            int flag = data.readUnsignedByte();
            if (word == null || word.length() == 0) {
                continue;
            }
            tally(meta.summary, status, flag, due, now);
            if (db != null) {
                db.execSQL(
                        "INSERT OR REPLACE INTO progress"
                                + "(word,status,seen,known_count,unknown_count,updated_at,box,due_at,flag) "
                                + "VALUES(?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                                word, status, seen, known, unknown, updated, box, due, flag
                        });
            }
        }
        meta.summary.total = meta.count;
        // recount total from tallies if some empty skipped
        int tallied = meta.summary.known + meta.summary.unknown;
        // unseen-in-progress possible; keep header count as total
        if (meta.summary.total < tallied) {
            meta.summary.total = tallied;
        }
        return meta;
    }

    private static void tally(Summary s, int status, int flag, long dueAt, long now) {
        s.total++;
        if (flag == DbHelper.FLAG_STASH) {
            s.stashed++;
        }
        if (status == DbHelper.STATUS_KNOWN) {
            s.known++;
        } else if (status == DbHelper.STATUS_UNKNOWN) {
            s.unknown++;
        }
        if (dueAt > 0 && dueAt <= now) {
            s.due++;
        }
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static void writeShortString(DataOutputStream out, String s) throws Exception {
        if (s == null) {
            s = "";
        }
        byte[] bytes = s.getBytes("UTF-8");
        if (bytes.length > 255) {
            byte[] cut = new byte[255];
            System.arraycopy(bytes, 0, cut, 0, 255);
            bytes = cut;
        }
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    private static String readShortString(DataInputStream in) throws Exception {
        int n = in.readUnsignedByte();
        byte[] bytes = new byte[n];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private static void writeUtf16Len(DataOutputStream out, String s) throws Exception {
        byte[] bytes = s.getBytes("UTF-8");
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("单词过长");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readUtf16Len(DataInputStream in) throws Exception {
        int n = in.readUnsignedShort();
        byte[] bytes = new byte[n];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private static int clampU8(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    private static int clampU16(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 65535) {
            return 65535;
        }
        return v;
    }

    private static void copyFile(File src, File dest) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dest);
        try {
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
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }
}
