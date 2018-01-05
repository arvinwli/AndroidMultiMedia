package com.wangheart.rtmpfile;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;

import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

/**
 * Author : eric
 * CreateDate : 2018/1/5  14:01
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class AudioRecordActivity extends Activity {
    private AudioRecord mAudioRecord;
    private int aSampleRate;
    private int aChanelCount;
    private byte[] aBuffer;
    private boolean aLoop;
    private MediaCodec aencoder;
    private long presentationTimeUs;
    private Thread recordThread;
    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record);
    }

    public void btnStart(View view) {
        initAudioDevice();
        try {
            aencoder = initAudioEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("audio encoder init fail");
        }
        //开启录音
        aLoop = true;
        recordThread = new Thread(fetchAudioRunnable());

        presentationTimeUs = new Date().getTime() * 1000;
        mAudioRecord.startRecording();
        if (aencoder != null) {
            aencoder.start();
        }
        recordThread.start();
    }

    public void btnStop(View view) {
        aLoop = false;
        mAudioRecord.stop();
        aencoder.stop();
    }

    private Runnable fetchAudioRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                fetchPcmFromDevice();
            }
        };
    }

    private void initAudioDevice() {
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {
            //编码制式
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            // stereo 立体声，
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int buffsize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    audioFormat, buffsize);
            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                continue;
            }
            aSampleRate = sampleRate;
            aChanelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
            aBuffer = new byte[Math.min(4096, buffsize)];
            LogUtils.w(aSampleRate + " " + aChanelCount);
        }
    }

    private MediaCodec initAudioEncoder() throws IOException {
        MediaCodec aencoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                aSampleRate, aChanelCount);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * 30);
        aencoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return aencoder;
    }

    private void fetchPcmFromDevice() {
        LogUtils.w("录音线程开始");
        while (aLoop && mAudioRecord != null && !Thread.interrupted()) {
            int size = mAudioRecord.read(aBuffer, 0, aBuffer.length);
            if (size < 0) {
                LogUtils.d("audio ignore ,no data to read");
                break;
            }
            if (aLoop) {
                byte[] audio = new byte[size];
                System.arraycopy(aBuffer, 0, audio, 0, size);
                LogUtils.d("length:" + audio.length);
//                onGetPcmFrame(audio);
            }
        }
    }

    private void onGetPcmFrame(byte[] data) {
        ByteBuffer[] inputBuffers = aencoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aencoder.getOutputBuffers();
        int inputBufferId = aencoder.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            ByteBuffer bb = inputBuffers[inputBufferId];
            bb.clear();
            bb.put(data, 0, data.length);
            long pts = new Date().getTime() * 1000 - presentationTimeUs;
            aencoder.queueInputBuffer(inputBufferId, 0, data.length, pts, 0);
        }

        for (; ; ) {
            int outputBufferId = aencoder.dequeueOutputBuffer(aBufferInfo, 0);
            if (outputBufferId >= 0) {
                // outputBuffers[outputBufferId] is ready to be processed or rendered.
                ByteBuffer bb = outputBuffers[outputBufferId];
//                onEncodeAacFrame(bb, aBufferInfo);
                aencoder.releaseOutputBuffer(outputBufferId, false);
            }
            if (outputBufferId < 0) {
                break;
            }
        }
    }
}
