package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.Prefs;
import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.WordTts;
import cn.kylebing.bbq10vocab.WordUtil;
import cn.kylebing.bbq10vocab.db.WordDao;

public class SpellActivity extends Activity {
    private WordDao dao;
    private WordTts tts;
    private Word current;
    private long lastId = -1;
    private boolean waitingNext;
    private boolean liveOn;
    private boolean imeOn;

    private TextView header;
    private TextView prompt;
    private TextView blankSentence;
    private TextView blankCn;
    private TextView typedPreview;
    private EditText input;
    private TextView result;
    private TextView answer;
    private Button btnSubmit;
    private View resultBar;
    private CheckBox chkLive;
    private CheckBox chkIme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spell);
        VocabApp app = (VocabApp) getApplication();
        dao = app.dao();
        tts = app.tts();
        header = (TextView) findViewById(R.id.header);
        prompt = (TextView) findViewById(R.id.prompt);
        blankSentence = (TextView) findViewById(R.id.blankSentence);
        blankCn = (TextView) findViewById(R.id.blankCn);
        typedPreview = (TextView) findViewById(R.id.typedPreview);
        input = (EditText) findViewById(R.id.input);
        result = (TextView) findViewById(R.id.result);
        answer = (TextView) findViewById(R.id.answer);
        btnSubmit = (Button) findViewById(R.id.btnSubmit);
        resultBar = findViewById(R.id.resultBar);
        chkLive = (CheckBox) findViewById(R.id.chkLive);
        chkIme = (CheckBox) findViewById(R.id.chkIme);

        liveOn = Prefs.liveSpellFeedback(this);
        imeOn = Prefs.imeSuggest(this);
        chkLive.setChecked(liveOn);
        chkIme.setChecked(imeOn);
        chkLive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                liveOn = isChecked;
                Prefs.setLiveSpellFeedback(SpellActivity.this, isChecked);
                updateTypedPreview();
            }
        });
        chkIme.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                imeOn = isChecked;
                Prefs.setImeSuggest(SpellActivity.this, isChecked);
                applyImeMode(true);
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                keepInputVisible();
                updateTypedPreview();
                maybeAutoFinish();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // Some IMEs send IME_NULL / UNSPECIFIED on physical Enter.
                boolean enterKey = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_NEXT
                        || actionId == EditorInfo.IME_ACTION_SEND
                        || actionId == EditorInfo.IME_NULL
                        || enterKey) {
                    if (waitingNext) {
                        showNext();
                    } else {
                        onSubmit();
                    }
                    return true;
                }
                return false;
            }
        });
        input.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (RecognizeActivity.isSpeakKey(keyCode, event)) {
                    speakCurrent();
                    return true;
                }
                if (waitingNext) {
                    if (keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_SPACE
                            || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        showNext();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_M) {
                        onStashFromResult();
                        return true;
                    }
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                    onSubmit();
                    return true;
                }
                return false;
            }
        });
        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSubmit();
            }
        });
        findViewById(R.id.btnStash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStashFromResult();
            }
        });
        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNext();
            }
        });

        applyImeMode(false);
        showNext();
    }

    private void speakCurrent() {
        if (current == null) {
            return;
        }
        tts.speakWord(this, current, Prefs.speakExample(this));
    }

    private void maybeAutoSpeak(boolean afterResult) {
        if (!Prefs.autoSpeak(this) || current == null) {
            return;
        }
        int when = Prefs.speakWhen(this);
        if (afterResult && when == Prefs.SPEAK_AFTER_RESULT) {
            speakCurrent();
        } else if (!afterResult && when == Prefs.SPEAK_BEFORE_RESULT) {
            speakCurrent();
        }
    }

    private void applyImeMode(boolean refocus) {
        String keep = input.getText() == null ? "" : input.getText().toString();
        int sel = input.getSelectionEnd();
        if (imeOn) {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }
        input.setSingleLine(true);
        input.setText(keep);
        if (sel >= 0 && sel <= keep.length()) {
            input.setSelection(sel);
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        if (imeOn) {
            if (refocus) {
                input.requestFocus();
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void syncKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return;
        }
        if (imeOn) {
            input.requestFocus();
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    /** Keep centered; still pin caret to end so long words stay editable. */
    private void keepInputVisible() {
        Editable text = input.getText();
        if (text == null) {
            return;
        }
        final int len = text.length();
        input.setGravity(Gravity.CENTER);
        input.setSelection(len);
        input.post(new Runnable() {
            @Override
            public void run() {
                int l = input.length();
                if (l > 0) {
                    input.setSelection(l);
                }
            }
        });
    }

    private void updateTypedPreview() {
        if (current == null || waitingNext) {
            return;
        }
        typedPreview.setText(WordUtil.colorTyped(input.getText().toString(), current.word, liveOn));
    }

    private boolean isComposing() {
        CharSequence t = input.getText();
        if (!(t instanceof Spannable)) {
            return false;
        }
        Spannable sp = (Spannable) t;
        Object[] spans = sp.getSpans(0, sp.length(), Object.class);
        for (int i = 0; i < spans.length; i++) {
            if ((sp.getSpanFlags(spans[i]) & Spanned.SPAN_COMPOSING) != 0) {
                return true;
            }
        }
        return false;
    }

    private void maybeAutoFinish() {
        // While IME composing / candidate pending, don't auto-submit.
        if (current == null || waitingNext || isComposing()) {
            return;
        }
        String typed = input.getText().toString().trim();
        if (typed.length() > 0 && typed.equalsIgnoreCase(current.word)) {
            finishAttempt(true);
        }
    }

    private void showNext() {
        waitingNext = false;
        current = dao.nextReview(lastId);
        result.setVisibility(View.GONE);
        answer.setVisibility(View.GONE);
        input.setEnabled(true);
        input.setVisibility(View.VISIBLE);
        input.setGravity(Gravity.CENTER);
        input.setText("");
        typedPreview.setText("");
        btnSubmit.setText(R.string.btn_view_result);
        btnSubmit.setVisibility(View.VISIBLE);
        resultBar.setVisibility(View.GONE);
        if (current == null) {
            prompt.setText(R.string.empty_queue);
            blankSentence.setText("");
            blankCn.setText("");
            input.setVisibility(View.GONE);
            typedPreview.setVisibility(View.GONE);
            btnSubmit.setVisibility(View.GONE);
            resultBar.setVisibility(View.GONE);
            refreshHeader();
            return;
        }
        lastId = current.id;
        typedPreview.setVisibility(View.VISIBLE);
        btnSubmit.setVisibility(View.VISIBLE);
        prompt.setText(WordUtil.formatTrans(current.trans));
        String blank = WordUtil.blankSentence(current.exampleEn, current.word);
        blankSentence.setText(blank);
        blankCn.setText(current.exampleCn == null ? "" : current.exampleCn);
        refreshHeader();
        input.requestFocus();
        syncKeyboard();
        maybeAutoSpeak(false);
    }

    private void onSubmit() {
        if (current == null) {
            return;
        }
        // Result already shown: Enter = next (same as Space / 下一词).
        if (waitingNext) {
            showNext();
            return;
        }
        // Enter / 查看结果: accept current text even if IME still composing.
        String typed = input.getText().toString().trim();
        boolean ok = typed.equalsIgnoreCase(current.word);
        finishAttempt(ok);
    }

    private void finishAttempt(boolean ok) {
        if (current == null || waitingNext) {
            return;
        }
        lastId = current.id;
        dao.mark(current.id, ok);
        showAnswer(ok);
        refreshHeader();
    }

    private void onStashFromResult() {
        if (current == null || !waitingNext) {
            return;
        }
        dao.markStash(current.id);
        current.flag = 1;
        showNext();
    }

    private void showAnswer(boolean ok) {
        waitingNext = true;
        input.setEnabled(false);
        input.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.GONE);
        resultBar.setVisibility(View.VISIBLE);
        resultBar.setFocusable(true);
        resultBar.setFocusableInTouchMode(true);
        input.clearFocus();
        resultBar.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        String typed = input.getText().toString().trim();
        typedPreview.setText(WordUtil.colorTyped(typed, current.word, true));
        result.setVisibility(View.VISIBLE);
        if (ok) {
            result.setText(R.string.spell_ok);
            result.setTextColor(getResources().getColor(R.color.accent));
            answer.setText("");
            answer.setVisibility(View.GONE);
        } else {
            result.setText(R.string.spell_bad);
            result.setTextColor(0xFFEF9A9A);
            answer.setText(current.word);
            answer.setVisibility(View.VISIBLE);
        }
        maybeAutoSpeak(true);
    }

    private void refreshHeader() {
        ProgressStats s = dao.stats();
        header.setText(dao.bookTitle() + "  " + s.reviewLine());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (current != null && RecognizeActivity.isSpeakKey(keyCode, event)) {
            speakCurrent();
            return true;
        }
        if (waitingNext) {
            if (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_M) {
                onStashFromResult();
                return true;
            }
            // 空格 / Enter = 下一词
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_SPACE
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                showNext();
                return true;
            }
        }
        if (!waitingNext && current != null && input.getVisibility() == View.VISIBLE
                && (keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            onSubmit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
