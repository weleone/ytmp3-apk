package com.weleone.ytmp3;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** 已下载文件列表的 Adapter */
public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.VH> {

    public static class Item {
        long id;
        String name;
        long size;
        Uri uri;
    }

    private final List<Item> items = new ArrayList<>();
    private final Context ctx;

    public FileListAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setItems(List<Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public Item getItem(int pos) {
        return items.get(pos);
    }

    public void removeAt(int pos) {
        if (pos >= 0 && pos < items.size()) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        VH(TextView v) {
            super(v);
            tv = v;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(ctx);
        tv.setTextColor(ThemeHelper.getColor(ctx, R.attr.appTextPrimary));
        tv.setTextSize(14);
        tv.setPadding(4, 16, 4, 16);
        tv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Item it = items.get(pos);
        h.tv.setText(it.name + "  (" + formatSize(it.size) + ")");
        h.tv.setOnClickListener(v -> DownloadListHelper.openFile(ctx, it.uri, it.name));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static String formatSize(long size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.1fKB", size / 1024f);
        return String.format("%.1fMB", size / 1024f / 1024f);
    }
}
