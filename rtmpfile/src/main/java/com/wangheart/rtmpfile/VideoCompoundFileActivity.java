package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import com.wangheart.rtmpfile.device.AudioRecordController;
import com.wangheart.rtmpfile.device.CameraController;
import com.wangheart.rtmpfile.flv.FlvPacker;
import com.wangheart.rtmpfile.flv.Packer;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;
import com.wangheart.rtmpfile.video.AudioComponent;
import com.wangheart.rtmpfile.video.AudioConfig;
import com.wangheart.rtmpfile.video.SourceDataCallback;
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
 * Desc :  音视频合成。采用Android硬编码方式，编码后合成到FLV文件
 * Modified :
 */

public class VideoCompoundFileActivity extends Activity implements SurfaceHolder.Callback, EncodedDataCallback, SourceDataCallback {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private MySurfaceView sv;
    //建议的视频宽度，不超过这个宽度，自动寻找4：3的尺寸
    private final int SUGGEST_PREVIEW_WIDTH = 640;
    private SurfaceHolder mHolder;
    //每帧开始编码时间
    long mVideoencodeTime = 0;
    long mAudioencodeTime = 0;
    private FlvPacker mFlvPacker;
    private OutputStream mOutStream;
    //视频编码组件封装
    private VideoComponent mVideoComponent;
    private AudioComponent mAudioComponent;
    private HandlerThread mPackageThread;
    private Handler mPackageHandler;
    private final String TAG = "VideoCompoundFileActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_compound_file);
        LogUtils.d(TAG, "onCreate");
        init();
    }

    private void init() {
        sv = findViewById(R.id.sv);
        mPackageThread = new HandlerThread("Package-Thread");
        mPackageThread.start();
        mPackageHandler = new Handler(mPackageThread.getLooper());

        mVideoComponent = new VideoComponent();
        mAudioComponent = new AudioComponent(AudioRecordController.MAX_BUFFER_SIZE);
        //初始化摄像头
        VideoConfig cameraConfig = initCamera();
        //初始化录音设备
        AudioConfig audioConfig = initAudioRecord();
        if (cameraConfig == null) {
            LogUtils.e(TAG, "VideoConfig is null");
            return;
        }
        if (audioConfig == null) {
            LogUtils.e(TAG, "AudioConfig is null");
            return;
        }
        //配置视频组件
        mVideoComponent.config(cameraConfig);
        //配置音频组件
        mAudioComponent.config(audioConfig);
        //设置编码回调
        mVideoComponent.setEncodedDataCallback(this);
        mAudioComponent.setEncodedDataCallback(this);
        //初始化并设置flv打包参数
        mFlvPacker = new FlvPacker();
        //设置FLV打包器参数
        mFlvPacker.initVideoParams(mVideoComponent.getWidth(), mVideoComponent.getHeight(), mVideoComponent.getFrameRate());
        mFlvPacker.initAudioParams(mAudioComponent.getAudioSampleRate(),
                mAudioComponent.getAudioChanelCount() * 8, mAudioComponent.getAudioChanelCount() == 2);
        //FLV封装数据回调
        mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
            @Override
            synchronized public void onPacket(final byte[] data, final int packetType) {
//                mPackageHandler.post(new Runnable() {
//                    @Override
//                    public void run() {
                IOUtils.write(mOutStream, data, 0, data.length);
                LogUtils.w("flv输出 type:" + packetType + ",length:" + data.length);
//                if(packetType==3||packetType==2){
//                    LogUtils.w("firstVideo:"+ BytesHexStrTranslate.bytesToHex(data));
//                }
//                    }
//                });
            }
        });
    }

    //
    //===================编码后的数据回调=======================
    //
    @Override
    public void onAudioEncodedCallback(final ByteBuffer byteBuffer, final MediaCodec.BufferInfo bufferInfo) {
//        if(mFlvPacker!=null) {
//            mPackageHandler.post(new Runnable() {
//                @Override
//                public void run() {
        mFlvPacker.onAudioData(byteBuffer, bufferInfo);
//                }
//            });
//        }
    }

    @Override
    public void onVideoEncodedCallback(final ByteBuffer byteBuffer, final MediaCodec.BufferInfo bufferInfo) {
//        if(mFlvPacker!=null) {
//            mPackageHandler.post(new Runnable() {
//                @Override
//                public void run() {
        mFlvPacker.onVideoData(byteBuffer, bufferInfo);
//                }
//            });
//        }
    }

    //
    // ===================采集原始数据回调=======================
    //
    @Override
    public void onAudioSourceDataCallback(final byte[] data, final int index) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                mAudioencodeTime = System.currentTimeMillis();
                mAudioComponent.putData(data);
                LogUtils.w("编码第:" + (index) + "帧，size:" + data.length + "耗时:" + (System.currentTimeMillis() - mAudioencodeTime));
            }
        });
    }

    @Override
    public void onVideoSourceDataCallback(final byte[] data, final int index) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                mVideoencodeTime = System.currentTimeMillis();
                mVideoComponent.encode(data);
                LogUtils.w("编码第:" + (index) + "帧，size:" + data.length + "耗时:" + (System.currentTimeMillis() - mVideoencodeTime));
            }
        });
    }


    /**
     * 初始胡摄像头
     *
     * @return
     */
    private VideoConfig initCamera() {
        VideoConfig cameraConfig = new VideoConfig();
        CameraController.getInstance().open(0);
        Camera.Parameters params = CameraController.getInstance().getParams();
        //查找合适的预览尺寸
        Camera.Size size = CameraController.getInstance().getSupportPreviewSize(params, SUGGEST_PREVIEW_WIDTH);
        if (size == null) {
            LogUtils.e("getSupportPreviewSize failed");
            return null;
        }
        final int width = size.width;
        final int height = size.height;
        //查找合适的预览图像格式
        final int previewColorFormat = CameraController.getInstance().getSupportPreviewColorFormat(params);
        if (previewColorFormat <= 0) {
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
                    lp.height = PhoneUtils.getWidth() * width / height;
                } else {
                    lp.height = PhoneUtils.getWidth() * width / height;
                }
                sv.setLayoutParams(lp);
            }
        });
        CameraController.getInstance().resetParams(params);
        CameraController.getInstance().setCallback(this);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
        cameraConfig.setPreviewWidth(width);
        cameraConfig.setPreviewHeight(height);
        cameraConfig.setPreviewColorFormat(previewColorFormat);
        cameraConfig.setFrameRate(15);
        return cameraConfig;
    }

    /**
     * 初始话录音设备
     *
     * @return
     */
    private AudioConfig initAudioRecord() {
        AudioRecordController.getInstance().init();
        AudioRecordController.getInstance().setCallback(this);
        return AudioRecordController.getInstance().getAudioConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.d(TAG, "onDestroy");
        CameraController.getInstance().close();
        CameraController.getInstance().setCallback(null);
        AudioRecordController.getInstance().setCallback(null);
        AudioRecordController.getInstance().release();
        mAudioComponent.setEncodedDataCallback(null);
        mVideoComponent.setEncodedDataCallback(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.d(TAG, "onResume");
        if (mHolder != null) {
            CameraController.getInstance().startPreview(mHolder);
        }
        AudioRecordController.getInstance().start();
        mVideoComponent.start();
        mAudioComponent.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.d(TAG, "onPause");
        CameraController.getInstance().stopPreview();
        AudioRecordController.getInstance().stop();
        mVideoComponent.stop();
        mAudioComponent.stop();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtils.d(TAG, "surfaceCreated");
        mFlvPacker.start();
        mOutStream = IOUtils.open(FileUtil.getMainDir() + File.separator + "/VideoCompound.flv", false);
        CameraController.getInstance().startPreview(mHolder);

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtils.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.d(TAG, "surfaceDestroyed");
        mFlvPacker.stop();
        CameraController.getInstance().stopPreview();
        CameraController.getInstance().close();
        IOUtils.close(mOutStream);
    }
}
