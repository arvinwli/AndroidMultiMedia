package com.wangheart.rtmpfile.rtmp;

/**
 * Author : eric
 * CreateDate : 2017/12/25  18:13
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :  RTMPDump调用类
 * Modified :
 */

public class RtmpHandle {
    private static RtmpHandle mInstance;

    private RtmpHandle() {
    }

    public synchronized static RtmpHandle getInstance() {
        if (mInstance == null) {
            mInstance = new RtmpHandle();
        }
        return mInstance;
    }

    static {
        System.loadLibrary("rtmp");
    }

    public native void pushFile(String path);

    public native int connect(String url);

    public native int push(byte[] buf, int length);

    public native int close();
}
