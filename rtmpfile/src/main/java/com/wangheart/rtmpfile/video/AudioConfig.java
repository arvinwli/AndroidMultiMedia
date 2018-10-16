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
    private int sampleRate;
    private int bufferSize;

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

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
}
