package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.db.WordDao;

public class LookupActivity extends Activity {
    private WordDao dao;
    private WordListAdapter adapter;
    private TextView empty;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lookup);
        dao = ((VocabApp) getApplication()).dao();
        empty = (TextView) findViewById(R.id.empty);
        input = (EditText) findViewById(R.id.input);
        ListView list = (ListView) findViewById(R.id.list);
        adapter = new WordListAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Word w = adapter.getItem(position);
                Intent intent = new Intent(LookupActivity.this, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_WORD_ID, w.id);
                startActivity(intent);
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refresh(String q) {
        List<Word> words = dao.searchPrefix(q, 30);
        adapter.setItems(words);
        boolean showEmpty = q.trim().length() > 0 && words.isEmpty();
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }
}
