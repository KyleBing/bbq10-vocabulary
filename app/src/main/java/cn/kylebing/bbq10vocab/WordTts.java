package cn.kylebing.bbq10vocab;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Android 4.3 / BB10 Runtime TTS probe + speak.
 * Uses the pre-API-21 {@link TextToSpeech#speak(String, int, java.util.HashMap)} signature.
 */
public final class WordTts implements TextToSpeech.OnInitListener {
    private final Context app;
    private TextToSpeech tts;
    private boolean ready;
    private boolean initFailed;
    private String status = "TTS 初始化中…";
    private String pending;

    public WordTts(Context context) {
        app = context.getApplicationContext();
        try {
            tts = new TextToSpeech(app, this);
        } catch (Throwable t) {
            initFailed = true;
            status = "TTS 创建失败：" + msg(t);
        }
    }

    @Override
    public void onInit(int resultCode) {
        if (tts == null) {
            return;
        }
        if (resultCode != TextToSpeech.SUCCESS) {
            initFailed = true;
            status = "TTS onInit 失败 code=" + resultCode + engineSummary();
            return;
        }

        int lang = tryLanguage();
        if (lang == TextToSpeech.LANG_MISSING_DATA
                || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            initFailed = true;
            status = "无可用英语语音（lang=" + langName(lang) + "）" + engineSummary();
            return;
        }

        ready = true;
        status = "TTS OK eng=" + safeEngine() + " lang=" + langName(lang) + engineSummary();
        if (pending != null) {
            String w = pending;
            pending = null;
            doSpeak(w);
        }
    }

    /** Speak English word; only toast on real failure (no engine banner). */
    public void speak(Context ui, String word) {
        if (word == null) {
            return;
        }
        word = word.trim();
        if (word.length() == 0) {
            return;
        }
        if (initFailed) {
            toast(ui, "朗读不可用");
            return;
        }
        if (!ready) {
            pending = word;
            return;
        }
        if (!doSpeak(word)) {
            toast(ui, "朗读失败");
        }
    }

    public String status() {
        return status;
    }

    public void shutdown() {
        pending = null;
        ready = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
    }

    private boolean doSpeak(String word) {
        try {
            // API 18 signature
            int r = tts.speak(word, TextToSpeech.QUEUE_FLUSH, null);
            return r == TextToSpeech.SUCCESS;
        } catch (Throwable t) {
            status = "speak 异常：" + msg(t) + engineSummary();
            return false;
        }
    }

    private int tryLanguage() {
        int r = tts.setLanguage(Locale.US);
        if (okLang(r)) {
            return r;
        }
        r = tts.setLanguage(Locale.UK);
        if (okLang(r)) {
            return r;
        }
        return tts.setLanguage(Locale.ENGLISH);
    }

    private static boolean okLang(int r) {
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private String safeEngine() {
        try {
            String e = tts.getDefaultEngine();
            return e == null ? "?" : e;
        } catch (Throwable t) {
            return "?";
        }
    }

    private String engineSummary() {
        try {
            List<EngineInfo> engines = tts.getEngines();
            if (engines == null || engines.isEmpty()) {
                return "\n引擎：无";
            }
            StringBuilder sb = new StringBuilder("\n引擎：");
            for (int i = 0; i < engines.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                EngineInfo e = engines.get(i);
                sb.append(e.label != null ? e.label : e.name);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "\n引擎列表不可用";
        }
    }

    private static String langName(int r) {
        switch (r) {
            case TextToSpeech.LANG_AVAILABLE:
                return "AVAILABLE";
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                return "COUNTRY_AVAILABLE";
            case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                return "COUNTRY_VAR_AVAILABLE";
            case TextToSpeech.LANG_MISSING_DATA:
                return "MISSING_DATA";
            case TextToSpeech.LANG_NOT_SUPPORTED:
                return "NOT_SUPPORTED";
            default:
                return String.valueOf(r);
        }
    }

    private static void toast(Context ui, String msg) {
        Toast.makeText(ui, msg, Toast.LENGTH_LONG).show();
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
