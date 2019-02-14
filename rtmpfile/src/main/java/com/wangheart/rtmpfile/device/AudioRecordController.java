package com.wangheart.rtmpfile.device;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.video.AudioConfig;
import com.wangheart.rtmpfile.video.SourceDataCallback;

/**
 * @author Arvin
 * @date 2018/9/3
 * @e-mail arvinli@pacewear.com
 * @description
 */
public class AudioRecordController {
    private static AudioRecordController mInstance;
    private AudioRecord mAudioRecord;
    private boolean isRecording = false;
    private Thread mRecordThread;
    private byte[] mAudioBuffer;
    private SourceDataCallback mCallback;
    private int mBufferSize;
    private final String TAG="AudioRecordController";
    public static int MAX_BUFFER_SIZE = 8192;

    private AudioRecordController() {

    }

    public static AudioRecordController getInstance() {
        if (mInstance == null) {
            synchronized (AudioRecordController.class) {
                if (mInstance == null) {
                    mInstance = new AudioRecordController();
                }
            }
        }
        return mInstance;
    }

    public AudioRecord init() {
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {
            // stereo 立体声，
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            mBufferSize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, mBufferSize);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED&&mBufferSize<=MAX_BUFFER_SIZE) {
                mAudioRecord = audioRecord;
                mAudioBuffer=new byte[mBufferSize];
                return mAudioRecord;
            }
        }
        return null;
    }

    public void setCallback(SourceDataCallback mCallback) {
        this.mCallback = mCallback;
    }

    public AudioConfig getAudioConfig() {
        if (mAudioRecord == null) {
            LogUtils.w(TAG,"mAudioRecord is null");
            return null;
        }
        if(mBufferSize<=0){
            LogUtils.w(TAG,"mBufferSize < 0");
            return null;
        }
        AudioConfig audioConfig = new AudioConfig();
        audioConfig.setAudioFormat(mAudioRecord.getAudioFormat());
        audioConfig.setChannelCount(mAudioRecord.getChannelCount());
        audioConfig.setSampleRate(mAudioRecord.getSampleRate());
        audioConfig.setBufferSize(mBufferSize);
        return audioConfig;
    }

    public void start() {
        if (mAudioRecord == null)
            return;
        LogUtils.d("AudioRecord start");
        isRecording = true;
        mRecordThread = new ReadThread();
        mRecordThread.start();
        mAudioRecord.startRecording();
    }

    public void stop() {
        isRecording = false;
        if(mAudioRecord!=null) {
            LogUtils.d("AudioRecord stop");
            mAudioRecord.stop();
        }
    }

    public void release(){
        if(mAudioRecord!=null){
            mAudioRecord.release();
        }
    }


    private class ReadThread extends Thread {
        int index=0;
        public ReadThread() {
            super("Audio-Read-Thread ");
        }

        @Override
        public void run() {
            super.run();
            LogUtils.d("Audio Record Thread start...");
            while (isRecording && mAudioRecord != null) {
                int size = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
                if (size < 0) {
                    LogUtils.w("AudioRecord read empty");
                    continue;
                }
                if (isRecording) {
                    byte[] audio = new byte[size];
                    System.arraycopy(mAudioBuffer, 0, audio, 0, size);
                    LogUtils.v("采集到数据:" + audio.length);
                    if(mCallback!=null){
                        mCallback.onAudioSourceDataCallback(audio,index);
                    }
                }
                index++;
            }
            LogUtils.d("Audio Record Thread finish...");
        }
    }
}
