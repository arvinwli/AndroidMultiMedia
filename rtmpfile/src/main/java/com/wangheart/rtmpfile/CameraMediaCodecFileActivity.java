package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import com.wangheart.rtmpfile.device.CameraController;
import com.wangheart.rtmpfile.flv.FlvPacker;
import com.wangheart.rtmpfile.flv.Packer;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;
import com.wangheart.rtmpfile.video.VideoComponent;
import com.wangheart.rtmpfile.view.MySurfaceView;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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

public class CameraMediaCodecFileActivity extends Activity implements SurfaceHolder.Callback {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private MySurfaceView sv;
    //建议的视频宽度，不超过这个宽度，自动寻找4：3的尺寸
    private final int SUGGEST_PREVIEW_WIDTH=640;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private SurfaceHolder mHolder;
    //采集到每帧数据时间
    long previewTime = 0;
    //每帧开始编码时间
    long encodeTime = 0;
    //采集数量
    int count = 0;
    //编码数量
    int encodeCount = 0;
    //采集数据回调
    private PreviewFrameCallback mPreviewFrameCallback;
    private MediaCodec mMediaCodec;
    private static final String VCODEC_MIME = "video/avc";
    private FlvPacker mFlvPacker;
    private final int FRAME_RATE = 15;
    private OutputStream mOutStream;
    //视频编码组件封装
    private VideoComponent mVideoComponent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        init();
    }

    private void init() {
        sv = findViewById(R.id.sv);
        mVideoComponent = new VideoComponent();
        mPreviewFrameCallback = new PreviewFrameCallback();
        initCamera();
        initMediaCodec();
        mFlvPacker = new FlvPacker();
        mFlvPacker.initVideoParams(videoWidth, videoHeight, FRAME_RATE);
        mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
            @Override
            public void onPacket(byte[] data, int packetType) {
                IOUtils.write(mOutStream, data, 0, data.length);
                LogUtils.w(data.length + " " + packetType);
            }
        });
    }

    private void initMediaCodec() {
        int bitrate = 2 * videoWidth * videoHeight * FRAME_RATE / 20;
        try {
            MediaCodecInfo mediaCodecInfo = mVideoComponent.getSupportMediaCodecInfo(VCODEC_MIME);
            if (mediaCodecInfo == null) {
                throw new RuntimeException("mediaCodecInfo is Empty");
            }
            mMediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(VCODEC_MIME, videoWidth, videoHeight);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    mVideoComponent.getSupportMediaCodecColorFormat(mediaCodecInfo));
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void initCamera(){
        CameraController.getInstance().open(0);
        Camera.Parameters params = CameraController.getInstance().getParams();
        //查找合适的预览尺寸
        Camera.Size size= mVideoComponent.getSupportPreviewSize(params,SUGGEST_PREVIEW_WIDTH);
        if(size==null){
            throw new RuntimeException("not found support preview size");
        }
        videoWidth=size.width;
        videoHeight=size.height;
        params.setPictureFormat(ImageFormat.JPEG);
        params.setPreviewFormat(mVideoComponent.getSupportPreviewColorFormat(params));
//        params.setPictureSize(videoWidth, videoHeight);
        params.setPreviewSize(videoWidth, videoHeight);
        params.setPreviewFpsRange(15000, 20000);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        CameraController.getInstance().adjustOrientation(this, new CameraController.OnOrientationChangeListener() {
            @Override
            public void onChange(int degree) {
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) sv.getLayoutParams();
                LogUtils.d(PhoneUtils.getWidth() + " " + PhoneUtils.getHeight());
                if (degree == 90) {
                    lp.height = PhoneUtils.getWidth() * videoWidth / videoHeight;
                } else {
                    lp.height = PhoneUtils.getWidth() * videoHeight / videoWidth;
                }
                sv.setLayoutParams(lp);
            }
        });
        CameraController.getInstance().resetParams(params);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraController.getInstance().close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHolder != null) {
            CameraController.getInstance().startPreview(mHolder, mPreviewFrameCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CameraController.getInstance().stopPreview();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mFlvPacker.start();
        mOutStream = IOUtils.open(FileUtil.getMainDir() + File.separator + "/CameraMediaCodecFileActivity.flv", true);
        CameraController.getInstance().startPreview(mHolder, mPreviewFrameCallback);

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mFlvPacker.stop();
        CameraController.getInstance().stopPreview();
        CameraController.getInstance().close();
        IOUtils.close(mOutStream);
    }


    public class PreviewFrameCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            long endTime = System.currentTimeMillis();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    encodeTime = System.currentTimeMillis();
                    flvPackage(data);
                    LogUtils.w("编码第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                }
            });
            LogUtils.d("采集第:" + (++count) + "帧，距上一帧间隔时间:"
                    + (endTime - previewTime) + "  " + Thread.currentThread().getName());
            previewTime = endTime;
        }
    }


    private void flvPackage(byte[] bufSou) {
        //编码格式转换
        byte[] buf = mVideoComponent.convert(bufSou);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        try {
            //查找可用的的input buffer用来填充有效数据
            int bufferIndex = mMediaCodec.dequeueInputBuffer(-1);
            if (bufferIndex >= 0) {
                //数据放入到inputBuffer中
                ByteBuffer inputBuffer = inputBuffers[bufferIndex];
                inputBuffer.clear();
                inputBuffer.put(buf, 0, buf.length);
                //把数据传给编码器并进行编码
                mMediaCodec.queueInputBuffer(bufferIndex, 0,
                        inputBuffers[bufferIndex].position(),
                        System.nanoTime() / 1000, 0);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                //输出buffer出队，返回成功的buffer索引。
                int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    //进行flv封装
                    mFlvPacker.onVideoData(outputBuffer, bufferInfo);
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            } else {
                LogUtils.w("No buffer available !");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
