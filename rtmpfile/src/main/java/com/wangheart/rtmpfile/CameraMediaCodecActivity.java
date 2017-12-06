package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;
import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author : eric
 * CreateDate : 2017/11/6  10:57
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class CameraMediaCodecActivity extends Activity implements SurfaceHolder.Callback {
    private MySurfaceView sv;
    private final int WIDTH = 480;
    private final int HEIGHT = 320;
    private SurfaceHolder mHolder;
    private String url = "rtmp://192.168.31.127/live/test";
    //采集到每帧数据时间
    long previewTime = 0;
    //每帧开始编码时间
    long encodeTime = 0;
    //采集数量
    int count = 0;
    //编码数量
    int encodeCount = 0;
    //采集数据回调
    private StreamIt mStreamIt;
    private MediaCodec mMediaCodec;
    private static final String VCODEC_MIME = "video/avc";
    byte[] mPpsSps = new byte[0];
    private final String DATA_DIR = Environment.getExternalStorageDirectory() + File.separator + "AndroidVideo";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        init();
    }

    private void init() {
        FFmpegHandle.getInstance().initVideo(url);
        sv = findViewById(R.id.sv);
        initMediaCodec();
        mStreamIt = new StreamIt();
        CameraInterface.getInstance().openCamera(1);
        Camera.Parameters params = CameraInterface.getInstance().getParams();
        params.setPictureFormat(ImageFormat.YV12);
        params.setPictureSize(WIDTH, HEIGHT);
        params.setPreviewSize(WIDTH, HEIGHT);
        params.setPreviewFpsRange(30000, 30000);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        CameraInterface.getInstance().resetParams(params);
        FFmpegHandle.init(this);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);

    }

    private void initMediaCodec() {
        int framerate = 15;
        int bitrate = 2 * WIDTH * HEIGHT * framerate / 20;
        try {
            MediaCodecInfo mediaCodecInfo = selectCodec(VCODEC_MIME);
            if (mediaCodecInfo == null) {
                Toast.makeText(this, "mMediaCodec null", Toast.LENGTH_LONG).show();
                throw new RuntimeException("mediaCodecInfo is Empty");
            }
            LogUtils.w("MediaCodecInfo " + mediaCodecInfo.getName());
            mMediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(VCODEC_MIME, WIDTH, HEIGHT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            //是否是编码器
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            LogUtils.w(Arrays.toString(types));
            for (String type : types) {
                LogUtils.e("equal " + mimeType.equalsIgnoreCase(type));
                if (mimeType.equalsIgnoreCase(type)) {
                    LogUtils.e("codecInfo " + codecInfo.getName());
                    return codecInfo;
                }
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegHandle.getInstance().close();
        CameraInterface.getInstance().releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHolder != null) {
            CameraInterface.getInstance().startPreview(mHolder, mStreamIt);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CameraInterface.getInstance().stopPreview();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        CameraInterface.getInstance().startPreview(mHolder, mStreamIt);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        CameraInterface.getInstance().stopPreview();
        CameraInterface.getInstance().releaseCamera();
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();

    public void btnStart(View view) {
    }

    public class StreamIt implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            long endTime = System.currentTimeMillis();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    encodeTime = System.currentTimeMillis();
                    encode(data);
                    LogUtils.w("编码第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                }
            });
            LogUtils.d("采集第:" + (++count) + "帧，距上一帧间隔时间:"
                    + (endTime - previewTime) + "  " + Thread.currentThread().getName());
            previewTime = endTime;
        }
    }

    /**
     * 预览格式设置为NV21，两个平面，第一个平面Y 第二个平面VUVU交替。
     * 编码器颜色格式 COLOR_FormatYUV420SemiPlanar 则是UVUV交替
     * <p>
     * 览格式设置为YV12,三个平面，第一个平面Y 第二个平面V，第三个U。
     * 编码器颜色格式COLOR_FormatYUV420Planar 则是 第二个平面U，第三个V
     *
     * @param buf
     */
    private void encode(byte[] buf) {
        long startTime = System.currentTimeMillis();
        int length = buf.length;
        //YV12数据转化成COLOR_FormatYUV420Planar
        for (int i = (WIDTH * HEIGHT); i < length / 4; i++) {
            byte temp = buf[i];
            buf[i] = buf[i + length / 4];
            buf[i + length / 4] = temp;
        }
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        byte[] dst = new byte[buf.length];
        try {
            //查找可用的的input buffer用来填充有效数据
            int bufferIndex = mMediaCodec.dequeueInputBuffer(-1);
            if (bufferIndex >= 0) {
                //数据放入到inputBuffer中
                ByteBuffer inputBuffer = inputBuffers[bufferIndex];
                inputBuffer.clear();
                inputBuffer.put(buf, 0, length);
                //把数据传给编码器并进行编码
                mMediaCodec.queueInputBuffer(bufferIndex, 0,
                        inputBuffers[bufferIndex].position(),
                        System.nanoTime() / 1000, 0);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                //输出buffer出队，返回成功的buffer索引。
                int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] outData = new byte[bufferInfo.size];
                    //把数据复制到outData中。
                    outputBuffer.get(outData);
                    //记录pps和sps
                    //103==01100111    101=01100101
                    if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 103) {
                        LogUtils.w("pps sps :" + Arrays.toString(outData));
                        mPpsSps = outData;
                    } else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 101) {
                        //在关键帧前面加上pps和sps数据
                        byte[] iframeData = new byte[mPpsSps.length + outData.length];
                        System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
                        System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
                        outData = iframeData;
                    }
//                    FFmpegHandle.getInstance().sendH264(outData, outData.length);
                    //保存到文件中
//                    FileUtil.save(outData, 0, outData.length, DATA_DIR + File.separator + "/eric.h264", true);
                    FFmpegHandle.getInstance().sendH264(outData, outData.length);
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    //循环查找是否还有已编码成功的数据。
                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            } else {
                LogUtils.w("No buffer available !");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stack = sw.toString();
            LogUtils.e(stack);
            e.printStackTrace();
        } finally {
            LogUtils.d("encode time " + (System.currentTimeMillis() - startTime));
            CameraInterface.getInstance().getCamera().addCallbackBuffer(dst);
        }
    }

}
