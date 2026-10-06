package com.weleone.ytmp3;


import android.content.ContentValues;


import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.arthenica.ffmpegkit.StreamInformation;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

public class LocalFragment extends Fragment {
    private ProgressBar progress;
    private TextView tvStatus;
    private Button btnGo;
    private RecyclerView rvFiles;
    private TextView tvFilesEmpty;
    private TextView tvLastPath;
    // 分辨率下拉显示值 -> 传给 yt-dlp 的数值
    private static final String[] RES_VALUES = {"360", "480", "720", "1080", "1440", "2160"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle s) {
        View v = inflater.inflate(R.layout.fragment_local, container, false);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(requireContext()));

        EditText etUrl = v.findViewById(R.id.et_url);
        progress = v.findViewById(R.id.progress);
        tvStatus = v.findViewById(R.id.tv_status);
        btnGo = v.findViewById(R.id.btn_go);
        rvFiles = v.findViewById(R.id.rv_files);
        tvFilesEmpty = v.findViewById(R.id.tv_files_empty);
        tvLastPath = v.findViewById(R.id.tv_last_path);
        DownloadListHelper.setup(requireContext(), rvFiles);

        RadioButton rbMp3 = v.findViewById(R.id.rb_mp3);
        RadioButton rbMp4 = v.findViewById(R.id.rb_mp4);
        Spinner spBitrate = v.findViewById(R.id.sp_bitrate);
        Spinner spResolution = v.findViewById(R.id.sp_resolution);

        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"128K", "192K", "256K", "320K"});
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBitrate.setAdapter(bitrateAdapter);
        spBitrate.setSelection(3);

        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"360p", "480p", "720p", "1080p", "1440p", "2160p"});
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spResolution.setAdapter(resAdapter);
        spResolution.setSelection(3);

        // 两个 RadioButton 手动互斥 + 联动 Spinner 变灰
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

        btnGo.setOnClickListener(view -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) { toast("请先粘贴链接"); return; }
            boolean isMp4 = rbMp4.isChecked();
            String bitrate = spBitrate.getSelectedItem().toString();
            String resolution = RES_VALUES[spResolution.getSelectedItemPosition()];
            startLocalDownload(url, isMp4 ? "mp4" : "mp3", bitrate, resolution);
        });
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadListHelper.refresh(getContext(), rvFiles, tvFilesEmpty);
    }

    private void startLocalDownload(String url, String fmt, String bitrate, String resolution) {
        btnGo.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("获取信息…", 5);
        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                String title = py.getModule("ytdlp_helper")
                        .callAttr("get_title", url).toString();
                String safe = Pattern.compile("[\\\\/:*?\"<>|]")
                        .matcher(title).replaceAll("_");
                if (safe.length() > 60) safe = safe.substring(0, 60);

                File outDir = new File(requireContext().getExternalFilesDir(
                        Environment.DIRECTORY_DOWNLOADS), "YTMP3");
                outDir.mkdirs();

                setStatus("下载中…", 20);
                String raw = py.getModule("ytdlp_helper")
                        .callAttr("download", url, fmt,
                                outDir.getAbsolutePath(), resolution).toString();

                // 文件名带上码率/分辨率：title_320K.mp3 / title_1080p.mp4
                String quality = "mp3".equals(fmt) ? bitrate : resolution + "p";
                String outName = safe + "_" + quality + "." + fmt;
                File outFile = new File(outDir, outName);

                if ("mp3".equals(fmt)) {
                    File rawFile = new File(raw);
                    setStatus("转码 MP3 " + bitrate + " 中…", 70);
                    runFfmpeg(rawFile.getAbsolutePath(), outFile.getAbsolutePath(), bitrate);
                    rawFile.delete();
                } else {
                    // 视频：Python 侧分离下载，返回 video|audio 双路径
                    // Java 侧用 ffmpeg 合并转码，保证有画面且手机可播
                    String videoPath = raw;
                    String audioPath = null;
                    int sep = raw.indexOf('|');
                    if (sep > 0) {
                        videoPath = raw.substring(0, sep);
                        audioPath = raw.substring(sep + 1);
                    }
                    setStatus("合并转码 MP4 中…", 70);
                    mergeToMp4(videoPath, audioPath, outFile.getAbsolutePath());
                    //noinspection ResultOfMethodCallIgnored
                    new File(videoPath).delete();
                    if (audioPath != null) {
                        //noinspection ResultOfMethodCallIgnored
                        new File(audioPath).delete();
                    }
                }

                setStatus("完成: " + outName, 100);
                // 成品搬到系统公共 Download/YTMP3（私有目录文件管理器看不到）
                Uri savedUri = saveToPublicDownloads(outFile, outName);
                String toastMsg = savedUri != null
                        ? "已保存到 Download/YTMP3/" + outName
                        : "公共目录写入失败，文件在应用私有目录";
                String dirPath = new File(
                        Environment.getExternalStorageDirectory(),
                        Environment.DIRECTORY_DOWNLOADS + "/YTMP3")
                        .getAbsolutePath();
                ui(() -> {
                    toast(toastMsg);
                    btnGo.setEnabled(true);
                    tvLastPath.setText("文件位置：" + dirPath);
                    DownloadListHelper.refresh(getContext(), rvFiles, tvFilesEmpty);
                    // 完成提示跟 toast 同时消失（约 3.5 秒）
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdded()) tvStatus.setText("");
                    }, 3500);
                });
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                if (msg.length() > 500) msg = msg.substring(0, 500);
                String fmsg = msg;
                ui(() -> {
                    setStatus("失败: " + fmsg, 0);
                    progress.setVisibility(View.GONE);
                    btnGo.setEnabled(true);
                });
            }
        }).start();
    }

    /** 用 ffmpeg-kit 同步执行转码；失败时抛带详细输出的异常 */
    private void runFfmpeg(String input, String output, String bitrate) throws Exception {
        // 路径可能含空格，用引号包裹
        String cmd = "-y -i \"" + input + "\" -vn -b:a " + bitrate.toLowerCase()
                + " -ar 44100 \"" + output + "\"";
        FFmpegSession session = FFmpegKit.execute(cmd);
        if (ReturnCode.isSuccess(session.getReturnCode())) return;
        String detail = session.getOutput();
        if (detail == null || detail.isEmpty())
            detail = String.valueOf(session.getFailStackTrace());
        if (detail == null || detail.isEmpty())
            detail = "rc=" + session.getReturnCode();
        if (detail.length() > 500) detail = detail.substring(detail.length() - 500);
        throw new RuntimeException("ffmpeg 执行失败: " + detail);
    }

    /** 检测视频是否为手机可播组合（H.264/mpeg4 视频 + AAC/MP3 音频） */
    private boolean isPhoneFriendly(String path) {
        try {
            MediaInformationSession session = FFprobeKit.getMediaInformation(path);
            MediaInformation info = session != null ? session.getMediaInformation() : null;
            if (info == null || info.getStreams() == null) return false;
            boolean hasVideo = false;
            for (StreamInformation st : info.getStreams()) {
                String type = st.getType();
                String codec = st.getCodec();
                if (codec == null) continue;
                codec = codec.toLowerCase();
                if ("video".equals(type)) {
                    hasVideo = true;
                    // VP9/AV1/HEVC 在部分手机上黑屏，只认 h264/mpeg4
                    if (!codec.contains("h264") && !codec.contains("avc")
                            && !codec.contains("mpeg4")) return false;
                } else if ("audio".equals(type)) {
                    // Opus 在 MP4 里很多播放器不支持
                    if (!codec.contains("aac") && !codec.contains("mp3")) return false;
                }
            }
            return hasVideo;
        } catch (Exception e) {
            return false;
        }
    }

    /** 把分离的视频流+音频流合并转成手机可播的 mp4（mpeg4+aac）。
     *  一律重编码：VP9/Opus 等直接进 MP4 会黑屏；免费版无 libx264，用 mpeg4。
     *  audioPath 可为 null（无音频时只转视频）。 */
    private void mergeToMp4(String videoPath, String audioPath,
                            String output) throws Exception {
        String cmd;
        if (audioPath != null) {
            cmd = "-y -i \"" + videoPath + "\" -i \"" + audioPath + "\""
                    + " -c:v mpeg4 -q:v 3 -c:a aac -b:a 192k -shortest \""
                    + output + "\"";
        } else {
            cmd = "-y -i \"" + videoPath + "\""
                    + " -c:v mpeg4 -q:v 3 -an \"" + output + "\"";
        }
        FFmpegSession s = FFmpegKit.execute(cmd);
        if (ReturnCode.isSuccess(s.getReturnCode())) return;
        String detail = s.getOutput();
        if (detail == null || detail.isEmpty())
            detail = String.valueOf(s.getFailStackTrace());
        if (detail == null || detail.isEmpty())
            detail = "rc=" + s.getReturnCode();
        if (detail.length() > 500) detail = detail.substring(detail.length() - 500);
        throw new RuntimeException("转 MP4 失败: " + detail);
    }

    /** 把成品复制到系统公共 Download/YTMP3（targetSdk 34 无需额外权限，需 API 29+）。
     *  失败时静默留在应用私有目录，不抛错。 */
    /** 返回搬运后的 MediaStore Uri，失败返回 null（失败时会删掉已建的空记录） */
    private Uri saveToPublicDownloads(File srcFile, String fileName) {
        Uri uri = null;
        try {
            String mime;
            if (fileName.endsWith(".mp3")) mime = "audio/mpeg";
            else if (fileName.endsWith(".webm")) mime = "video/webm";
            else mime = "video/mp4";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/YTMP3");
            uri = requireContext().getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;
            try (InputStream in = new FileInputStream(srcFile);
                 OutputStream out = requireContext().getContentResolver()
                         .openOutputStream(uri)) {
                if (out == null) throw new RuntimeException("openOutputStream null");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            srcFile.delete();
            return uri;
        } catch (Exception ignored) {
            // 写入失败：删掉已建的空记录，避免留下 0B 文件
            if (uri != null) {
                try {
                    requireContext().getContentResolver().delete(uri, null, null);
                } catch (Exception ignored2) {
                }
            }
        }
        return null;
    }

    private void setStatus(String t, int p) {
        ui(() -> {
            tvStatus.setText(t);
            progress.setProgress(p);
            if (p >= 100 || p == 0) progress.setVisibility(View.GONE);
            else progress.setVisibility(View.VISIBLE);
        });
    }

    private void ui(Runnable r) {
        if (isAdded()) requireActivity().runOnUiThread(r);
    }

    private void toast(String t) {
        ui(() -> Toast.makeText(requireContext(), t, Toast.LENGTH_LONG).show());
    }
}
