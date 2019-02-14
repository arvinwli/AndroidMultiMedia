package com.wangheart.rtmpfile.video;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.wangheart.rtmpfile.utils.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author Arvin
 * @date 2018/8/29
 * @e-mail ericli_wang@163.com
 * @description 录音编码组件
 */
public class AudioComponent{
    private int mAudioSampleRate;
    private int mAudioChanelCount;
    private int mAudioBufferSize;
    private MediaCodec mAudioEncoder;
    private long presentationTimeUs;
    private Thread mEncodeThread;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;
    private MediaCodec.BufferInfo mAudioEncodeBufferInfo;
    //    private BufferedOutputStream mAudioBos;
    private ArrayBlockingQueue<byte[]> queue;
    private boolean isEncoding = false;
    private int maxBufferSize = 8192;
    private EncodedDataCallback mEncodedDataCallback;

    public AudioComponent(int maxBufferSize) {
        this.maxBufferSize=maxBufferSize;
    }

    public void config(AudioConfig config) {
        queue = new ArrayBlockingQueue<byte[]>(10);
        mAudioChanelCount=config.getChannelCount();
        mAudioSampleRate=config.getSampleRate();
        mAudioBufferSize=config.getBufferSize();

        initAudioEncoder();
    }

    public int getAudioSampleRate() {
        return mAudioSampleRate;
    }

    public int getAudioChanelCount() {
        return mAudioChanelCount;
    }


    public void setEncodedDataCallback(EncodedDataCallback mEncodedDataCallback) {
        this.mEncodedDataCallback = mEncodedDataCallback;
    }


    /**
     * 初始化编码器
     *
     * @return
     * @throws IOException
     */
    private void initAudioEncoder() {
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                mAudioSampleRate, mAudioChanelCount);
        //最大缓冲区代销
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * 30);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    public void start() {
        presentationTimeUs = new Date().getTime() * 1000;
        isEncoding = true;
        if (mAudioEncoder != null) {
            mAudioEncoder.start();
            encodeInputBuffers = mAudioEncoder.getInputBuffers();
            encodeOutputBuffers = mAudioEncoder.getOutputBuffers();
            mAudioEncodeBufferInfo = new MediaCodec.BufferInfo();
            mEncodeThread = new Thread(new EncodeRunnable());
            mEncodeThread.start();
        }
    }

    public void stop() {
        isEncoding = false;
    }

    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.w("编码线程开始");
            while (isEncoding || !queue.isEmpty())
                encodePCM();
            release();
        }
    }

    /**
     * 编码PCM数据 得到MediaFormat.MIMETYPE_AUDIO_AAC格式的音频文件，并保存到
     */
    private void encodePCM() {
        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;
        byte[] chunkPCM;

        chunkPCM = getPCMData();//获取解码器所在线程输出的数据 代码后边会贴上
        if (chunkPCM == null) {
            return;
        }
        inputIndex = mAudioEncoder.dequeueInputBuffer(-1);//同解码器
        if (inputIndex >= 0) {
            inputBuffer = encodeInputBuffers[inputIndex];//同解码器
            inputBuffer.clear();//同解码器
            inputBuffer.limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
            long pts = new Date().getTime() * 1000 - presentationTimeUs;
            LogUtils.d("编码音频: ");
            mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, pts, 0);//通知编码器 编码
        }

        outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);//同解码器
        while (outputIndex >= 0) {//同解码器
            outputBuffer = encodeOutputBuffers[outputIndex];
            if(mEncodedDataCallback!=null){
                mEncodedDataCallback.onAudioEncodedCallback(outputBuffer, mAudioEncodeBufferInfo);
            }
            mAudioEncoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
        }
    }

    /**
     * 将PCM数据存入队列
     *
     * @param pcmData PCM数据块
     */
    public void putData(byte[] pcmData){
        try {
            queue.put(pcmData);
        } catch (InterruptedException e) {
            e.printStackTrace();
            LogUtils.e("queue put error");
        }
    }

    /**
     * 在Container中队列取出PCM数据
     *
     * @return PCM数据块
     */
    private byte[] getPCMData() {
        try {
            if (queue.isEmpty()) {
                return null;
            }
            return queue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        LogUtils.w("release");
    }

}
