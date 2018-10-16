package com.wangheart.rtmpfile.device;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;
import com.wangheart.rtmpfile.video.SourceDataCallback;

import java.io.IOException;
import java.util.List;

/**
 * Author : eric
 * CreateDate : 2017/11/6  10:57
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :  摄像头操作类
 * Modified :
 */
public class CameraController {
    private Camera mCamera;
    private boolean isPreviewing = false;
    private static CameraController mCameraInterface;
    private SourceDataCallback mCallback;


    private CameraController() {

    }

    public static CameraController getInstance() {
        if (mCameraInterface == null) {
            synchronized(CameraController.class){
                if(mCameraInterface==null){
                    mCameraInterface = new CameraController();
                }
            }
        }
        return mCameraInterface;
    }


    public void setCallback(SourceDataCallback mCallback) {
        this.mCallback = mCallback;
    }

    /**
     * 打开Camera
     */
    public boolean open(int cameraId) {
        LogUtils.d("Camera open...");
        if (cameraId < 0 || cameraId >= Camera.getNumberOfCameras()) {
            LogUtils.e("cameraId is out of range");
            return false;
        }
        if (mCamera != null) {
            LogUtils.e("Camera is using...");
//            stopPreview();
            close();
        }
        mCamera = Camera.open(cameraId);
        LogUtils.d("Camera open over....");
        return true;
    }

    public Camera.Parameters getParams() {
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> pictureSize = params.getSupportedPictureSizes();
            if (pictureSize != null) {
                for (Camera.Size size : pictureSize) {
                    LogUtils.d("SupportedPictureSize width:" + size.width + ",height:" + size.height);
                }
            }
            List<Camera.Size> previewSize = params.getSupportedPreviewSizes();
            if (previewSize != null) {
                for (Camera.Size size : previewSize) {
                    LogUtils.d("SupportedPreviewSize width:" + size.width + ",height:" + size.height);
                }
            }
            List<Integer> pictureFormats = params.getSupportedPictureFormats();
            if (previewSize != null) {
                LogUtils.d("SupportedPictureFormats:" + pictureFormats);
            }
            List<Integer> previewFormats = params.getSupportedPreviewFormats();
            if (previewSize != null) {
                LogUtils.d("SupportedPreviewFormats:" + previewFormats);
            }
            return params;
        } else return null;
    }

    /**
     * 寻找合适的预览格式，NV21和YV12。其他格式目前暂时不做支持
     * @param parameters
     * @return
     */
    public int getSupportPreviewColorFormat(Camera.Parameters parameters) {
        if (parameters == null) {
            throw new RuntimeException("Camera.Parameters is null");
        }
        List<Integer> previewSize = parameters.getSupportedPreviewFormats();
        if (previewSize == null) {
            throw new RuntimeException("getSupportedPreviewFormats is null");
        }
        int supportColorFormat = 0;
        for (int colorFormat : previewSize) {
            if (colorFormat == ImageFormat.NV21) {
                supportColorFormat = ImageFormat.NV21;
                break;
            }
            if (colorFormat == ImageFormat.YV12) {
                supportColorFormat = ImageFormat.YV12;
                break;
            }
        }
        if (supportColorFormat == 0) {
            throw new RuntimeException("not find support preview color format ");
        }
        LogUtils.d("PreviewColorFormat:" + supportColorFormat);
        return supportColorFormat;
    }

    /**
     * 找到合适的预览尺寸
     * @param parameters
     * @param width 建议尺寸，不超过width 宽高比为4:3
     * @return
     */
    public Camera.Size getSupportPreviewSize(Camera.Parameters parameters, int width) {
        List<Camera.Size> suppportPreviewSize = parameters.getSupportedPreviewSizes();
        if (suppportPreviewSize == null || suppportPreviewSize.size() == 0) {
            throw new RuntimeException("getSupportedPreviewSizes is empty");
        }
        Camera.Size chooseSize = suppportPreviewSize.get(suppportPreviewSize.size() / 2);
        for (Camera.Size size : suppportPreviewSize) {
            LogUtils.d("w:" + size.width + ",h:" + size.height);
            if (size.width <= width && equalRate(size, 1.33f)) {
                chooseSize = size;
                break;
            }
        }
        LogUtils.d("choose size:" + chooseSize.width + "*" + chooseSize.height);
        return chooseSize;
    }

    private boolean equalRate(Camera.Size s, float rate) {
        float r = (float) (s.width) / (float) (s.height);
        return Math.abs(r - rate) <= 0.2;
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

    public boolean startPreview(SurfaceHolder holder) {
        return startPreview(holder,null);
    }
    /**
     * 使用Surfaceview开启预览
     *
     * @param holder
     */
    public boolean startPreview(SurfaceHolder holder, Camera.PreviewCallback cb) {
        LogUtils.d("doStartPreview...");
        stopPreview();
        if(holder==null){
            LogUtils.d("startPreview failed.SurfaceHolder is null");
            return false;
        }
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(holder);
                if (cb != null) {
                    mCamera.setPreviewCallback(cb);
                }else{
                    mCamera.setPreviewCallback(new PreviewFrameCallback());
                }
            } catch (IOException e) {
                e.printStackTrace();
                LogUtils.d("startPreview failed."+e.getMessage());
                return false;
            }
            preview();
            return true;
        }else{
            LogUtils.d("startPreview failed.Camera not open");
            return false;
        }
    }

    private synchronized void preview() {
        mCamera.startPreview();//开启预览
        isPreviewing = true;
        Camera.Parameters mParams = mCamera.getParameters(); //重新get一次
        LogUtils.d("Camera startPreview Success");
        LogUtils.d("最终设置:PreviewSize--With = " + mParams.getPreviewSize().width
                + "Height = " + mParams.getPreviewSize().height);
        LogUtils.d("最终设置:PictureSize--With = " + mParams.getPictureSize().width
                + "Height = " + mParams.getPictureSize().height);
    }


    public synchronized void stopPreview() {
        if (mCamera != null && isPreviewing) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
        }
        isPreviewing = false;
    }

    /**
     * 停止预览，释放Camera
     */
    public synchronized void close() {
        if(isPreviewing){
            stopPreview();
        }
        if (null != mCamera) {
            mCamera.release();
            mCamera = null;
        }
    }

    public boolean isPreviewing() {
        return isPreviewing;
    }




    private class PreviewFrameCallback implements Camera.PreviewCallback {
        //采集到每帧数据时间
        long lastPreviewTime = 0;
        //采集数量
        int count = 0;
        public PreviewFrameCallback() {

        }
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            long currentTime = System.currentTimeMillis();
            LogUtils.v("采集第:" + (count) + "帧，距上一帧间隔时间:"
                    + (currentTime - lastPreviewTime) + "  " + Thread.currentThread().getName());
            lastPreviewTime = currentTime;
            if(mCallback!=null){
                mCallback.onVideoSourceDataCallback(data,count);
            }
            count++;
//            executor.execute(new Runnable() {
//                @Override
//                public void run() {
//                    encodeTime = System.currentTimeMillis();
//                    mVideoComponent.encode(data);
//                    LogUtils.w("编码第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
//                }
//            });

        }
    }
}
