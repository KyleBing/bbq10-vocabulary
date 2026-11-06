package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.db.WordDao;

public class LearnedActivity extends Activity {
    private WordDao dao;
    private WordListAdapter adapter;
    private TextView empty;
    private TextView header;
    private ListView list;
    private WordStatusMapView statusMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned);
        dao = ((VocabApp) getApplication()).dao();
        empty = (TextView) findViewById(R.id.empty);
        header = (TextView) findViewById(R.id.header);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        byte[] cells = dao.listAllStatusCells();
        statusMap.setCells(cells);
        List<Word> words = dao.listKnown(5000);
        adapter.setItems(words);
        ProgressStats s = dao.stats();
        header.setText("词库 " + cells.length + " · 已会 " + s.known
                + " · 不会 " + s.unknown + " · 暂存 " + s.stashed);
        boolean none = words.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
    }
}
