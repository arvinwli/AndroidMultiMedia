package com.wangheart.rtmpfile.device;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.wangheart.rtmpfile.video.AudioConfig;

/**
 * @author Arvin
 * @date 2018/9/3
 * @e-mail arvinli@pacewear.com
 * @description
 */
public class AudioRecordController {
    private static AudioRecordController mInstance;
    private AudioRecord mAudioRecord;
    private AudioRecordController(){

    }

    public static AudioRecordController getInstance(){
        if(mInstance==null){
            synchronized (AudioRecordController.class){
                if(mInstance==null){
                    mInstance=new AudioRecordController();
                }
            }
        }
        return mInstance;
    }

    public AudioRecord getAudioRecord(){
        int[] sampleRates = {44100, 22050, 16000, 11025};
        for (int sampleRate : sampleRates) {
            // stereo 立体声，
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int buffsize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
           AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, buffsize);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mAudioRecord=audioRecord;
                return mAudioRecord;
            }
        }
        return null;
    }

    public AudioConfig getAudioConfig(){
        if(mAudioRecord==null)
            return null;
        AudioConfig audioConfig=new AudioConfig();
        audioConfig.setAudioFormat(mAudioRecord.getAudioFormat());
        audioConfig.setChannelCount(mAudioRecord.getChannelCount());
        return audioConfig;
    }
}
