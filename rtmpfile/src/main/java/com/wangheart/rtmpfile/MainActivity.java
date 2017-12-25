package com.wangheart.rtmpfile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;
import com.wangheart.rtmpfile.rtmp.RtmpHandle;
import com.wangheart.rtmpfile.utils.LogUtils;

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

    public void btnCamera(View view) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }

    public void btnPushFile(View view) {
        Intent intent = new Intent(this, PushFileRtmpActivity.class);
        startActivity(intent);
    }

    public void btnMediaCodec(View view) {
        Intent intent = new Intent(this, CameraMediaCodecActivity.class);
        startActivity(intent);
    }

    public void librmtp(View view) {
        new Thread(){
            @Override
            public void run() {
                super.run();
                final String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "dongfengpo.flv";
                File file = new File(path);
                LogUtils.d(path + "  " + file.exists());
                RtmpHandle.getInstance().pushFile(path);
            }
        }.start();
    }
}
