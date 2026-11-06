package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.WordTts;
import cn.kylebing.bbq10vocab.WordUtil;
import cn.kylebing.bbq10vocab.db.WordDao;

public class DetailActivity extends Activity {
    public static final String EXTRA_WORD_ID = "word_id";

    private WordDao dao;
    private WordTts tts;
    private Word current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        VocabApp app = (VocabApp) getApplication();
        dao = app.dao();
        tts = app.tts();
        long id = getIntent().getLongExtra(EXTRA_WORD_ID, -1);
        current = dao.getById(id);
        if (current == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.wordText)).setText(current.word);
        ((TextView) findViewById(R.id.phonetic)).setText(current.phonetic());
        TextView badge = (TextView) findViewById(R.id.stashBadge);
        badge.setVisibility(current.isStashed() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.trans)).setText(WordUtil.formatTrans(current.trans));
        TextView exampleEn = (TextView) findViewById(R.id.exampleEn);
        TextView exampleCn = (TextView) findViewById(R.id.exampleCn);
        exampleEn.setText(current.exampleEn);
        exampleCn.setText(current.exampleCn);
        if (current.exampleEn == null || current.exampleEn.length() == 0) {
            exampleEn.setVisibility(View.GONE);
            exampleCn.setVisibility(View.GONE);
        }
        findViewById(R.id.btnSpeak).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tts.speak(DetailActivity.this, current.word);
            }
        });
        findViewById(R.id.btnUnknown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dao.mark(current.id, false);
                finish();
            }
        });
        findViewById(R.id.btnStash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dao.markStash(current.id);
                finish();
            }
        });
        findViewById(R.id.btnKnow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dao.mark(current.id, true);
                finish();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (current != null && RecognizeActivity.isSpeakKey(keyCode, event)) {
            tts.speak(this, current.word);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
