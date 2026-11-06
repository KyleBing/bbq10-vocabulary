package cn.kylebing.bbq10vocab.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.Word;

public class WordDao {
    /** Join global progress by word text (up.progress), not by book-local word_id. */
    private static final String COLS =
            "SELECT w.id,w.word,w.us,w.uk,w.trans,w.example_en,w.example_cn,"
                    + "IFNULL(p.status,0),IFNULL(p.flag,0) ";
    private static final String JOIN_PROG =
            " LEFT JOIN " + ProgressDb.ATTACH_ALIAS + ".progress p ON p.word=w.word ";

    private final SQLiteDatabase db;

    public WordDao(SQLiteDatabase db) {
        this.db = db;
    }

    public String bookTitle() {
        Cursor c = db.rawQuery("SELECT value FROM meta WHERE key='book_title'", null);
        try {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        } finally {
            c.close();
        }
        return "词库";
    }

    public ProgressStats stats() {
        int total = scalar("SELECT COUNT(*) FROM words");
        int known = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status=" + DbHelper.STATUS_KNOWN);
        int unknown = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status=" + DbHelper.STATUS_UNKNOWN);
        long now = System.currentTimeMillis();
        int due = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.due_at>0 AND p.due_at<=" + now
                + " AND p.status!=" + DbHelper.STATUS_UNSEEN);
        int stashed = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.flag=" + DbHelper.FLAG_STASH);
        ProgressStats s = new ProgressStats(total, known, unknown);
        s.due = due;
        s.stashed = stashed;
        return s;
    }

    public Word getById(long id) {
        Cursor c = db.rawQuery(
                COLS + "FROM words w" + JOIN_PROG + "WHERE w.id=?",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    /**
     * Next card. Queue respects due_at so「不会/暂存」won't bounce back on the next card.
     * Order: due-unknown → due-known → unseen (keep learning) → due-stash (later reinforce).
     */
    public Word nextReview(long excludeId) {
        long now = System.currentTimeMillis();
        String excl = excludeId > 0 ? (" AND w.id!=" + excludeId) : "";
        // due_at<=0 treated as already due (legacy rows)
        String due = " AND (p.due_at<=0 OR p.due_at<=" + now + ")";

        // 1) due hard-unknown
        Word w = one(COLS + "FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status="
                + DbHelper.STATUS_UNKNOWN
                + " AND IFNULL(p.flag,0)!=" + DbHelper.FLAG_STASH + due + excl
                + " ORDER BY p.due_at ASC, p.updated_at ASC LIMIT 1");
        if (w != null) {
            return w;
        }

        // 2) due known (Leitner)
        w = one(COLS + "FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status="
                + DbHelper.STATUS_KNOWN
                + " AND p.due_at>0 AND p.due_at<=" + now + excl
                + " ORDER BY p.due_at ASC LIMIT 1");
        if (w != null) {
            return w;
        }

        // 3) unseen — continue learning; stash must not block new words
        w = one(COLS + "FROM words w" + JOIN_PROG
                + "WHERE (p.word IS NULL OR p.status=" + DbHelper.STATUS_UNSEEN + ")" + excl
                + " ORDER BY RANDOM() LIMIT 1");
        if (w != null) {
            return w;
        }

        // 4) due stash — reinforce later, after new/due work
        w = one(COLS + "FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.flag="
                + DbHelper.FLAG_STASH + due + excl
                + " ORDER BY p.due_at ASC, p.updated_at ASC LIMIT 1");
        if (w != null) {
            return w;
        }

        // Do not force the just-marked word back; empty queue is OK.
        return null;
    }

    public Word nextReview() {
        return nextReview(-1);
    }

    public List<Word> searchPrefix(String prefix, int limit) {
        List<Word> list = new ArrayList<Word>();
        if (prefix == null) {
            prefix = "";
        }
        prefix = prefix.trim();
        if (prefix.length() == 0) {
            return list;
        }
        Cursor c = db.rawQuery(
                COLS + "FROM words w" + JOIN_PROG
                        + "WHERE w.word LIKE ? ORDER BY w.word LIMIT ?",
                new String[]{prefix + "%", String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public List<Word> listKnown(int limit) {
        List<Word> list = new ArrayList<Word>();
        Cursor c = db.rawQuery(
                COLS + "FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                        + ".progress p ON p.word=w.word WHERE p.status=" + DbHelper.STATUS_KNOWN
                        + " ORDER BY w.word ASC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /** Status mosaic for every word in the active book (order by id). */
    public byte[] listAllStatusCells() {
        Cursor c = db.rawQuery(
                "SELECT IFNULL(p.status,0), IFNULL(p.flag,0) FROM words w" + JOIN_PROG
                        + "ORDER BY w.id",
                null);
        try {
            byte[] cells = new byte[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                cells[i++] = DbHelper.statusCell(c.getInt(0), c.getInt(1));
            }
            return cells;
        } finally {
            c.close();
        }
    }

    /** Non-overlapping buckets for the home stacked bar. */
    public int[] statusBuckets() {
        ProgressStats s = stats();
        int stash = s.stashed;
        // known/unknown counts include stashed rows; subtract stash from them for bar segments.
        int knownPlain = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status=" + DbHelper.STATUS_KNOWN
                + " AND IFNULL(p.flag,0)!=" + DbHelper.FLAG_STASH);
        int unknownPlain = scalar("SELECT COUNT(*) FROM words w JOIN " + ProgressDb.ATTACH_ALIAS
                + ".progress p ON p.word=w.word WHERE p.status=" + DbHelper.STATUS_UNKNOWN
                + " AND IFNULL(p.flag,0)!=" + DbHelper.FLAG_STASH);
        int used = knownPlain + unknownPlain + stash;
        int unseen = s.total - used;
        if (unseen < 0) {
            unseen = 0;
        }
        return new int[]{knownPlain, unknownPlain, stash, unseen};
    }

    public void mark(long wordId, boolean known) {
        Word word = getById(wordId);
        if (word == null || word.word == null || word.word.length() == 0) {
            return;
        }
        markWord(word.word, known);
    }

    /** 暂存 / 不太会：稍后加强，不插队到下一张。 */
    public void markStash(long wordId) {
        Word word = getById(wordId);
        if (word == null || word.word == null || word.word.length() == 0) {
            return;
        }
        markStashWord(word.word);
    }

    public void markWord(String headWord, boolean known) {
        if (headWord == null || headWord.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Cursor c = db.rawQuery(
                "SELECT status,seen,known_count,unknown_count,box,IFNULL(flag,0) FROM "
                        + ProgressDb.ATTACH_ALIAS + ".progress WHERE word=?",
                new String[]{headWord});
        try {
            int box = 0;
            int seen = 1;
            int knownCount = 0;
            int unknownCount = 0;
            int flag = DbHelper.FLAG_NONE;
            int status;
            long dueAt;
            boolean exists = c.moveToFirst();
            if (exists) {
                seen = c.getInt(1) + 1;
                knownCount = c.getInt(2);
                unknownCount = c.getInt(3);
                box = c.getInt(4);
                flag = c.getInt(5);
            }
            if (known) {
                knownCount++;
                if (box < 2) {
                    box = 2;
                } else if (box < DbHelper.BOX_INTERVAL_MS.length - 1) {
                    box++;
                }
                status = DbHelper.STATUS_KNOWN;
                dueAt = now + DbHelper.BOX_INTERVAL_MS[box];
                flag = DbHelper.FLAG_NONE; // 会 → 清暂存
            } else {
                unknownCount++;
                box = 1;
                status = DbHelper.STATUS_UNKNOWN;
                dueAt = now + DbHelper.UNKNOWN_RETRY_MS;
                // 不会 keeps existing stash flag if any
            }
            upsertProgress(headWord, exists, status, seen, knownCount, unknownCount, now, box, dueAt, flag);
        } finally {
            c.close();
        }
    }

    public void markStashWord(String headWord) {
        if (headWord == null || headWord.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Cursor c = db.rawQuery(
                "SELECT status,seen,known_count,unknown_count,box FROM "
                        + ProgressDb.ATTACH_ALIAS + ".progress WHERE word=?",
                new String[]{headWord});
        try {
            int box = 1;
            int seen = 1;
            int knownCount = 0;
            int unknownCount = 0;
            int status = DbHelper.STATUS_UNKNOWN;
            boolean exists = c.moveToFirst();
            if (exists) {
                status = c.getInt(0);
                seen = c.getInt(1) + 1;
                knownCount = c.getInt(2);
                unknownCount = c.getInt(3);
                box = c.getInt(4);
                if (status == DbHelper.STATUS_UNSEEN) {
                    status = DbHelper.STATUS_UNKNOWN;
                }
                if (box < 1) {
                    box = 1;
                }
            }
            // Soft park: keep known status if already known; unseen → unknown.
            if (status == DbHelper.STATUS_UNSEEN) {
                status = DbHelper.STATUS_UNKNOWN;
            }
            long dueAt = now + DbHelper.STASH_RETRY_MS;
            upsertProgress(headWord, exists, status, seen, knownCount, unknownCount, now, box, dueAt,
                    DbHelper.FLAG_STASH);
        } finally {
            c.close();
        }
    }

    private void upsertProgress(String headWord, boolean exists, int status, int seen,
                                int knownCount, int unknownCount, long now, int box,
                                long dueAt, int flag) {
        if (exists) {
            db.execSQL(
                    "UPDATE " + ProgressDb.ATTACH_ALIAS + ".progress SET status=?,seen=?,"
                            + "known_count=?,unknown_count=?,updated_at=?,box=?,due_at=?,flag=? WHERE word=?",
                    new Object[]{
                            status, seen, knownCount, unknownCount, now, box, dueAt, flag, headWord
                    });
        } else {
            db.execSQL(
                    "INSERT INTO " + ProgressDb.ATTACH_ALIAS
                            + ".progress(word,status,seen,known_count,unknown_count,updated_at,box,due_at,flag)"
                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                    new Object[]{
                            headWord, status, seen, knownCount, unknownCount, now, box, dueAt, flag
                    });
        }
    }

    private int scalar(String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            return 0;
        } finally {
            c.close();
        }
    }

    private Word one(String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    private static Word fromCursor(Cursor c) {
        Word w = new Word();
        w.id = c.getLong(0);
        w.word = c.getString(1);
        w.us = c.getString(2);
        w.uk = c.getString(3);
        w.trans = c.getString(4);
        w.exampleEn = c.getString(5);
        w.exampleCn = c.getString(6);
        w.status = c.getInt(7);
        w.flag = c.getInt(8);
        return w;
    }
}
