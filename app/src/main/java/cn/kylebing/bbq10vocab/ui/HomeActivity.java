package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.db.BookImporter;
import cn.kylebing.bbq10vocab.db.BuiltInBooks;
import cn.kylebing.bbq10vocab.db.DbHelper;

public class HomeActivity extends Activity {
    private static final int REQ_IMPORT = 41;

    private TextView title;
    private TextView known;
    private TextView unknown;
    private TextView stash;
    private TextView due;
    private SegmentProgressView progressBar;
    private TextView progressLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        title = (TextView) findViewById(R.id.title);
        known = (TextView) findViewById(R.id.statKnown);
        unknown = (TextView) findViewById(R.id.statUnknown);
        stash = (TextView) findViewById(R.id.statStash);
        due = (TextView) findViewById(R.id.statDue);
        progressBar = (SegmentProgressView) findViewById(R.id.progressBar);
        progressLabel = (TextView) findViewById(R.id.progressLabel);
        ((TextView) findViewById(R.id.versionText)).setText(appVersionLabel());

        String bootErr = ((VocabApp) getApplication()).initError();
        if (bootErr != null) {
            Toast.makeText(this, "启动失败: " + bootErr, Toast.LENGTH_LONG).show();
        }

        findViewById(R.id.btnRecognize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, RecognizeActivity.class));
            }
        });
        findViewById(R.id.btnSpell).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, SpellActivity.class));
            }
        });
        findViewById(R.id.btnLookup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LookupActivity.class));
            }
        });
        findViewById(R.id.btnLearned).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LearnedActivity.class));
            }
        });
        findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, HelpActivity.class));
            }
        });
        findViewById(R.id.btnImport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBookPicker();
            }
        });
        findViewById(R.id.btnRestore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(HomeActivity.this)
                        .setMessage("切回内置四级词库？已学进度按单词全局保存，不会清空。")
                        .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // keep existing cet4.db (and its unused local table); progress is global
                                DbHelper.switchToBuiltIn(HomeActivity.this, BuiltInBooks.ALL[0], false);
                                ((VocabApp) getApplication()).reloadDao();
                                Toast.makeText(HomeActivity.this, R.string.restore_ok, Toast.LENGTH_SHORT).show();
                                refresh();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    private void showBookPicker() {
        final BuiltInBooks.Item[] books = BuiltInBooks.ALL;
        CharSequence[] labels = new CharSequence[books.length + 1];
        for (int i = 0; i < books.length; i++) {
            labels[i] = books[i].title + "（内置）";
        }
        labels[books.length] = "从文件导入…";

        new AlertDialog.Builder(this)
                .setTitle(R.string.mode_import)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < books.length) {
                            switchBuiltIn(books[which]);
                        } else {
                            openFilePicker();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchBuiltIn(final BuiltInBooks.Item book) {
        Toast.makeText(this, "正在切换到 " + book.title + "…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DbHelper.switchToBuiltIn(HomeActivity.this, book, false);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((VocabApp) getApplication()).reloadDao();
                            Toast.makeText(HomeActivity.this,
                                    "已切换：" + book.title,
                                    Toast.LENGTH_SHORT).show();
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                            Toast.makeText(HomeActivity.this,
                                    getString(R.string.import_fail, msg),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.mode_import)), REQ_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器，请用内置词库", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        try {
            VocabApp app = (VocabApp) getApplication();
            title.setText(app.dao().bookTitle());
            ProgressStats s = app.dao().stats();
            known.setText("会 " + s.known);
            unknown.setText("不会 " + s.unknown);
            stash.setText("暂存 " + s.stashed);
            due.setText("到期 " + s.due);
            int[] buckets = app.dao().statusBuckets();
            progressBar.setSegments(buckets, new int[]{
                    0xFF7DCE82, // known
                    0xFFEF9A9A, // unknown
                    0xFFFFCC80, // stash
                    0xFF3A3A3A  // unseen
            });
            int total = s.total <= 0 ? 1 : s.total;
            int learned = buckets[0] + buckets[1] + buckets[2];
            int pct = (learned * 100) / total;
            progressLabel.setText("进度 " + learned + "/" + total + "（" + pct + "%）  绿会·红不会·橙暂存·灰未学");
        } catch (Throwable t) {
            Toast.makeText(this, "加载失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String appVersionLabel() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + pi.versionName;
        } catch (Exception e) {
            return "v1.0.0.19";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        try {
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        final String name = queryDisplayName(uri);
        // Copy into app cache on this thread while URI grant is still fresh (BB10 often
        // denies later background openInputStream).
        File cached;
        try {
            cached = copyUriToCacheNow(uri, name);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            Toast.makeText(this, getString(R.string.import_fail, msg), Toast.LENGTH_LONG).show();
            return;
        }

        final File cachedFile = cached;
        Toast.makeText(this, R.string.importing, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BookImporter.Result result = BookImporter.importUri(
                            HomeActivity.this, Uri.fromFile(cachedFile), name);
                    DbHelper.switchToImported(HomeActivity.this, result.dbFile, result.title);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((VocabApp) getApplication()).reloadDao();
                            Toast.makeText(HomeActivity.this,
                                    getString(R.string.import_ok, result.count, result.title),
                                    Toast.LENGTH_LONG).show();
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                            Toast.makeText(HomeActivity.this,
                                    getString(R.string.import_fail, msg),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (cachedFile != null && cachedFile.exists()) {
                        // import may have moved/renamed; ignore
                        cachedFile.delete();
                    }
                }
            }
        }).start();
    }

    private File copyUriToCacheNow(Uri uri, String displayName) throws Exception {
        InputStream in = BookImporter.openReadable(this, uri, displayName);
        if (in == null) {
            // last try: Downloads/<name>
            String base = displayName == null ? "" : displayName;
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null && base.length() > 0) {
                File f = new File(dl, base);
                if (f.exists() && f.canRead()) {
                    in = new java.io.FileInputStream(f);
                }
            }
        }
        if (in == null) {
            throw new BookImporter.IOExceptionFriendly(
                    "Permission denied：读不到该文件。请改用「切换词库」选内置四级/六级等。");
        }
        String suffix = ".bin";
        String lower = displayName == null ? "" : displayName.toLowerCase();
        if (lower.endsWith(".db")) {
            suffix = ".db";
        } else if (lower.endsWith(".jsonl") || lower.endsWith(".json")) {
            suffix = ".jsonl";
        } else if (lower.endsWith(".tsv") || lower.endsWith(".txt")) {
            suffix = ".txt";
        }
        File out = new File(getCacheDir(), "picked_import" + suffix);
        OutputStream os = null;
        try {
            os = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private String queryDisplayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return c.getString(idx);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return uri.getLastPathSegment();
    }
}
