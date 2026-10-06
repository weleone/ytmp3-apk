package com.weleone.ytmp3;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class ServerFragment extends Fragment {
    private ProgressBar progress;
    private TextView tvStatus, tvCurrent;
    private Button btnGo, btnSave;
    private RadioButton rbMp3, rbMp4;
    private Spinner spBitrate, spResolution;
    private RecyclerView rvFiles;
    private TextView tvFilesEmpty;
    private TextView tvLastPath;
    private ServerStore.Server current;
    private String pendingUrl, pendingName;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean polling;
    // 分辨率下拉显示值 -> 传给服务器的数值
    private static final String[] RES_VALUES = {"360", "480", "720", "1080", "1440", "2160"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle s) {
        View v = inflater.inflate(R.layout.fragment_server, container, false);
        EditText etUrl = v.findViewById(R.id.et_url);
        progress = v.findViewById(R.id.progress);
        tvStatus = v.findViewById(R.id.tv_status);
        tvCurrent = v.findViewById(R.id.tv_current);
        btnGo = v.findViewById(R.id.btn_go);
        btnSave = v.findViewById(R.id.btn_save);
        rbMp3 = v.findViewById(R.id.rb_mp3);
        rbMp4 = v.findViewById(R.id.rb_mp4);
        spBitrate = v.findViewById(R.id.sp_bitrate);
        spResolution = v.findViewById(R.id.sp_resolution);
        // 两个 RadioButton 手动互斥（两列布局无法用 RadioGroup）+ 联动 Spinner 变灰
        rbMp3.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                rbMp4.setChecked(false);
                spBitrate.setEnabled(true);
                spResolution.setEnabled(false);
            }
        });
        rbMp4.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                rbMp3.setChecked(false);
                spBitrate.setEnabled(false);
                spResolution.setEnabled(true);
            }
        });
        // 初始 MP3 选中，分辨率不可选
        spResolution.setEnabled(false);
        rvFiles = v.findViewById(R.id.rv_files);
        tvFilesEmpty = v.findViewById(R.id.tv_files_empty);
        tvLastPath = v.findViewById(R.id.tv_last_path);
        DownloadListHelper.setup(requireContext(), rvFiles);

        // 码率下拉：128K/192K/256K/320K，默认 320K
        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"128K", "192K", "256K", "320K"});
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBitrate.setAdapter(bitrateAdapter);
        spBitrate.setSelection(3);
        // 分辨率下拉：360p~2160p，默认 1080p
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"360p", "480p", "720p", "1080p", "1440p", "2160p"});
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spResolution.setAdapter(resAdapter);
        spResolution.setSelection(3);

        v.findViewById(R.id.btn_servers).setOnClickListener(x ->
                startActivity(new Intent(requireContext(), ServerManageActivity.class)));

        v.findViewById(R.id.btn_docker).setOnClickListener(x -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://hub.docker.com/r/weleone/ytmp3")));
            } catch (Exception e) {
                toast("无法打开浏览器");
            }
        });

        // 检测服务器连通性：GET /api/health
        v.findViewById(R.id.btn_ping).setOnClickListener(x -> {
            if (current == null) { toast("请先添加并选择服务器"); return; }
            toast("正在检测…");
            String base = current.url.endsWith("/")
                    ? current.url.substring(0, current.url.length() - 1) : current.url;
            String target = base + "/api/health";
            new Thread(() -> {
                String msg;
                try {
                    HttpURLConnection c = (HttpURLConnection)
                            new URL(target).openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestMethod("GET");
                    int code = c.getResponseCode();
                    msg = (code == 200) ? "服务器连接正常" : "服务器异常（HTTP " + code + "）";
                } catch (Exception e) {
                    msg = "连接失败：检查地址和网络";
                }
                String fmsg = msg;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) toast(fmsg);
                });
            }).start();
        });

        btnGo.setOnClickListener(view -> {
            if (current == null) { toast("请先添加并选择服务器"); return; }
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) { toast("请先粘贴链接"); return; }
            boolean isVideo = rbMp4.isChecked();
            String mode = isVideo ? "video" : "audio";
            String codec = isVideo ? "mp4" : "mp3";
            String bitrate = spBitrate.getSelectedItem().toString();
            String resolution = RES_VALUES[spResolution.getSelectedItemPosition()];
            submitToServer(url, mode, codec, bitrate, resolution);
        });

        btnSave.setOnClickListener(view -> {
            if (pendingUrl == null) return;
            DownloadManager dm = (DownloadManager) requireContext()
                    .getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(pendingUrl));
            req.setTitle(pendingName);
            req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "YTMP3/" + pendingName);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long downloadId = dm.enqueue(req);
            btnSave.setEnabled(false);
            toast("开始保存到手机");
            // 系统下载完成广播偶尔送达不稳定，改为主动轮询下载状态
            Handler pollHandler = new Handler(Looper.getMainLooper());
            Runnable poll = new Runnable() {
                @Override public void run() {
                    if (!isAdded()) return;
                    int status = -1;
                    try (Cursor cur = dm.query(
                            new DownloadManager.Query().setFilterById(downloadId))) {
                        if (cur != null && cur.moveToFirst()) {
                            status = cur.getInt(
                                    cur.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        }
                    } catch (Exception ignored) {
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        onDownloadSaved(pollHandler);
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        btnSave.setEnabled(true);
                        toast("保存失败，检查服务器是否可连");
                    } else {
                        // 还在下载中，2 秒后再查
                        pollHandler.postDelayed(this, 2000);
                    }
                }
            };
            pollHandler.postDelayed(poll, 2000);
        });
        return v;
    }

    /** 保存到手机下载成功后：触发系统扫描 + 带重试刷新文件列表 */
    private void onDownloadSaved(Handler h) {
        if (!isAdded()) return;
        // 先主动触发系统扫描下载目录，加快 MediaStore 索引
        String dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + "/YTMP3";
        try {
            MediaScannerConnection.scanFile(requireContext(),
                    new String[]{dir}, null, null);
        } catch (Exception ignored) {
        }
        int before = DownloadListHelper.refresh(getContext(), rvFiles, tvFilesEmpty);
        Runnable doRefresh = new Runnable() {
            int tries = 0;
            @Override public void run() {
                if (!isAdded()) return;
                int after = DownloadListHelper.refresh(getContext(), rvFiles, tvFilesEmpty);
                if (after > before || tries >= 4) {
                    btnSave.setEnabled(true);
                    tvLastPath.setText("文件位置："
                            + Environment.getExternalStorageDirectory()
                            + "/" + Environment.DIRECTORY_DOWNLOADS + "/YTMP3");
                } else {
                    tries++;
                    h.postDelayed(this, 2000);
                }
            }
        };
        h.postDelayed(doRefresh, 1500);
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadListHelper.refresh(getContext(), rvFiles, tvFilesEmpty);
        List<ServerStore.Server> list = ServerStore.load(requireContext());
        if (!list.isEmpty()) {
            // 优先用保存的当前选中
            String curUrl = ServerStore.getCurrentUrl(requireContext());
            current = null;
            if (curUrl != null) {
                for (ServerStore.Server sv : list) {
                    if (curUrl.equals(sv.url)) { current = sv; break; }
                }
            }
            if (current == null) current = list.get(0);
            tvCurrent.setText("当前: " + current.name + " (" + current.url + ")");
        } else {
            tvCurrent.setText("未选择服务器");
            current = null;
        }
    }

    private void submitToServer(String url, String mode, String codec,
                                String bitrate, String resolution) {
        btnGo.setEnabled(false);
        btnSave.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        setStatus("提交到服务器…", 5);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("url", url);
                body.put("mode", mode);
                body.put("codec", codec);
                body.put("bitrate", bitrate);
                body.put("resolution", resolution);
                JSONObject resp = post(current.url + "/api/convert", body.toString());
                String jobId = resp.getString("job_id");
                poll(jobId, codec, bitrate, resolution);
            } catch (Exception e) {
                ui.post(() -> {
                    setStatus("提交失败: " + e.getMessage(), 0);
                    progress.setVisibility(View.GONE);
                    btnGo.setEnabled(true);
                });
            }
        }).start();
    }

    private void poll(String jobId, String codec, String bitrate, String resolution) {
        polling = true;
        new Thread(() -> {
            try {
                while (polling) {
                    Thread.sleep(2000);
                    JSONObject st = get(current.url + "/api/status/" + jobId);
                    String status = st.optString("status", "");
                    int prog = st.optInt("progress", 0);
                    String title = st.optString("title", "");
                    ui.post(() -> {
                        setStatus(status + (title.isEmpty() ? "" : ": " + title),
                                Math.max(prog, 5));
                    });
                    if ("done".equals(status)) {
                        polling = false;
                        String dl = current.url + "/api/download/" + jobId;
                        // 后缀用服务器返回的真实编码，兜底用用户选择的编码
                        String ext = st.optString("format", codec);
                        if (!ext.matches("[a-z0-9]+")) ext = codec;
                        // 文件名带上码率/分辨率：title_320K.mp3 / title_1080p.mp4
                        String quality = "mp3".equals(codec) ? bitrate : resolution + "p";
                        String base = title.isEmpty() ? jobId
                                : title.replaceAll("[\\\\/:*?\"<>|]", "_");
                        String name = base + "_" + quality + "." + ext;
                        pendingUrl = dl; pendingName = name;
                        ui.post(() -> {
                            setStatus("服务器转换完成", 100);
                            progress.setVisibility(View.GONE);
                            btnSave.setVisibility(View.VISIBLE);
                            btnSave.setEnabled(true);
                            btnGo.setEnabled(true);
                            toast("点\"保存到手机\"下载");
                        });
                        return;
                    } else if ("error".equals(status)) {
                        polling = false;
                        String err = st.optString("error", "未知错误");
                        ui.post(() -> {
                            setStatus("失败: " + err, 0);
                            progress.setVisibility(View.GONE);
                            btnGo.setEnabled(true);
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                polling = false;
                ui.post(() -> {
                    setStatus("轮询失败: " + e.getMessage(), 0);
                    progress.setVisibility(View.GONE);
                    btnGo.setEnabled(true);
                });
            }
        }).start();
    }

    private JSONObject post(String urlStr, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }
        return readJson(c);
    }

    private JSONObject get(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        return readJson(c);
    }

    private JSONObject readJson(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        if (code >= 400) throw new IOException("HTTP " + code + ": " + sb);
        return new JSONObject(sb.toString());
    }

    private void setStatus(String t, int p) {
        tvStatus.setText(t);
        progress.setProgress(p);
    }

    private void toast(String t) {
        Toast.makeText(requireContext(), t, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        polling = false;
    }
}
