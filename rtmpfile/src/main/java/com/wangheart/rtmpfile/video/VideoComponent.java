package com.wangheart.rtmpfile.video;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import com.wangheart.rtmpfile.utils.LogUtils;

import java.util.Arrays;
import java.util.List;

/**
 * @author Arvin
 * @date 2018/8/22
 * @e-mail arvinli@pacewear.com
 * @description
 */
public class VideoComponent {
    private int previewColorFormat;
    private int mediaCodecFormat;
    private int width;
    private int height;


    public int getSupportPreviewColorFormat(Camera.Parameters parameters){
        if(parameters==null){
            throw new RuntimeException("Camera.Parameters is null");
        }
        List<Integer> previewSize = parameters.getSupportedPreviewFormats();
        if(previewSize==null){
            throw new RuntimeException("getSupportedPreviewFormats is null");
        }
        int supportColorFormat=0;
        for(int colorFormat:previewSize){
            if (colorFormat == ImageFormat.NV21) {
                supportColorFormat =ImageFormat.NV21;
                break;
            }
            if (colorFormat == ImageFormat.YV12) {
                supportColorFormat = ImageFormat.YV12;
                break;
            }
        }
        if(supportColorFormat==0){
            throw new RuntimeException("not find support preview color format ");
        }
        this.previewColorFormat=supportColorFormat;
        LogUtils.d("PreviewColorFormat:"+previewColorFormat);
        return this.previewColorFormat;
    }

    public int getSupportMediaCodecColorFormat(MediaCodecInfo mediaCodecInfo){
        MediaCodecInfo.CodecCapabilities capabilities = mediaCodecInfo.getCapabilitiesForType("video/avc");
        LogUtils.d("CodecCapabilities length:"+capabilities.colorFormats.length + " " + Arrays.toString(capabilities.colorFormats));
        if(this.previewColorFormat<=0){
            throw new RuntimeException("please getSupportMediaCodecColorFormat first");
        }
        int supportColorFormat=0;
        for(int colorFormat:capabilities.colorFormats) {
            if(previewColorFormat==ImageFormat.NV21&&colorFormat==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar){
                supportColorFormat=MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                break;
            }
            if(previewColorFormat==ImageFormat.YV12&&colorFormat==MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar){
                supportColorFormat=MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                break;
            }
        }
        if(supportColorFormat==0){
            throw new RuntimeException("not find support MediaCodec color format ");
        }
        this.mediaCodecFormat=supportColorFormat;
        LogUtils.d("MediaCodecFormat:"+mediaCodecFormat);
        return this.mediaCodecFormat;
    }


    public Camera.Size getSupportPreviewSize(Camera.Parameters parameters,int width){
        List<Camera.Size> suppportPreviewSize = parameters.getSupportedPreviewSizes();
        if(suppportPreviewSize==null||suppportPreviewSize.size()==0){
            throw new RuntimeException("getSupportedPreviewSizes is empty");
        }
        Camera.Size chooseSize= suppportPreviewSize.get(suppportPreviewSize.size()/2);
        for(Camera.Size size:suppportPreviewSize){
            LogUtils.d("w:"+size.width+",h:"+size.height);
            if(size.width==width){
                chooseSize= size;
                break;
            }
        }
        this.width=chooseSize.width;
        this.height=chooseSize.height;
        LogUtils.d("choose size:"+this.width+"*"+ this.height);
        return chooseSize;
    }

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



    public byte[] convert(byte[] buffer) {
        int ySize=buffer.length*2/3;
        int uSize=ySize/4;
        int vSize=ySize/4;
        long startTime = System.currentTimeMillis();
        if (previewColorFormat == ImageFormat.NV21 && mediaCodecFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            for (int i =ySize; i < buffer.length; i += 2) {
                byte temp = buffer[i];
                buffer[i] = buffer[i+1];
                buffer[i+1] = temp;
            }
        } else if (previewColorFormat == ImageFormat.YV12 && mediaCodecFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            //YV12数据转化成COLOR_FormatYUV420Planar
            for (int i =ySize; i < ySize+uSize; i++) {
                byte temp = buffer[i];
                buffer[i] = buffer[i + uSize];
                buffer[i + uSize] = temp;
//            char x = 128;
//            buf[i] = (byte) x;
            }
        } else {
            throw new RuntimeException("");
        }
        LogUtils.d("convert use time:" + (System.currentTimeMillis()-startTime) + " , size:" + buffer.length);
        return buffer;
    }
}
