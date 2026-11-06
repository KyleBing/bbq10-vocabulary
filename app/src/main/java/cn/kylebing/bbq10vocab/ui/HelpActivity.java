package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.R;

public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        TextView body = (TextView) findViewById(R.id.helpBody);
        body.setText(formatHelp(getString(R.string.help_body)));
    }

    /**
     * Markup: [k]空格[/k] = key/shortcut chip, [f]查看结果[/f] = feature/UI label.
     */
    static CharSequence formatHelp(String raw) {
        if (raw == null) {
            return "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int kOpen = raw.indexOf("[k]", i);
            int fOpen = raw.indexOf("[f]", i);
            int open;
            boolean isKey;
            if (kOpen < 0 && fOpen < 0) {
                out.append(raw.substring(i));
                break;
            }
            if (kOpen < 0 || (fOpen >= 0 && fOpen < kOpen)) {
                open = fOpen;
                isKey = false;
            } else {
                open = kOpen;
                isKey = true;
            }
            if (open > i) {
                out.append(raw.substring(i, open));
            }
            String closeTag = isKey ? "[/k]" : "[/f]";
            int close = raw.indexOf(closeTag, open + 3);
            if (close < 0) {
                out.append(raw.substring(open));
                break;
            }
            String inner = raw.substring(open + 3, close);
            int start = out.length();
            if (isKey) {
                out.append(' ').append(inner).append(' ');
            } else {
                out.append(inner);
            }
            int end = out.length();
            if (isKey) {
                out.setSpan(new BackgroundColorSpan(0xFF2A4A7A), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(0xFFFFFFFF), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.setSpan(new ForegroundColorSpan(0xFF7DCE82), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            i = close + closeTag.length();
        }
        return out;
    }
}
