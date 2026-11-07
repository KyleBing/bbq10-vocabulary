package cn.kylebing.bbq10vocab.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.kylebing.bbq10vocab.ProgressStats;
import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.VocabApp;
import cn.kylebing.bbq10vocab.Word;
import cn.kylebing.bbq10vocab.db.WordDao;

/**
 * 快速筛选：两列随机密铺未学词；点＝会（变绿），再点＝取消回未学。
 * 只改当前格子，不 notifyDataSetChanged；写库延后一帧。
 */
public class FilterActivity extends Activity {
    private static final int PAGE = 90;
    private static final int LOW_WATER = 24;
    private static final int COL_PENDING = 0xFFFFFFFF;
    private static final int COL_DONE = 0xFF7DCE82;

    private WordDao dao;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String bookTitleCached;
    private int knownCached;
    private int unseenCached;

    private TextView header;
    private GridView grid;
    private TextView empty;
    private FilterAdapter adapter;

    private boolean loading;
    private boolean exhausted;
    private int sessionMarked;
    private int pendingCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter);
        dao = ((VocabApp) getApplication()).dao();
        header = (TextView) findViewById(R.id.header);
        grid = (GridView) findViewById(R.id.grid);
        empty = (TextView) findViewById(R.id.empty);
        adapter = new FilterAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                toggleAt(position, view);
            }
        });
        grid.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                if (!exhausted && !loading
                        && totalItemCount > 0
                        && firstVisibleItem + visibleItemCount >= totalItemCount - 12) {
                    loadMore();
                }
            }
        });
        refreshHeaderFromDb();
        loadMore();
    }

    private void toggleAt(int position, View cell) {
        Word w = adapter.getItem(position);
        if (w == null) {
            return;
        }
        final long wordId = w.id;
        final boolean nowKnown = !adapter.isMarked(position);

        // 1) paint first — no full grid rebind
        if (nowKnown) {
            adapter.markAt(position, true);
            pendingCount--;
            sessionMarked++;
            if (unseenCached > 0) {
                unseenCached--;
            }
            knownCached++;
            applyCellStyle(position, cell, true);
            if (pendingCount < LOW_WATER) {
                loadMore();
            }
        } else {
            adapter.markAt(position, false);
            pendingCount++;
            if (sessionMarked > 0) {
                sessionMarked--;
            }
            unseenCached++;
            if (knownCached > 0) {
                knownCached--;
            }
            applyCellStyle(position, cell, false);
        }
        paintHeader();

        // 2) persist desired state after this frame (survives rapid re-taps)
        ui.post(new Runnable() {
            @Override
            public void run() {
                Boolean want = adapter.wantKnown(wordId);
                if (want == null) {
                    return;
                }
                if (want.booleanValue()) {
                    dao.markFilteredKnown(wordId);
                } else {
                    dao.clearFilteredKnown(wordId);
                }
            }
        });
    }

    private void applyCellStyle(int position, View cell, boolean done) {
        TextView tv = null;
        if (cell instanceof TextView) {
            tv = (TextView) cell;
        } else {
            int first = grid.getFirstVisiblePosition();
            View child = grid.getChildAt(position - first);
            if (child instanceof TextView) {
                tv = (TextView) child;
            }
        }
        if (tv == null) {
            return;
        }
        if (done) {
            styleDone(tv);
        } else {
            stylePending(tv);
        }
    }

    private static void styleDone(TextView v) {
        v.setTextColor(COL_DONE);
        v.setBackgroundResource(R.drawable.row_filter_done_bg);
    }

    private static void stylePending(TextView v) {
        v.setTextColor(COL_PENDING);
        v.setBackgroundResource(R.drawable.row_filter_bg);
    }

    private void loadMore() {
        if (loading || exhausted) {
            return;
        }
        loading = true;
        List<Word> more = dao.listUnseenRandom(PAGE, adapter.ids());
        loading = false;
        if (more.isEmpty()) {
            exhausted = true;
            maybeShowEmpty();
            return;
        }
        adapter.append(more);
        pendingCount += more.size();
        maybeShowEmpty();
    }

    private void maybeShowEmpty() {
        boolean noItems = adapter.getCount() == 0;
        empty.setVisibility(noItems ? View.VISIBLE : View.GONE);
        grid.setVisibility(noItems ? View.GONE : View.VISIBLE);
        if (noItems && exhausted) {
            empty.setText(R.string.filter_empty);
        }
    }

    private void refreshHeaderFromDb() {
        if (bookTitleCached == null) {
            bookTitleCached = dao.bookTitle();
        }
        ProgressStats s = dao.stats();
        knownCached = s.known;
        unseenCached = s.unseen;
        paintHeader();
    }

    private void paintHeader() {
        header.setText(bookTitleCached
                + "  本轮筛掉 " + sessionMarked
                + "  未学 " + unseenCached
                + "  会 " + knownCached);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (adapter.getCount() == 0) {
            return super.onKeyDown(keyCode, event);
        }
        int pos = grid.getSelectedItemPosition();
        if (pos < 0) {
            pos = grid.getFirstVisiblePosition();
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_Y) {
            if (pos >= 0 && pos < adapter.getCount()) {
                int first = grid.getFirstVisiblePosition();
                View cell = grid.getChildAt(pos - first);
                toggleAt(pos, cell);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private static final class FilterAdapter extends BaseAdapter {
        private final List<Word> items = new ArrayList<Word>();
        /** Compact flags; avoid Boolean boxing churn on bind. */
        private boolean[] marked = new boolean[0];

        List<Long> ids() {
            List<Long> out = new ArrayList<Long>(items.size());
            for (int i = 0; i < items.size(); i++) {
                out.add(Long.valueOf(items.get(i).id));
            }
            return out;
        }

        void append(List<Word> more) {
            if (more == null || more.isEmpty()) {
                return;
            }
            int old = items.size();
            items.addAll(more);
            boolean[] next = new boolean[items.size()];
            if (old > 0) {
                System.arraycopy(marked, 0, next, 0, old);
            }
            marked = next;
            notifyDataSetChanged();
        }

        boolean isMarked(int index) {
            return index >= 0 && index < marked.length && marked[index];
        }

        /** Data only — caller paints the visible cell. No notify. */
        void markAt(int index, boolean done) {
            if (index >= 0 && index < marked.length) {
                marked[index] = done;
            }
        }

        /** Current desired known flag for a word still on screen; null if gone. */
        Boolean wantKnown(long wordId) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id == wordId) {
                    return Boolean.valueOf(marked[i]);
                }
            }
            return null;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Word getItem(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            Word w = getItem(position);
            return w == null ? 0 : w.id;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v;
            if (convertView instanceof TextView) {
                v = (TextView) convertView;
            } else {
                v = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.row_filter, parent, false);
            }
            v.setText(items.get(position).word);
            if (marked[position]) {
                styleDone(v);
            } else {
                stylePending(v);
            }
            return v;
        }
    }
}
