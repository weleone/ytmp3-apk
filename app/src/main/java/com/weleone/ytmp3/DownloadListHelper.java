package com.weleone.ytmp3;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** 公共 Download/YTMP3 文件列表：本地 tab 和服务器 tab 共用（RecyclerView + 左滑删除） */
public class DownloadListHelper {

    /** 初始化列表（含左滑删除），在 onCreateView 里调用一次 */
    public static void setup(Context ctx, RecyclerView rv) {
        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setNestedScrollingEnabled(false);
        FileListAdapter adapter = new FileListAdapter(ctx);
        rv.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final Paint paint = new Paint();
            {
                paint.setColor(0xFFD32F2F);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView r,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos < 0) return;
                FileListAdapter.Item item = adapter.getItem(pos);
                new AlertDialog.Builder(ctx)
                        .setMessage("删除 " + item.name + "？")
                        .setPositiveButton("删除", (d, w) -> {
                            try {
                                ctx.getContentResolver().delete(item.uri, null, null);
                            } catch (Exception ignored) {
                            }
                            adapter.removeAt(pos);
                        })
                        .setNegativeButton("取消", (d, w) -> adapter.notifyItemChanged(pos))
                        .setOnCancelListener(d -> adapter.notifyItemChanged(pos))
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState, boolean isActive) {
                // 左滑时画红色删除背景
                if (dX < 0) {
                    View v = vh.itemView;
                    c.drawRect(v.getRight() + dX, v.getTop(),
                            v.getRight(), v.getBottom(), paint);
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive);
            }
        }).attachToRecyclerView(rv);
    }

    /** 刷新列表数据（需在主线程调用）；空列表时显示 emptyView；返回文件数 */
    public static int refresh(Context ctx, RecyclerView rv, TextView emptyView) {
        if (ctx == null || rv == null
                || !(rv.getAdapter() instanceof FileListAdapter)) return -1;
        FileListAdapter adapter = (FileListAdapter) rv.getAdapter();

        List<FileListAdapter.Item> list = new ArrayList<>();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
        };
        String sel = MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
        String[] args = new String[]{"%YTMP3%"};
        String sort = MediaStore.Downloads.DATE_MODIFIED + " DESC";
        try (Cursor c = ctx.getContentResolver().query(collection, proj, sel, args, sort)) {
            if (c != null) {
                while (c.moveToNext()) {
                    FileListAdapter.Item it = new FileListAdapter.Item();
                    it.id = c.getLong(0);
                    it.name = c.getString(1);
                    it.size = c.getLong(2);
                    it.uri = ContentUris.withAppendedId(collection, it.id);
                    if (it.size <= 0) {
                        // MediaStore 的 SIZE 回填有延迟，直接读文件真实大小
                        try (ParcelFileDescriptor pfd = ctx.getContentResolver()
                                .openFileDescriptor(it.uri, "r")) {
                            if (pfd != null) {
                                long ss = pfd.getStatSize();
                                if (ss > 0) it.size = ss;
                            }
                        } catch (Exception ignored) {}
                    }
                    list.add(it);
                }
            }
        } catch (Exception ignored) {
        }
        adapter.setItems(list);
        if (emptyView != null) {
            emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        }
        return list.size();
    }

    /** 点击文件直接调用系统应用打开 */
    public static void openFile(Context ctx, Uri uri, String name) {
        String n = name.toLowerCase();
        String mime = "audio/mpeg";
        if (n.endsWith(".mp4")) mime = "video/mp4";
        else if (n.endsWith(".webm")) mime = "video/webm";
        else if (n.endsWith(".m4a")) mime = "audio/mp4";
        else if (n.endsWith(".wav")) mime = "audio/wav";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(ctx, "没有可打开此文件的应用", Toast.LENGTH_LONG).show();
        }
    }
}
