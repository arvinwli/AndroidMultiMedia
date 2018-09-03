package com.wangheart.rtmpfile.video;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * @author Arvin
 * @date 2018/8/31
 * @e-mail arvinli@pacewear.com
 * @description 音视频编码数据回调
 */
public interface EncodedDataCallback {
    void onAudioEncodedCallback(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo);
    void onVideoEncodedCallback(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo);
}
