package com.wangheart.rtmpfile;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.wangheart.rtmpfile.ffmpeg.FFmpegHandle;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
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

public class CameraActivity extends Activity implements SurfaceHolder.Callback {
    private MySurfaceView sv;
    private int screenWidth = 480;
    private int screenHeight = 320;
    private SurfaceHolder mHolder;
    private Camera mcamera;
    private String ipname;
    boolean isPreview = false; // 是否在浏览中
    private String dir = "CameraDemo1";
    private String url="rtmp://192.168.31.127/live/test";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        File file = new File(Environment.getExternalStorageDirectory(), dir);
        if (file.exists()) {
            file.delete();
        }
        file.mkdirs();
        init();
    }

    private void init() {
        sv = (MySurfaceView) findViewById(R.id.sv);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "CameraDemo1" + File.separator + "test.flv";
        FFmpegHandle.getInstance().initVideo(path);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegHandle.getInstance().close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mcamera == null) {
            mcamera = getCamera();
            if (mHolder != null) {
                setStartPreview(mcamera, mHolder);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        FFmpegHandle.getInstance().flush();
        releaseCamera();
    }

    private final static int MAX_FPS = 15;

    private Camera getCamera() {
        Camera camera;
        try {
            //打开相机，默认为后置，可以根据摄像头ID来指定打开前置还是后置
            camera = Camera.open(1);
            if (camera != null && !isPreview) {
                try {
                    Log.w("eric", "Parameters");
                    Camera.Parameters parameters = camera.getParameters();
                    //对拍照参数进行设置
                    parameters.setPreviewSize(screenWidth, screenHeight); // 设置预览照片的大小
                    parameters.setPreviewFpsRange(5000, 5000);
//                    parameters.setPreviewFrameRate(10);
                    parameters.setPictureFormat(ImageFormat.NV21); // 设置图片格式
                    parameters.setPictureSize(screenWidth, screenHeight); // 设置照片的大小
                    camera.setParameters(parameters);
                    //指定使用哪个SurfaceView来显示预览图片
                    camera.setPreviewDisplay(sv.getHolder()); // 通过SurfaceView显示取景画面
                    camera.setPreviewCallback(new StreamIt(ipname)); // 设置回调的类
                    camera.startPreview(); // 开始预览
                    //Camera.takePicture()方法进行拍照
                    camera.autoFocus(null); // 自动对焦
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isPreview = true;
            }
        } catch (Exception e) {
            camera = null;
            e.printStackTrace();
            Toast.makeText(this, "无法获取前置摄像头", Toast.LENGTH_LONG);
        }
        return camera;
    }

    public static void followScreenOrientation(Context context, Camera camera) {
        final int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            camera.setDisplayOrientation(90);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setStartPreview(mcamera, mHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setStartPreview(mcamera, mHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    /*
    开始预览相机内容
     */
    long encodeStartTime = 0;

    private void setStartPreview(Camera camera, SurfaceHolder holder) {
        try {

            camera.setPreviewDisplay(holder);
            followScreenOrientation(this, camera);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    long startTime = 0;

    private class StreamTask extends AsyncTask<Void, Void, Void> {

        private byte[] mData;

        //构造函数
        StreamTask(byte[] data) {
            this.mData = data;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // TODO Auto-generated method stub
            if (mData != null) {
                encodeStartTime = System.currentTimeMillis();
                FFmpegHandle.getInstance().onFrameCallback(mData);
                long endTime = System.currentTimeMillis();
                Log.d("eric", count++ + "消耗时间:" + (endTime - startTime));
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
    }

    private StreamTask mStreamTask;


    /*
    释放相机资源
     */
    private void releaseCamera() {
        if (mcamera != null) {
            mcamera.setPreviewCallback(null);
            mcamera.stopPreview();
            mcamera.release();
            mcamera = null;
        }
    }


    int count = 0;
    int encodeCount = 0;
    long xStartTime = 0;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private final static int FRAME_PERIOD = (1000 / MAX_FPS); // the frame period

    public class StreamIt implements Camera.PreviewCallback {
        private String ipname;

        public StreamIt(String ipname) {
            this.ipname = ipname;
        }

        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            long endTime = System.currentTimeMillis();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("eric", "=============开始编码");
                        encodeStartTime = System.currentTimeMillis();
                        FFmpegHandle.getInstance().onFrameCallback(data);
                        long endTime = System.currentTimeMillis();
                        Log.d("eric", encodeCount++ + "消耗时间:" + (endTime - startTime));
                    }
                });
//            }
            Log.d("eric", count++ + "间隔时间:" + (endTime - startTime) + "  " + Thread.currentThread().getName());
            startTime = endTime;
        }
    }
}
