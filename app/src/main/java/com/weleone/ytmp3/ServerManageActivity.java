package com.weleone.ytmp3;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

public class ServerManageActivity extends AppCompatActivity {
    private List<ServerStore.Server> servers;
    private ServerAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Override
    protected void onCreate(Bundle s) {
        ThemeHelper.applyTheme(this);
        super.onCreate(s);
        setContentView(R.layout.activity_server_manage);
        servers = ServerStore.load(this);

        RecyclerView rv = findViewById(R.id.rv_servers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ServerAdapter();
        rv.setAdapter(adapter);

        // 上下拖动排序
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView r, RecyclerView.ViewHolder vh,
                                  RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(servers, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }
            @Override public void onSwiped(RecyclerView.ViewHolder vh, int d) {}
            @Override
            public void clearView(RecyclerView r, RecyclerView.ViewHolder vh) {
                super.clearView(r, vh);
                ServerStore.save(ServerManageActivity.this, servers);
            }
        });
        touchHelper.attachToRecyclerView(rv);

        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog(-1));
    }

    private void showAddDialog(int index) {
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);
        EditText etName = dv.findViewById(R.id.et_name);
        EditText etUrl = dv.findViewById(R.id.et_url);
        if (index >= 0) {
            etName.setText(servers.get(index).name);
            etUrl.setText(servers.get(index).url);
        }
        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑服务器" : "添加服务器")
                .setView(dv)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String url = ServerStore.normalizeUrl(etUrl.getText().toString());
                    if (url.isEmpty() || url.equals("http://")) {
                        Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (name.isEmpty()) name = url;
                    boolean isAdd = index < 0;
                    if (index >= 0) {
                        ServerStore.Server old = servers.get(index);
                        servers.set(index, new ServerStore.Server(name, url));
                        // 编辑的是当前选中的服务器，同步更新选中地址
                        if (old.url.equals(ServerStore.getCurrentUrl(this))) {
                            ServerStore.setCurrentUrl(this, url);
                        }
                    } else {
                        servers.add(new ServerStore.Server(name, url));
                    }
                    ServerStore.save(this, servers);
                    if (isAdd) {
                        // 新加的服务器直接设为当前选中，并返回服务器页面
                        ServerStore.setCurrentUrl(this, url);
                        finish();
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            RadioButton rbSelect;
            TextView tvName, tvUrl, tvDrag;
            Button btnDel;
            VH(View v) {
                super(v);
                rbSelect = v.findViewById(R.id.rb_select);
                tvName = v.findViewById(R.id.tv_name);
                tvUrl = v.findViewById(R.id.tv_url);
                tvDrag = v.findViewById(R.id.tv_drag);
                btnDel = v.findViewById(R.id.btn_del);
            }
        }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_server, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            ServerStore.Server sv = servers.get(pos);
            h.tvName.setText(sv.name);
            h.tvUrl.setText(sv.url);
            // 点选：RadioButton 标记当前使用的服务器
            h.rbSelect.setChecked(sv.url.equals(
                    ServerStore.getCurrentUrl(ServerManageActivity.this)));
            h.rbSelect.setOnClickListener(v -> {
                ServerStore.setCurrentUrl(ServerManageActivity.this, sv.url);
                // 点选后自动返回服务端主页面
                finish();
            });
            // 按住 ≡ 手柄上下拖动排序
            h.tvDrag.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(h);
                }
                return false;
            });
            h.itemView.setOnClickListener(v -> showAddDialog(pos));
            h.btnDel.setOnClickListener(v -> {
                ServerStore.Server removed = servers.remove(pos);
                // 删掉的是当前选中的，清空选中
                if (removed.url.equals(
                        ServerStore.getCurrentUrl(ServerManageActivity.this))) {
                    ServerStore.setCurrentUrl(ServerManageActivity.this, null);
                }
                ServerStore.save(ServerManageActivity.this, servers);
                notifyDataSetChanged();
            });
        }
        @Override public int getItemCount() { return servers.size(); }
    }
}
