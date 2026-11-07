package cn.kylebing.bbq10vocab;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WordUtil {
    private static final int GREEN = Color.parseColor("#7DCE82");
    private static final int RED = Color.parseColor("#EF9A9A");

    private WordUtil() {
    }

    /**
     * Show each sense on its own line. Old books joined senses with " / "
     * (looks like "/ n."), and some sources store a literal backslash-n.
     */
    public static String formatTrans(String trans) {
        if (trans == null || trans.length() == 0) {
            return "";
        }
        String s = trans;
        if (s.indexOf('\\') >= 0) {
            s = s.replace("\\n", "\n").replace("\\t", "\t");
        }
        // "v. get / n. noun" → line break before next part-of-speech
        s = s.replaceAll("\\s+/\\s+(?=[A-Za-z]+\\.)", "\n");
        return s.trim();
    }

    public static String blankSentence(String sentence, String word) {
        if (sentence == null || sentence.length() == 0) {
            return "";
        }
        if (word == null || word.length() == 0) {
            return sentence;
        }
        // Spaced blanks + ZWSP so a long blank at line end can wrap instead of clipping.
        String blanks = " " + spacedBlank(Math.max(word.length(), 3)) + " ";
        Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
        Matcher m = p.matcher(sentence);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(blanks));
        }
        // fallback: case-insensitive replace without word boundary
        int idx = sentence.toLowerCase(Locale.US).indexOf(word.toLowerCase(Locale.US));
        if (idx >= 0) {
            return sentence.substring(0, idx) + blanks + sentence.substring(idx + word.length());
        }
        return sentence;
    }

    private static String spacedBlank(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('\u200B'); // allow wrap mid-blank
            }
            sb.append('_');
        }
        return sb.toString();
    }

    public static CharSequence colorTyped(String typed, String answer, boolean enabled) {
        if (typed == null) {
            typed = "";
        }
        // No live colors: plain text avoids per-char spans on every keystroke.
        if (!enabled) {
            return typed;
        }
        if (answer == null) {
            answer = "";
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(typed);
        String a = answer.toLowerCase(Locale.US);
        int n = typed.length();
        for (int i = 0; i < n; i++) {
            char ch = typed.charAt(i);
            int color;
            if (i < a.length() && Character.toLowerCase(ch) == a.charAt(i)) {
                color = GREEN;
            } else {
                color = RED;
            }
            sb.setSpan(new ForegroundColorSpan(color), i, i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

}
