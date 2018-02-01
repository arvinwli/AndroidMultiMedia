package com.wangheart.rtmpfile.camera;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.view.SurfaceHolder;

import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.ImageUtil;
import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;

import java.io.IOException;

/**
 * Author : eric
 * CreateDate : 2017/11/6  10:57
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :  摄像头操作类
 * Modified :
 */
public class CameraInterface {
    private Camera mCamera;
    private boolean isPreviewing = false;
    private static CameraInterface mCameraInterface;


    private CameraInterface() {

    }

    public static synchronized CameraInterface getInstance() {
        if (mCameraInterface == null) {
            mCameraInterface = new CameraInterface();
        }
        return mCameraInterface;
    }

    /**
     * 打开Camera
     */
    public boolean openCamera(int cameraId) {
        LogUtils.d("Camera open...");
        if (cameraId < 0 || cameraId >= Camera.getNumberOfCameras()) {
            LogUtils.e("cameraId is out of range");
            return false;
        }
        if (mCamera != null) {
            LogUtils.e("Camera is using...");
            stopPreview();
            releaseCamera();
        }
        mCamera = Camera.open(cameraId);
        LogUtils.d("Camera open over....");
        return true;
    }

    public Camera.Parameters getParams() {
        if (mCamera != null)
            return mCamera.getParameters();
        else return null;
    }

    public void resetParams(Camera.Parameters param) {
        mCamera.setParameters(param);
    }

    public void setOrientation(int degree) {
        if (mCamera != null)
            mCamera.setDisplayOrientation(degree);
    }

    public void adjustOrientation(Activity activity, OnOrientationChangeListener listener) {
        int deviceDegree = PhoneUtils.getDisplayRotation(activity);
        LogUtils.d(" " + deviceDegree);
        int degree = 0;
        switch (deviceDegree) {
            case 0:
                degree = 90;
                break;
            case 90:
                degree = 0;
                break;
            case 180:
                degree = 0;
                break;
            case 270:
                degree = 180;
                break;
        }
        setOrientation(degree);
        if (listener != null) {
            listener.onChange(degree);
        }
    }

    public static interface OnOrientationChangeListener {
        void onChange(int degree);
    }

    /**
     * 使用Surfaceview开启预览
     *
     * @param holder
     */
    public void startPreview(SurfaceHolder holder, Camera.PreviewCallback cb) {
        LogUtils.d("doStartPreview...");
        stopPreview();
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                if (cb != null) {
                    mCamera.setPreviewCallback(cb);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            preview();
        }
    }


    public void followScreenOrientation(Context context) {
        if (mCamera == null)
            return;
        final int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mCamera.setDisplayOrientation(90);
        }
    }

    /**
     * 使用TextureView预览Camera
     *
     * @param surface
     */
    public synchronized void startPreview(SurfaceTexture surface) {
        LogUtils.d("doStartPreview...");
        stopPreview();
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surface);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            preview();
        }
    }


    private synchronized void preview() {
        mCamera.startPreview();//开启预览
        isPreviewing = true;
        Camera.Parameters mParams = mCamera.getParameters(); //重新get一次
        LogUtils.d("最终设置:PreviewSize--With = " + mParams.getPreviewSize().width
                + "Height = " + mParams.getPreviewSize().height);
        LogUtils.d("最终设置:PictureSize--With = " + mParams.getPictureSize().width
                + "Height = " + mParams.getPictureSize().height);
    }


    public synchronized void stopPreview() {
        if (mCamera != null && isPreviewing) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            isPreviewing = false;
        }
    }

    /**
     * 停止预览，释放Camera
     */
    public synchronized void releaseCamera() {
        if (null != mCamera) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 拍照
     */
    public void takePicture() {
        if (isPreviewing && (mCamera != null)) {
            mCamera.takePicture(mShutterCallback, null, mJpegPictureCallback);
        }
    }

    public boolean isPreviewing() {
        return isPreviewing;
    }


    public Camera getCamera() {
        return mCamera;
    }

    /*为了实现拍照的快门声音及拍照保存照片需要下面三个回调变量*/
    ShutterCallback mShutterCallback = new ShutterCallback()
            //快门按下的回调，在这里我们可以设置类似播放“咔嚓”声之类的操作。默认的就是咔嚓。
    {
        public void onShutter() {
            // TODO Auto-generated method stub
            LogUtils.d("myShutterCallback:onShutter...");
        }
    };
    PictureCallback mRawCallback = new PictureCallback()
            // 拍摄的未压缩原数据的回调,可以为null
    {

        public void onPictureTaken(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            LogUtils.d("myRawCallback:onPictureTaken...");

        }
    };
    PictureCallback mJpegPictureCallback = new PictureCallback()
            //对jpeg图像数据的回调,最重要的一个回调
    {
        public void onPictureTaken(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            LogUtils.d("myJpegCallback:onPictureTaken...");
            Bitmap b = null;
            if (null != data) {
                b = BitmapFactory.decodeByteArray(data, 0, data.length);//data是字节数据，将其解析成位图
                mCamera.stopPreview();
                isPreviewing = false;
            }
            //保存图片到sdcard
            if (null != b) {
                //设置FOCUS_MODE_CONTINUOUS_VIDEO)之后，myParam.set("rotation", 90)失效。
                //图片竟然不能旋转了，故这里要旋转下
                Bitmap rotaBitmap = ImageUtil.getRotateBitmap(b, 90.0f);
                FileUtil.saveBitmap(rotaBitmap);
            }
            //再次进入预览
            mCamera.startPreview();
            isPreviewing = true;
        }
    };


}
