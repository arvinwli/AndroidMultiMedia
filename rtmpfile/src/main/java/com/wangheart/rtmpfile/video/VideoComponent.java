package com.wangheart.rtmpfile.video;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import com.wangheart.rtmpfile.utils.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * @author Arvin
 * @date 2018/8/22
 * @e-mail ericli_wang@163.com
 * @description
 */
public class VideoComponent {
    private final String TAG="VideoComponent";
    private int previewColorFormat;
    private int mediaCodecFormat;
    private int width;
    private int height;
    private int mFrameRate = 15;
    private static final String VCODEC_MIME = "video/avc";
    private MediaCodec mMediaCodec;
    private EncodedDataCallback mEncodedDataCallback;
    private boolean isCodecStart=false;

    public VideoComponent() {
    }

    public void config(VideoConfig cameraConfig) {
        this.width=cameraConfig.getPreviewWidth();
        this.height=cameraConfig.getPreviewHeight();
        this.previewColorFormat=cameraConfig.getPreviewColorFormat();
        this.mFrameRate=cameraConfig.getFrameRate();
        initMediaCodec();
        LogUtils.d(TAG,"Video Config >>> CodecColorFormat:"+mediaCodecFormat+",PreviewColorFormat:"+previewColorFormat+
        ",FrameRate:"+mFrameRate+",width:"+width+",height:"+height);
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    public void setEncodedDataCallback(EncodedDataCallback mEncodedDataCallback) {
        this.mEncodedDataCallback = mEncodedDataCallback;
    }

    private void initMediaCodec() {
        LogUtils.d( "initMediaCodec");
        int bitrate = 2 * width * height * mFrameRate / 20;
        try {
            MediaCodecInfo mediaCodecInfo = getSupportMediaCodecInfo(VCODEC_MIME);
            if (mediaCodecInfo == null) {
                throw new RuntimeException("mediaCodecInfo is Empty");
            }
            //获取编码器
            mMediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(VCODEC_MIME, width, height);
            //码率
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            //帧率
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            //编码器图像格式
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    getSupportMediaCodecColorFormat(mediaCodecInfo));
            //关键帧间隔时间设置
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start(){
        if(mMediaCodec!=null){
            isCodecStart=true;
            LogUtils.d( "mMediaCodec.start()");
            mMediaCodec.start();
        }
    }

    public void stop(){
        if(mMediaCodec!=null){
            LogUtils.d( "mMediaCodec.stop()");
            mMediaCodec.stop();
            isCodecStart=false;
        }
    }


    /**
     * 编码
     * @param bufSou
     */
    public void encode(byte[] bufSou) {
        if(!isCodecStart)
            return;
        //编码格式转换
        LogUtils.d( "encode size:"+bufSou.length);
        byte[] buf = convert(bufSou);

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
                    if (mEncodedDataCallback != null)
                        mEncodedDataCallback.onVideoEncodedCallback(outputBuffer, bufferInfo);
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
        this.previewColorFormat = supportColorFormat;
        LogUtils.d("PreviewColorFormat:" + previewColorFormat);
        return this.previewColorFormat;
    }

    /**
     * 先获取预览图像的格式，再根据预览图像格式去查找合适的编码图像格式
     *
     * @param mediaCodecInfo
     * @return
     */
    public int getSupportMediaCodecColorFormat(MediaCodecInfo mediaCodecInfo) {
        MediaCodecInfo.CodecCapabilities capabilities = mediaCodecInfo.getCapabilitiesForType("video/avc");
        LogUtils.d("CodecCapabilities length:" + capabilities.colorFormats.length + " " + Arrays.toString(capabilities.colorFormats));
        if (this.previewColorFormat <= 0) {
            throw new RuntimeException("please getSupportedPreviewFormats first");
        }
        int supportColorFormat = 0;
        for (int colorFormat : capabilities.colorFormats) {
            if (previewColorFormat == ImageFormat.NV21 && colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                supportColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                break;
            }
            if (previewColorFormat == ImageFormat.YV12 && colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                supportColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                break;
            }
        }
        if (supportColorFormat == 0) {
            throw new RuntimeException("not find support MediaCodec color format ");
        }
        this.mediaCodecFormat = supportColorFormat;
        LogUtils.d("MediaCodecFormat:" + mediaCodecFormat);
        return this.mediaCodecFormat;
    }


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
        this.width = chooseSize.width;
        this.height = chooseSize.height;
        LogUtils.d("choose size:" + this.width + "*" + this.height);
        return chooseSize;
    }

    private boolean equalRate(Camera.Size s, float rate) {
        float r = (float) (s.width) / (float) (s.height);
        return Math.abs(r - rate) <= 0.2;
    }

    /**
     * 获取编码器信息
     * @param mimeType 编码器类型名称。如：video/avc
     * @return
     */
    public MediaCodecInfo getSupportMediaCodecInfo(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            //是否是编码器
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            LogUtils.d("SupportType:" + Arrays.toString(types));
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


    /**
     * 图像格式转换，将获取的原始帧图像格式转换成编码器支持的格式
     * 目前支持 NV21->COLOR_FormatYUV420SemiPlanar 或YV12COLOR_FormatYUV420Planar
     * 格式示例：为了这个转换的效率，这里尽量按上面的选择上面的格式进行匹配
     * NV21                           YYYYYYYY VU VU
     * COLOR_FormatYUV420SemiPlanar   YYYYYYYY UV UV
     * ImageFormat.YV12               YYYYYYYY VV UU
     * COLOR_FormatYUV420Planar       YYYYYYYY UU VV
     * @param buffer 原始采集到的一帧数据
     * @return 编码器支持的格式
     */
    public byte[] convert(byte[] buffer) {
        //yuv420的带下比例是  Y:U:V=4:1:1  这里不要被420所误导
        int ySize = buffer.length * 2 / 3;
        int uSize = ySize / 4;
        int vSize = ySize / 4;
        long startTime = System.currentTimeMillis();
        if (previewColorFormat == ImageFormat.NV21 && mediaCodecFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            for (int i = ySize; i < buffer.length; i += 2) {
                byte temp = buffer[i];
                buffer[i] = buffer[i + 1];
                buffer[i + 1] = temp;
            }
        } else if (previewColorFormat == ImageFormat.YV12 && mediaCodecFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            //YV12数据转化成COLOR_FormatYUV420Planar
            for (int i = ySize; i < ySize + uSize; i++) {
                byte temp = buffer[i];
                buffer[i] = buffer[i + uSize];
                buffer[i + uSize] = temp;
//            char x = 128;
//            buf[i] = (byte) x;
            }
        } else {
            throw new RuntimeException("sorry.you must convert by yourself");
        }
//        LogUtils.d("convert use time:" + (System.currentTimeMillis()-startTime) + " , size:" + buffer.length);
        return buffer;
    }
}
