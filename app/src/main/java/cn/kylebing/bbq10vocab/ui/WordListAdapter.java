package cn.kylebing.bbq10vocab.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.kylebing.bbq10vocab.R;
import cn.kylebing.bbq10vocab.Word;

public class WordListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Word> items = new ArrayList<Word>();

    public WordListAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Word> words) {
        items.clear();
        if (words != null) {
            items.addAll(words);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Word getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView v;
        if (convertView instanceof TextView) {
            v = (TextView) convertView;
        } else {
            v = (TextView) inflater.inflate(R.layout.row_word, parent, false);
        }
        Word w = items.get(position);
        if (w.isStashed()) {
            v.setText("[暂存] " + w.word);
            v.setTextColor(0xFFFFCC80);
        } else {
            v.setText(w.word);
            v.setTextColor(0xFFFFFFFF);
        }
        return v;
    }
}
