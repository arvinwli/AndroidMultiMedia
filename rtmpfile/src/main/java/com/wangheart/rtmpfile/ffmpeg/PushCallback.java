package com.wangheart.rtmpfile.ffmpeg;

/**
 * Author : eric
 * CreateDate : 2017/11/2  10:45
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public interface PushCallback {
    public void videoCallback(long pts, long dts, long duration, long index);
}
