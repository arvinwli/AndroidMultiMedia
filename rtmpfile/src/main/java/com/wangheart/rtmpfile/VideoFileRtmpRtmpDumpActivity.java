package com.wangheart.rtmpfile;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.wangheart.rtmpfile.rtmp.RtmpHandle;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.LogUtils;

import java.io.File;

/**
 * Author : eric
 * CreateDate : 2018/2/1  10:15
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class VideoFileRtmpRtmpDumpActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtmpdump_file);
    }

    public void btnStart(View view) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                File file = new File(FileUtil.getMainDir(), "dongfengpo.flv");
                LogUtils.d("file  " + file.exists());
                RtmpHandle.getInstance().pushFile(file.getAbsolutePath());
            }
        }.start();
    }
}
