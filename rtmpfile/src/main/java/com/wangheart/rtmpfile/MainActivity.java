package com.wangheart.rtmpfile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;
import com.wangheart.rtmpfile.ffmpeg.PushCallback;

import java.io.File;

public class MainActivity extends Activity {
    private TextView tvCodecInfo;
    private TextView tvPushInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    private void initView() {
        tvCodecInfo = findViewById(R.id.tv_codec_info);
        tvPushInfo = findViewById(R.id.tv_push_info);
    }


    private void initData() {
        FFmpegHandle.init(this);
        tvCodecInfo.setText(FFmpegHandle.getInstance().getAvcodecConfiguration());
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
        log("result " + res);
    }

    public void btnPush(View view) {
        final String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "sample.flv";
        File file = new File(path);
        log(path + "  " + file.exists());
        new Thread() {
            @Override
            public void run() {
                super.run();
                int result = FFmpegHandle.getInstance().pushRtmpFile(path);
                log("result " + result);
            }
        }.start();
    }


    public void log(String content) {
        Log.w("eric", content);
    }
}
