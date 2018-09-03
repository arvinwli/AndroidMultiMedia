package com.wangheart.rtmpfile.video;

/**
 * @author Arvin
 * @date 2018/9/3
 * @e-mail arvinli@pacewear.com
 * @description
 */
public class AudioConfig {
    private int audioFormat;
    private int channelCount;
    private int sampleFormat;

    public int getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(int audioFormat) {
        this.audioFormat = audioFormat;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public int getSampleFormat() {
        return sampleFormat;
    }

    public void setSampleFormat(int sampleFormat) {
        this.sampleFormat = sampleFormat;
    }
}
