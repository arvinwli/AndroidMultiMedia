package com.wangheart.rtmpfile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;
import com.wangheart.rtmpfile.ffmpeg.PushCallback;
import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Author : eric
 * CreateDate : 2017/11/22  18:29
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class VideoFileRtmpFFmpegActivity extends Activity {
    private TextView tvPushInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_file_rtmp);
        initView();
        initData();
    }

    private void initView() {
        tvPushInfo = findViewById(R.id.tv_push_info);
    }


    private void initData() {
        int res = FFmpegHandle.getInstance().setCallback(new PushCallback() {
            @Override
            public void videoCallback(final long pts, final long dts, final long duration, final long index) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("pts: ").append(pts).append("\n");
                        sb.append("dts: ").append(dts).append("\n");
                        sb.append("duration: ").append(duration).append("\n");
                        sb.append("index: ").append(index).append("\n");
                        tvPushInfo.setText(sb.toString());
                    }
                });
            }
        });
        LogUtils.d("result " + res);
    }


    public void btnPush(View view) {
        final String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "main.flv";
        File file = new File(path);
        LogUtils.d(path + "  " + file.exists());
        new Thread() {
            @Override
            public void run() {
                super.run();
                int result = FFmpegHandle.getInstance().pushRtmpFile(path);
                LogUtils.d("result " + result);
            }
        }.start();
    }

}
