package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import com.wangheart.rtmpfile.camera.CameraInterface;
import com.wangheart.rtmpfile.flv.FlvPacker;
import com.wangheart.rtmpfile.flv.Packer;
import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.BytesHexStrTranslate;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;
import com.wangheart.rtmpfile.video.AudioComponent;
import com.wangheart.rtmpfile.video.EncodedDataCallback;
import com.wangheart.rtmpfile.video.VideoComponent;
import com.wangheart.rtmpfile.view.MySurfaceView;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
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

public class VideoCompoundFileActivity extends Activity implements SurfaceHolder.Callback,EncodedDataCallback {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private MySurfaceView sv;
    //建议的视频宽度，不超过这个宽度，自动寻找4：3的尺寸
    private final int SUGGEST_PREVIEW_WIDTH=640;
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
    private FlvPacker mFlvPacker;
    private OutputStream mOutStream;
    //视频编码组件封装
    private VideoComponent mVideoComponent;
    private AudioComponent mAudioComponent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_compound_file);
        init();
    }

    private void init() {
        sv = findViewById(R.id.sv);
        mVideoComponent = new VideoComponent();
        mAudioComponent=new AudioComponent();
        mPreviewFrameCallback = new PreviewFrameCallback();
        initCamera();
        //VideoComponent must init after initCamera
        mVideoComponent.init();
        mAudioComponent.init();
        mVideoComponent.setEncodedDataCallback(this);
        mAudioComponent.setEncodedDataCallback(this);
        mFlvPacker = new FlvPacker();
        mFlvPacker.initVideoParams(mVideoComponent.getWidth(), mVideoComponent.getHeight(), mVideoComponent.getFrameRate());
        mFlvPacker.initAudioParams(mAudioComponent.getAudioSampleRate(),
                mAudioComponent.getAudioFormat()*8,mAudioComponent.getAudioChanelCount()==2);
        mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
            @Override
            synchronized public void onPacket(byte[] data, int packetType) {
                IOUtils.write(mOutStream, data, 0, data.length);
                LogUtils.w("flv输出 type:"+packetType+",length:"+data.length);
//                if(packetType==3||packetType==2){
//                    LogUtils.w("firstVideo:"+ BytesHexStrTranslate.bytesToHex(data));
//                }
            }
        });
    }

    @Override
    public void onAudioEncodedCallback(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if(mFlvPacker!=null) {
            mFlvPacker.onAudioData(byteBuffer, bufferInfo);
        }
    }

    @Override
    public void onVideoEncodedCallback(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if(mFlvPacker!=null) {
            mFlvPacker.onVideoData(byteBuffer, bufferInfo);
        }
    }

    private void initCamera(){
        CameraInterface.getInstance().openCamera(0);
        Camera.Parameters params = CameraInterface.getInstance().getParams();
        //查找合适的预览尺寸
        Camera.Size size= mVideoComponent.getSupportPreviewSize(params,SUGGEST_PREVIEW_WIDTH);
        if(size==null){
            throw new RuntimeException("not found support preview size");
        }
        //设置视频尺寸
        mVideoComponent.setWidth(size.width);
        mVideoComponent.setHeight(size.height);
        params.setPictureFormat(ImageFormat.JPEG);
        params.setPreviewFormat(mVideoComponent.getSupportPreviewColorFormat(params));
//        params.setPictureSize(videoWidth, videoHeight);
        params.setPreviewSize(mVideoComponent.getWidth(), mVideoComponent.getHeight());
        params.setPreviewFpsRange(15000, 20000);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        CameraInterface.getInstance().adjustOrientation(this, new CameraInterface.OnOrientationChangeListener() {
            @Override
            public void onChange(int degree) {
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) sv.getLayoutParams();
                LogUtils.d(PhoneUtils.getWidth() + " " + PhoneUtils.getHeight());
                if (degree == 90) {
                    lp.height = PhoneUtils.getWidth() * mVideoComponent.getWidth() / mVideoComponent.getHeight();
                } else {
                    lp.height = PhoneUtils.getWidth() * mVideoComponent.getWidth() / mVideoComponent.getHeight();
                }
                sv.setLayoutParams(lp);
            }
        });
        CameraInterface.getInstance().resetParams(params);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraInterface.getInstance().releaseCamera();
        mAudioComponent.setEncodedDataCallback(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHolder != null) {
            CameraInterface.getInstance().startPreview(mHolder, mPreviewFrameCallback);
        }
        mAudioComponent.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAudioComponent.stop();
        CameraInterface.getInstance().stopPreview();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mFlvPacker.start();
        mOutStream = IOUtils.open(FileUtil.getMainDir() + File.separator + "/VideoCompound.flv", false);
        CameraInterface.getInstance().startPreview(mHolder, mPreviewFrameCallback);

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mFlvPacker.stop();
        CameraInterface.getInstance().stopPreview();
        CameraInterface.getInstance().releaseCamera();
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
                    mVideoComponent.encode(data);
                    LogUtils.w("编码第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                }
            });
            LogUtils.v("采集第:" + (++count) + "帧，距上一帧间隔时间:"
                    + (endTime - previewTime) + "  " + Thread.currentThread().getName());
            previewTime = endTime;
        }
    }


}
