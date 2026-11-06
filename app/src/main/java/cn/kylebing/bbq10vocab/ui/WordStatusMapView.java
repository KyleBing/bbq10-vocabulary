package cn.kylebing.bbq10vocab.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import cn.kylebing.bbq10vocab.db.DbHelper;

/**
 * One cell per word (≈1px logical), colored by learning status.
 * Cells scale up slightly so the mosaic stays visible on Q10.
 */
public class WordStatusMapView extends View {
    private byte[] cells = new byte[0];
    private final Paint paint = new Paint();
    private android.graphics.Bitmap cache;
    private int cols = 1;
    private int cacheW;
    private int cacheH;

    private static final int COL_UNSEEN = 0xFF2A2A2A;
    private static final int COL_UNKNOWN = 0xFFEF9A9A;
    private static final int COL_KNOWN = 0xFF7DCE82;
    private static final int COL_STASH = 0xFFFFCC80;

    public WordStatusMapView(Context context) {
        super(context);
    }

    public WordStatusMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WordStatusMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setCells(byte[] cells) {
        this.cells = cells == null ? new byte[0] : cells;
        recycleCache();
        requestLayout();
        invalidate();
    }

    private void recycleCache() {
        if (cache != null && !cache.isRecycled()) {
            cache.recycle();
        }
        cache = null;
        cacheW = 0;
        cacheH = 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w <= 0) {
            w = 320;
        }
        // ~1px per word; bump to 2px when needed so mosaic isn't a hairline on Q10.
        int cell = 1;
        if (cells.length > 0) {
            int rowsAt1 = (cells.length + w - 1) / w;
            if (rowsAt1 < 24) {
                cell = 2;
            }
            cols = Math.max(1, w / cell);
        } else {
            cols = w;
        }
        int rows = cells.length == 0 ? 12 : (cells.length + cols - 1) / cols;
        setMeasuredDimension(w, rows * Math.max(1, w / cols));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (cells.length == 0) {
            canvas.drawColor(COL_UNSEEN);
            return;
        }
        if (cache == null || cache.isRecycled() || cacheW != w || cacheH != h) {
            recycleCache();
            cache = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.RGB_565);
            cacheW = w;
            cacheH = h;
            Canvas off = new Canvas(cache);
            int cell = Math.max(1, w / cols);
            int drawCols = Math.max(1, w / cell);
            for (int i = 0; i < cells.length; i++) {
                int col = i % drawCols;
                int row = i / drawCols;
                paint.setColor(colorOf(cells[i]));
                int left = col * cell;
                int top = row * cell;
                off.drawRect(left, top, left + cell, top + cell, paint);
            }
        }
        canvas.drawBitmap(cache, 0, 0, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleCache();
        super.onDetachedFromWindow();
    }

    private static int colorOf(byte cell) {
        switch (cell) {
            case DbHelper.CELL_KNOWN:
                return COL_KNOWN;
            case DbHelper.CELL_UNKNOWN:
                return COL_UNKNOWN;
            case DbHelper.CELL_STASH:
                return COL_STASH;
            case DbHelper.CELL_UNSEEN:
            default:
                return COL_UNSEEN;
        }
    }
}
