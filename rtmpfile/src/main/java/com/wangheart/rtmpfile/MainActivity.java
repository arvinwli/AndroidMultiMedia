package com.wangheart.rtmpfile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;

import java.io.File;

public class MainActivity extends Activity {
    private TextView tvCodecInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    private void initView() {
        tvCodecInfo = findViewById(R.id.tv_codec_info);
    }


    private void initData() {
        FFmpegHandle.init(this);
        tvCodecInfo.setText(FFmpegHandle.getInstance().getAvcodecConfiguration());
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
