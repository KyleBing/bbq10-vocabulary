package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.db.WordDao;

public class LearnedActivity extends Activity {
    private static final int COL_BOOK = 0xFFB0B0B0;
    private static final int COL_KNOWN = 0xFF7DCE82;
    private static final int COL_UNKNOWN = 0xFFEF9A9A;
    private static final int COL_STASH = 0xFFFFCC80;
    private static final int COL_DIM = 0xFF9A9A9A;

    private WordDao dao;
    private TextView empty;
    private TextView header;
    private TextView legend;
    private ListView list;
    private WordStatusMapView statusMap;
    private WordListAdapter adapter;
    private int loadToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned);
        dao = ((VocabApp) getApplication()).dao();
        empty = (TextView) findViewById(R.id.empty);
        header = (TextView) findViewById(R.id.header);
        legend = (TextView) findViewById(R.id.legend);
        list = (ListView) findViewById(R.id.list);
        statusMap = (WordStatusMapView) findViewById(R.id.statusMap);
        adapter = new WordListAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Word w = adapter.getItem(position);
                Intent intent = new Intent(LearnedActivity.this, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_WORD_ID, w.id);
                startActivity(intent);
            }
        });
        legend.setText(buildLegend());
    }

    @Override
    protected void onResume() {
        super.onResume();
        final int token = ++loadToken;
        header.setText("加载中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final byte[] cells = dao.listAllStatusCells();
                final List<Word> words = dao.listKnown(5000);
                final ProgressStats s = dao.stats();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken || isFinishing()) {
                            return;
                        }
                        statusMap.setCells(cells);
                        header.setText(buildHeader(cells.length, s));
                        boolean none = words == null || words.isEmpty();
                        empty.setVisibility(none ? View.VISIBLE : View.GONE);
                        list.setVisibility(none ? View.GONE : View.VISIBLE);
                        adapter.setItems(none ? Collections.<Word>emptyList() : words);
                    }
                });
            }
        }).start();
    }

    private CharSequence buildHeader(int bookSize, ProgressStats s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, "词库 ", COL_DIM);
        append(sb, String.valueOf(bookSize), COL_BOOK);
        append(sb, "  ·  ", COL_DIM);
        append(sb, "已会 ", COL_DIM);
        append(sb, String.valueOf(s.known), COL_KNOWN);
        append(sb, "  ·  ", COL_DIM);
        append(sb, "不会 ", COL_DIM);
        append(sb, String.valueOf(s.unknown), COL_UNKNOWN);
        append(sb, "  ·  ", COL_DIM);
        append(sb, "暂存 ", COL_DIM);
        append(sb, String.valueOf(s.stashed), COL_STASH);
        return sb;
    }

    private CharSequence buildLegend() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, "绿", COL_KNOWN);
        append(sb, "=会  ", COL_DIM);
        append(sb, "红", COL_UNKNOWN);
        append(sb, "=不会  ", COL_DIM);
        append(sb, "橙", COL_STASH);
        append(sb, "=暂存  ", COL_DIM);
        append(sb, "灰=未学 · 点词看释义", COL_DIM);
        return sb;
    }

    private static void append(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
