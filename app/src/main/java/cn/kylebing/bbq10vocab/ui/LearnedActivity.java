package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.db.WordDao;

public class LearnedActivity extends Activity {
    private WordDao dao;
    private TextView empty;
    private TextView header;
    private TextView wordCloud;
    private ScrollView wordScroll;
    private WordStatusMapView statusMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned);
        dao = ((VocabApp) getApplication()).dao();
        empty = (TextView) findViewById(R.id.empty);
        header = (TextView) findViewById(R.id.header);
        wordCloud = (TextView) findViewById(R.id.wordCloud);
        wordScroll = (ScrollView) findViewById(R.id.wordScroll);
        statusMap = (WordStatusMapView) findViewById(R.id.statusMap);
        wordCloud.setMovementMethod(LinkMovementMethod.getInstance());
        wordCloud.setHighlightColor(0x332A4A7A);
    }

    @Override
    protected void onResume() {
        super.onResume();
        byte[] cells = dao.listAllStatusCells();
        statusMap.setCells(cells);
        List<Word> words = dao.listKnown(5000);
        ProgressStats s = dao.stats();
        header.setText("词库 " + cells.length + " · 已会 " + s.known
                + " · 不会 " + s.unknown + " · 暂存 " + s.stashed);
        boolean none = words.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        wordScroll.setVisibility(none ? View.GONE : View.VISIBLE);
        if (!none) {
            wordCloud.setText(buildCloud(words));
        } else {
            wordCloud.setText("");
        }
    }

    private CharSequence buildCloud(List<Word> words) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < words.size(); i++) {
            final Word w = words.get(i);
            if (i > 0) {
                sb.append("   ");
            }
            int start = sb.length();
            sb.append(w.word);
            int end = sb.length();
            final int color = w.isStashed() ? 0xFFFFCC80 : Color.WHITE;
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    Intent intent = new Intent(LearnedActivity.this, DetailActivity.class);
                    intent.putExtra(DetailActivity.EXTRA_WORD_ID, w.id);
                    startActivity(intent);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setUnderlineText(false);
                    ds.setColor(color);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
}
