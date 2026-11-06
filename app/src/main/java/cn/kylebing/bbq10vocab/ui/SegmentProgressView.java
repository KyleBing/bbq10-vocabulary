package cn.kylebing.bbq10vocab.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Horizontal stacked progress bar: each segment width ∝ count. */
public class SegmentProgressView extends View {
    private int[] counts = new int[0];
    private int[] colors = new int[0];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SegmentProgressView(Context context) {
        super(context);
        init();
    }

    public SegmentProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SegmentProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        track.setColor(0xFF2A2A2A);
        setMinimumHeight(dp(10));
    }

    public void setSegments(int[] counts, int[] colors) {
        this.counts = counts == null ? new int[0] : counts;
        this.colors = colors == null ? new int[0] : colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float r = h / 2f;
        canvas.drawRoundRect(0, 0, w, h, r, r, track);
        int total = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                total += counts[i];
            }
        }
        if (total <= 0 || w <= 0) {
            return;
        }
        float x = 0;
        for (int i = 0; i < counts.length && i < colors.length; i++) {
            if (counts[i] <= 0) {
                continue;
            }
            float seg = (counts[i] * (float) w) / total;
            // last visible segment eats remainder to avoid gap
            boolean last = true;
            for (int j = i + 1; j < counts.length; j++) {
                if (counts[j] > 0) {
                    last = false;
                    break;
                }
            }
            float right = last ? w : x + seg;
            paint.setColor(colors[i]);
            canvas.drawRect(x, 0, right, h, paint);
            x = right;
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}
