package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.WordUtil;
import cn.kylebing.bbq10vocab.db.WordDao;

public class RecognizeActivity extends Activity {
    private WordDao dao;
    private Word current;
    private long lastId = -1;
    private boolean revealed;

    private TextView header;
    private TextView wordText;
    private TextView phonetic;
    private TextView stashBadge;
    private TextView trans;
    private TextView exampleEn;
    private TextView exampleCn;
    private View markBar;
    private View resultBar;
    private Button btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        dao = ((VocabApp) getApplication()).dao();
        header = (TextView) findViewById(R.id.header);
        wordText = (TextView) findViewById(R.id.wordText);
        phonetic = (TextView) findViewById(R.id.phonetic);
        stashBadge = (TextView) findViewById(R.id.stashBadge);
        trans = (TextView) findViewById(R.id.trans);
        exampleEn = (TextView) findViewById(R.id.exampleEn);
        exampleCn = (TextView) findViewById(R.id.exampleCn);
        markBar = findViewById(R.id.markBar);
        resultBar = findViewById(R.id.resultBar);
        btnNext = (Button) findViewById(R.id.btnNext);

        findViewById(R.id.btnUnknown).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markUnknown();
            }
        });
        findViewById(R.id.btnKnow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markKnown();
            }
        });
        findViewById(R.id.btnStash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStashFromResult();
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNext();
            }
        });
        showNext();
    }

    private void showNext() {
        current = dao.nextReview(lastId);
        revealed = false;
        if (current == null) {
            wordText.setText(getString(R.string.empty_queue));
            phonetic.setText("");
            stashBadge.setVisibility(View.GONE);
            hideMeaning();
            markBar.setVisibility(View.GONE);
            resultBar.setVisibility(View.GONE);
            refreshHeader();
            return;
        }
        lastId = current.id;
        wordText.setText(current.word);
        phonetic.setText(current.phonetic());
        stashBadge.setVisibility(current.isStashed() ? View.VISIBLE : View.GONE);
        trans.setText(WordUtil.formatTrans(current.trans));
        exampleEn.setText(current.exampleEn == null ? "" : current.exampleEn);
        exampleCn.setText(current.exampleCn == null ? "" : current.exampleCn);
        hideMeaning();
        markBar.setVisibility(View.VISIBLE);
        resultBar.setVisibility(View.GONE);
        refreshHeader();
    }

    private void hideMeaning() {
        trans.setVisibility(View.INVISIBLE);
        exampleEn.setVisibility(View.INVISIBLE);
        exampleCn.setVisibility(View.INVISIBLE);
    }

    private void reveal() {
        trans.setVisibility(View.VISIBLE);
        if (current != null && current.exampleEn != null && current.exampleEn.length() > 0) {
            exampleEn.setVisibility(View.VISIBLE);
        }
        if (current != null && current.exampleCn != null && current.exampleCn.length() > 0) {
            exampleCn.setVisibility(View.VISIBLE);
        }
        markBar.setVisibility(View.GONE);
        resultBar.setVisibility(View.VISIBLE);
        revealed = true;
        if (current != null) {
            stashBadge.setVisibility(current.isStashed() ? View.VISIBLE : View.GONE);
        }
    }

    private void markKnown() {
        if (current == null || revealed) {
            return;
        }
        lastId = current.id;
        dao.mark(current.id, true);
        current.flag = 0;
        reveal();
        refreshHeader();
    }

    private void markUnknown() {
        if (current == null || revealed) {
            return;
        }
        lastId = current.id;
        dao.mark(current.id, false);
        reveal();
        refreshHeader();
    }

    /** 出结果后再暂存，不立刻跳下一词。 */
    private void onStashFromResult() {
        if (current == null || !revealed) {
            return;
        }
        dao.markStash(current.id);
        current.flag = 1;
        stashBadge.setVisibility(View.VISIBLE);
        refreshHeader();
    }

    private void refreshHeader() {
        ProgressStats s = dao.stats();
        header.setText(dao.bookTitle() + "  会" + s.known + " 不会" + s.unknown
                + " 暂存" + s.stashed + " 到期" + s.due);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (current != null && !revealed) {
            if (keyCode == KeyEvent.KEYCODE_SPACE
                    || keyCode == KeyEvent.KEYCODE_Y
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                markKnown();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_B
                    || keyCode == KeyEvent.KEYCODE_N
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                markUnknown();
                return true;
            }
        } else if (revealed) {
            if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_M) {
                onStashFromResult();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_SPACE
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                showNext();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
