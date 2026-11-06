package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.WordTts;
import cn.kylebing.bbq10vocab.WordUtil;
import cn.kylebing.bbq10vocab.db.WordDao;

public class RecognizeActivity extends Activity {
    private WordDao dao;
    private WordTts tts;
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
    private Button btnSpeak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        VocabApp app = (VocabApp) getApplication();
        dao = app.dao();
        tts = app.tts();
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
        btnSpeak = (Button) findViewById(R.id.btnSpeak);

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
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speakCurrent();
            }
        });
        showNext();
    }

    private void speakCurrent() {
        if (current == null) {
            return;
        }
        tts.speak(this, current.word);
    }

    /** Q10: `$` (hardware often as unicode / shift-4). Also keep P. */
    static boolean isSpeakKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_P) {
            return true;
        }
        // API 18 jar may lack KEYCODE_DOLLAR; match char / shift+4.
        if (event != null) {
            if (event.getUnicodeChar() == '$') {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_4
                    && (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                return true;
            }
        }
        return false;
    }

    private void showNext() {
        current = dao.nextReview(lastId);
        revealed = false;
        if (current == null) {
            wordText.setText(getString(R.string.empty_queue));
            phonetic.setText("");
            stashBadge.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.GONE);
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
        btnSpeak.setVisibility(View.VISIBLE);
        trans.setText(WordUtil.formatTrans(current.trans));
        exampleEn.setText(current.exampleEn == null ? "" : current.exampleEn);
        exampleCn.setText(current.exampleCn == null ? "" : current.exampleCn);
        hideMeaning();
        markBar.setVisibility(View.VISIBLE);
        resultBar.setVisibility(View.GONE);
        refreshHeader();
        // Keep focus off ScrollView / buttons so Space is not swallowed on first press.
        sinkFocus(markBar);
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
        sinkFocus(resultBar);
    }

    /** Focus a bar that does not consume Space (unlike ScrollView / some BB10 buttons). */
    private void sinkFocus(View bar) {
        if (bar == null || bar.getVisibility() != View.VISIBLE) {
            return;
        }
        if (bar instanceof ViewGroup) {
            ((ViewGroup) bar).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        bar.setFocusable(true);
        bar.setFocusableInTouchMode(true);
        bar.requestFocus();
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

    /**
     * Intercept before focused children. ScrollView always eats Space for page-scroll
     * (even when it cannot scroll); BB10 may also activate the focused button on Space.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && handleShortcut(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleShortcut(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleShortcut(int keyCode, KeyEvent event) {
        if (current != null && isSpeakKey(keyCode, event)) {
            speakCurrent();
            return true;
        }
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
        return false;
    }
}
