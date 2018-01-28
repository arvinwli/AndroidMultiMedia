package com.wangheart.rtmpfile.audio;

/**
 * Author : eric
 * CreateDate : 2018/1/11  11:31
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class FFmpegAudioHandle {
    private static FFmpegAudioHandle mInstance;

    private FFmpegAudioHandle() {

    }

    public static synchronized FFmpegAudioHandle getInstance() {
        if (mInstance == null) {
            mInstance = new FFmpegAudioHandle();
        }
        return mInstance;
    }


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("avutil-55");
        System.loadLibrary("swresample-2");
        System.loadLibrary("avcodec-57");
        System.loadLibrary("avformat-57");
        System.loadLibrary("swscale-4");
        System.loadLibrary("avfilter-6");
        System.loadLibrary("avdevice-57");
        System.loadLibrary("postproc-54");
        System.loadLibrary("ffmpeg-handle");
    }

    public native int encodePcmFile(String souPath,String tarPath);

    public native int initAudio(String url);

    public native int encodeAudio(byte[] buffer);

    public native int close();


}
