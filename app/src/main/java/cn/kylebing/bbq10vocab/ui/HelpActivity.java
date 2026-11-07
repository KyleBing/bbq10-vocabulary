package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import cn.kylebing.bbq10vocab.R;

public class HelpActivity extends Activity {
    private static final int COL_HEAD = 0xFFFFFFFF;
    private static final int COL_KEY_BG = 0xFF2A4A7A;
    private static final int COL_KEY_FG = 0xFFFFFFFF;
    private static final int COL_FEAT = 0xFF7DCE82;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        TextView body = (TextView) findViewById(R.id.helpBody);
        body.setText(formatHelp(getString(R.string.help_body)));
    }

    /**
     * Markup:
     * [h]识词[/h] section heading
     * [k]空格[/k] key/shortcut chip
     * [f]查看结果[/f] feature/UI label
     */
    static CharSequence formatHelp(String raw) {
        if (raw == null) {
            return "";
        }
        SpannableStringBuilder out = new SpannableStringBuilder();
        int i = 0;
        while (i < raw.length()) {
            TagOpen next = nextTag(raw, i);
            if (next == null) {
                out.append(raw.substring(i));
                break;
            }
            if (next.open > i) {
                out.append(raw.substring(i, next.open));
            }
            String closeTag = "[/" + next.name + "]";
            int close = raw.indexOf(closeTag, next.contentStart);
            if (close < 0) {
                out.append(raw.substring(next.open));
                break;
            }
            String inner = raw.substring(next.contentStart, close);
            int start = out.length();
            if ("k".equals(next.name)) {
                // Tight chip: thin spaces so background doesn't collide across lines.
                out.append('\u2009').append(inner).append('\u2009');
            } else {
                out.append(inner);
            }
            int end = out.length();
            applySpan(out, next.name, start, end);
            i = close + closeTag.length();
        }
        return out;
    }

    private static void applySpan(SpannableStringBuilder out, String name, int start, int end) {
        if ("h".equals(name)) {
            out.setSpan(new AbsoluteSizeSpan(17, true), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(COL_HEAD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("k".equals(name)) {
            out.setSpan(new BackgroundColorSpan(COL_KEY_BG), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(COL_KEY_FG), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new AbsoluteSizeSpan(12, true), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("f".equals(name)) {
            out.setSpan(new ForegroundColorSpan(COL_FEAT), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static TagOpen nextTag(String raw, int from) {
        int best = -1;
        String bestName = null;
        String[] names = new String[]{"h", "k", "f"};
        for (int n = 0; n < names.length; n++) {
            String open = "[" + names[n] + "]";
            int at = raw.indexOf(open, from);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
                bestName = names[n];
            }
        }
        if (best < 0) {
            return null;
        }
        TagOpen t = new TagOpen();
        t.open = best;
        t.name = bestName;
        t.contentStart = best + 2 + bestName.length(); // [ + name + ]
        return t;
    }

    private static final class TagOpen {
        int open;
        int contentStart;
        String name;
    }
}
