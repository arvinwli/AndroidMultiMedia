package com.wangheart.rtmpfile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;

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

}
