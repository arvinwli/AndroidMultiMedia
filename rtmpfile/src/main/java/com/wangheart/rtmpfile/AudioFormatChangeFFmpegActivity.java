package com.wangheart.rtmpfile;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.audio.AudioCodec;
import com.wangheart.rtmpfile.utils.FileUtil;

import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Author : eric
 * CreateDate : 2018/1/4  10:26
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class AudioFormatChangeFFmpegActivity extends Activity {
    private TextView tvInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_codec);
        initView();
    }

    private void initView() {
        tvInfo = findViewById(R.id.tv_info);
    }

    public void btnStart(View view) {
        startRecord();
    }

    private void startRecord() {
        final AudioCodec audioCodec = AudioCodec.newInstance();
        audioCodec.setIOPath(FileUtil.getMainDir().getAbsolutePath() + "/dongfengpo.mp3",
                FileUtil.getMainDir().getAbsolutePath() + "/dongfengpo.aac");
        audioCodec.prepare();
        audioCodec.startAsync();
        audioCodec.setOnCompleteListener(new AudioCodec.OnCompleteListener() {
            @Override
            public void completed() {
                audioCodec.release();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvInfo.setText("100%");
                    }
                });
            }
        });
        final DecimalFormat df = (DecimalFormat) NumberFormat.getInstance();
        df.applyPattern("##.##%");
        audioCodec.setOnProgressListener(new AudioCodec.OnProgressListener() {

            @Override
            public void progress(final long current, final long total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvInfo.setText(current + "/" + total + "  " + df.format((double) current / total));
                    }
                });
            }
        });
    }
}
