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

    /**
     * FFmpeg推送视频文件
     *
     * @param view
     */
    public void btnFFmpegPushFile(View view) {
        Intent intent = new Intent(this, FFmpegPushFileRtmpActivity.class);
        startActivity(intent);
    }

    /**
     * FFmpeg推送摄像头采集的数据
     *
     * @param view
     */
    public void btnCameraFFmpeg(View view) {
        Intent intent = new Intent(this, CameraFFmpegPushRtmpActivity.class);
        startActivity(intent);
    }

    /**
     * MediaCodec编码摄像头数据并保存问flv格式到文件
     *
     * @param view
     */
    public void btnMediaCodec(View view) {
        Intent intent = new Intent(this, CameraMediaCodecActivity.class);
        startActivity(intent);
    }

    /**
     * RTMPDump推送视频文件
     *
     * @param view
     */
    public void btmRtmpdumpFile(View view) {
        new Thread() {
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

    /**
     * MediaCodec编码摄像头数据并使用RTMPDump进行推流
     *
     * @param view
     */
    public void btnMediaCodecRtmp(View view) {
        Intent intent = new Intent(this, CameraMediaCodecRtmpActivity.class);
        startActivity(intent);
    }

    public void btnAudioFormatChange(View view) {
        startActivity(new Intent(this, AudioCodecActivity.class));
    }
}
