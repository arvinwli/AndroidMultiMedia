package com.wangheart.rtmpfile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;
import com.wangheart.rtmpfile.utils.PermissionsChecker;
import com.wangheart.rtmpfile.utils.PhoneUtils;

public class MainActivity extends Activity {
    private TextView tvCodecInfo;
    private PermissionsChecker mPermissionsChecker; // 权限检测器
    private static final int REQUEST_CODE = 0; // 请求码


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 缺少权限时, 进入权限配置页面
        if (mPermissionsChecker.lacksPermissions(PermissionsChecker.PERMISSIONS)) {
            PermissionsActivity.startActivityForResult(this, REQUEST_CODE, PermissionsChecker.PERMISSIONS);
        }
    }

    private void initView() {
        PhoneUtils.init(this);
        tvCodecInfo = findViewById(R.id.tv_codec_info);
    }


    private void initData() {
        mPermissionsChecker = new PermissionsChecker(this);
        //读取FFmpeg的配置信息
        String content = FFmpegHandle.getInstance().getAvcodecConfiguration();
        if (!TextUtils.isEmpty(content)) {
            tvCodecInfo.setText(content.replace("--", "\n--"));
        }
    }

    /**
     * FFmpeg推送视频文件
     *
     * @param view
     */
    public void btnFFmpegPushFile(View view) {
        Intent intent = new Intent(this, VideoFileRtmpFFmpegActivity.class);
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
        Intent intent = new Intent(this, CameraMediaCodecFileActivity.class);
        startActivity(intent);
    }

    /**
     * RTMPDump推送视频文件
     *
     * @param view
     */
    public void btmRtmpdumpFile(View view) {
        startActivity(new Intent(this, VideoFileRtmpRtmpDumpActivity.class));
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

    /**
     * 转换音频文件格式
     *
     * @param view
     */
    public void btnAudioFormatChange(View view) {
        startActivity(new Intent(this, AudioFormatChangeFFmpegActivity.class));
    }

    /**
     * 音频采集并使用MediaCodec编码
     *
     * @param view
     */
    public void btnAudioRecord(View view) {
        startActivity(new Intent(this, AudioRecordMediaCodecActivity.class));
    }

    /**
     * 音频采集并使用FFmpeg编码
     *
     * @param view
     */
    public void btnAudioRecordFFmpeg(View view) {
        startActivity(new Intent(this, AudioRecordFFmpegActivity.class));
    }
}
