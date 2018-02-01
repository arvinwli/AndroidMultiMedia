package com.wangheart.rtmpfile;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;

import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Author : eric
 * CreateDate : 2018/1/5  14:01
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class AudioRecordMediaCodecActivity extends Activity {
    private AudioRecord mAudioRecord;
    private int mAudioSampleRate;
    private int mAudioChanelCount;
    private byte[] mAudioBuffer;
    private MediaCodec mAudioEncoder;
    private long presentationTimeUs;
    private Thread mRecordThread;
    private Thread mEncodeThread;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;
    private MediaCodec.BufferInfo mAudioEncodeBufferInfo;
    private int mSampleRateType;
    private BufferedOutputStream mAudioBos;
    private ArrayBlockingQueue<byte[]> queue;
    private boolean isRecord = false;
    private int MAX_BUFFER_SIZE = 8192;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record);
    }

    public void btnStart(View view) {
        initAudioDevice();
        try {
            mAudioEncoder = initAudioEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("audio encoder init fail");
        }
        //开启录音
        mRecordThread = new Thread(fetchAudioRunnable());
        try {
            mAudioBos = new BufferedOutputStream(new FileOutputStream(new File(FileUtil.getMainDir(), "record.aac")), 200 * 1024);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        presentationTimeUs = new Date().getTime() * 1000;
        mAudioRecord.startRecording();
        queue = new ArrayBlockingQueue<byte[]>(10);
        isRecord = true;
        if (mAudioEncoder != null) {
            mAudioEncoder.start();
            encodeInputBuffers = mAudioEncoder.getInputBuffers();
            encodeOutputBuffers = mAudioEncoder.getOutputBuffers();
            mAudioEncodeBufferInfo = new MediaCodec.BufferInfo();
            mEncodeThread = new Thread(new EncodeRunnable());
            mEncodeThread.start();
        }
        mRecordThread.start();
    }

    public void btnStop(View view) {
        isRecord = false;
//        release();
    }

    private Runnable fetchAudioRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                fetchPcmFromDevice();
            }
        };
    }

    /**
     * 初始化AudioRecord
     */
    private void initAudioDevice() {
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {
            //编码制式
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            // stereo 立体声，
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int buffsize =2* AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    audioFormat, buffsize);
            if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED&&buffsize<=MAX_BUFFER_SIZE) {
                mAudioSampleRate = sampleRate;
                mAudioChanelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
                mAudioBuffer = new byte[buffsize];
                mSampleRateType = ADTSUtils.getSampleRateType(sampleRate);
                LogUtils.w("编码器参数:" + mAudioSampleRate + " " + mSampleRateType + " " + mAudioChanelCount + " " + buffsize);
                break;
            }
        }
    }

    /**
     * 初始化编码器
     * @return
     * @throws IOException
     */
    private MediaCodec initAudioEncoder() throws IOException {
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                mAudioSampleRate, mAudioChanelCount);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_BUFFER_SIZE);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * 30);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return encoder;
    }

    /**
     * 采集音频数据
     */
    private void fetchPcmFromDevice() {
        LogUtils.w("录音线程开始");
        while (isRecord && mAudioRecord != null && !Thread.interrupted()) {
            int size = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
            if (size < 0) {
                LogUtils.w("audio ignore ,no data to read");
                break;
            }
            if (isRecord) {
                byte[] audio = new byte[size];
                System.arraycopy(mAudioBuffer, 0, audio, 0, size);
                LogUtils.v("采集到数据:" + audio.length);
                putPCMData(audio);
            }
        }
    }

    /**
     * 将PCM数据存入队列
     *
     * @param pcmChunk PCM数据块
     */
    private void putPCMData(byte[] pcmChunk) {
        try {
            queue.put(pcmChunk);
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

    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.w("编码线程开始");
            while (isRecord || !queue.isEmpty())
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
        byte[] chunkAudio;
        int outBitSize;
        int outPacketSize;
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
            LogUtils.d("开始编码: ");
            mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, pts, 0);//通知编码器 编码
        }

        outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);//同解码器
        while (outputIndex >= 0) {//同解码器
            outBitSize = mAudioEncodeBufferInfo.size;
            outPacketSize = outBitSize + 7;//7为ADTS头部的大小
            outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
            outputBuffer.position(mAudioEncodeBufferInfo.offset);
            outputBuffer.limit(mAudioEncodeBufferInfo.offset + outBitSize);
            chunkAudio = new byte[outPacketSize];
            ADTSUtils.addADTStoPacket(mSampleRateType, chunkAudio, outPacketSize);//添加ADTS 代码后面会贴上
            outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
            outputBuffer.position(mAudioEncodeBufferInfo.offset);
            try {
                LogUtils.d("接受编码后数据 " + chunkAudio.length);
                mAudioBos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAudioEncoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        try {
            if (mAudioBos != null) {
                mAudioBos.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mAudioBos != null) {
                try {
                    mAudioBos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mAudioBos = null;
                }
            }
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }
        LogUtils.w("release");
    }
}
