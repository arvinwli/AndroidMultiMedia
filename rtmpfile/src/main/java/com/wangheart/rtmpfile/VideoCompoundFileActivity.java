package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
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
import com.wangheart.rtmpfile.video.AudioComponent;
import com.wangheart.rtmpfile.video.VideoConfig;
import com.wangheart.rtmpfile.video.EncodedDataCallback;
import com.wangheart.rtmpfile.video.VideoComponent;
import com.wangheart.rtmpfile.view.MySurfaceView;

import org.jetbrains.annotations.Nullable;

import java.io.File;
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
        //初始化摄像头
        VideoConfig cameraConfig=initCamera();
        if(cameraConfig==null){
            return;
        }
        //配置视频组件
        mVideoComponent.config(cameraConfig);
        //配置音频
        mAudioComponent.init();
        //设置编码回调
        mVideoComponent.setEncodedDataCallback(this);
        mAudioComponent.setEncodedDataCallback(this);
        //初始化并设置flv打包参数
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

    private VideoConfig initCamera(){
        VideoConfig cameraConfig=new VideoConfig();
        CameraController.getInstance().open(0);
        Camera.Parameters params = CameraController.getInstance().getParams();
        //查找合适的预览尺寸
        Camera.Size size= CameraController.getInstance().getSupportPreviewSize(params,SUGGEST_PREVIEW_WIDTH);
        if(size==null){
            LogUtils.e("getSupportPreviewSize failed");
            return null;
        }
        final int width=size.width;
        final int height=size.height;
        //查找合适的预览图像格式
        final int previewColorFormat= CameraController.getInstance().getSupportPreviewColorFormat(params);
        if(previewColorFormat<=0){
            LogUtils.e("getSupportPreviewColorFormat failed");
            return null;
        }
        params.setPictureFormat(ImageFormat.JPEG);
        params.setPreviewFormat(previewColorFormat);
//        params.setPictureSize(videoWidth, videoHeight);
        params.setPreviewSize(width, height);
        params.setPreviewFpsRange(15000, 20000);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        //调整布局尺寸
        CameraController.getInstance().adjustOrientation(this, new CameraController.OnOrientationChangeListener() {
            @Override
            public void onChange(int degree) {
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) sv.getLayoutParams();
                LogUtils.d(PhoneUtils.getWidth() + " " + PhoneUtils.getHeight());
                if (degree == 90) {
                    lp.height = PhoneUtils.getWidth() *width / height;
                } else {
                    lp.height = PhoneUtils.getWidth() * width / height;
                }
                sv.setLayoutParams(lp);
            }
        });
        CameraController.getInstance().resetParams(params);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
        cameraConfig.setPreviewWidth(width);
        cameraConfig.setPreviewHeight(height);
        cameraConfig.setPreviewColorFormat(previewColorFormat);
        cameraConfig.setFrameRate(15);
        return cameraConfig;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraController.getInstance().close();
        mAudioComponent.setEncodedDataCallback(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHolder != null) {
            CameraController.getInstance().startPreview(mHolder, mPreviewFrameCallback);
        }
        mVideoComponent.start();
        mAudioComponent.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CameraController.getInstance().stopPreview();
        mVideoComponent.stop();
        mAudioComponent.stop();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mFlvPacker.start();
        mOutStream = IOUtils.open(FileUtil.getMainDir() + File.separator + "/VideoCompound.flv", false);
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
