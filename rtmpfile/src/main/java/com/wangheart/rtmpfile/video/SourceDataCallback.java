package com.wangheart.rtmpfile.video;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * @author Arvin
 * @date 2018/9/3
 * @e-mail arvinli@pacewear.com
 * @description
 */
public interface SourceDataCallback {
    void onAudioSourceDataCallback(byte[] data,int index);
    void onVideoSourceDataCallback(byte[] data,int index);
}
