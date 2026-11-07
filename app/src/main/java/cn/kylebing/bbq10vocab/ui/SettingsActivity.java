package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import cn.kylebing.bbq10vocab.Prefs;
import cn.kylebing.bbq10vocab.R;

public class SettingsActivity extends Activity {
    private CheckBox chkAutoSpeak;
    private CheckBox chkSpeakExample;
    private RadioGroup speakWhenGroup;
    private RadioButton radioBefore;
    private RadioButton radioAfter;
    private View speakWhenLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        chkAutoSpeak = (CheckBox) findViewById(R.id.chkAutoSpeak);
        chkSpeakExample = (CheckBox) findViewById(R.id.chkSpeakExample);
        speakWhenGroup = (RadioGroup) findViewById(R.id.speakWhenGroup);
        radioBefore = (RadioButton) findViewById(R.id.radioSpeakBefore);
        radioAfter = (RadioButton) findViewById(R.id.radioSpeakAfter);
        speakWhenLabel = findViewById(R.id.speakWhenLabel);

        boolean auto = Prefs.autoSpeak(this);
        chkAutoSpeak.setChecked(auto);
        chkSpeakExample.setChecked(Prefs.speakExample(this));
        if (Prefs.speakWhen(this) == Prefs.SPEAK_AFTER_RESULT) {
            radioAfter.setChecked(true);
        } else {
            radioBefore.setChecked(true);
        }
        refreshSpeakWhenEnabled(auto);

        chkAutoSpeak.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAutoSpeak(SettingsActivity.this, isChecked);
                refreshSpeakWhenEnabled(isChecked);
            }
        });
        chkSpeakExample.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setSpeakExample(SettingsActivity.this, isChecked);
            }
        });
        speakWhenGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioSpeakAfter) {
                    Prefs.setSpeakWhen(SettingsActivity.this, Prefs.SPEAK_AFTER_RESULT);
                } else {
                    Prefs.setSpeakWhen(SettingsActivity.this, Prefs.SPEAK_BEFORE_RESULT);
                }
            }
        });
    }

    private void refreshSpeakWhenEnabled(boolean autoOn) {
        speakWhenLabel.setEnabled(autoOn);
        radioBefore.setEnabled(autoOn);
        radioAfter.setEnabled(autoOn);
        speakWhenGroup.setEnabled(autoOn);
        float alpha = autoOn ? 1f : 0.45f;
        speakWhenLabel.setAlpha(alpha);
        speakWhenGroup.setAlpha(alpha);
    }
}
