package com.wangheart.rtmpfile;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.wangheart.rtmpfile.audio.AudioBuffer;
import com.wangheart.rtmpfile.audio.FFmpegAudioHandle;
import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Author : eric
 * CreateDate : 2018/1/5  14:01
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :  如果需要把采集的原始数据输出到pcm文件，把out相关的注释打开即可
 * Modified :
 */

public class AudioRecordFFmpegActivity extends Activity {
    private TextView tvInfo;
    private AudioRecord mAudioRecord;
    private int mAudioSampleRate;
    private int mAudioChanelCount;
    private byte[] mAudioBuffer;
    private Thread mRecordThread;
    private Thread mEncodeThread;
    private int mSampleRateType;
    private boolean isRecord = false;
    private int MAX_BUFFER_SIZE = 10240;
    private int ret = 0;
    //采集数据并输入到pcm文件。
    private OutputStream out;
    private AudioBuffer audioBuffer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record_ffmpeg);
    }

    public void btnStart(View view) {
//        out = IOUtils.open(FileUtil.getMainDir() + "/AudioRecordFFmpegActivity.pcm", false);
        ret = FFmpegAudioHandle.getInstance().initAudio(FileUtil.getMainDir() + "/record_ffmpeg.aac");
        if (ret < 0) {
            LogUtils.e("initAudio error " + ret);
            return;
        }
        LogUtils.d("ReadSize " + ret);
        audioBuffer = new AudioBuffer(ret);
        if (!initAudioDevice()) {
            LogUtils.e("initAudioDevice failed");
            return;
        }
        isRecord = true;
        //开启录音
        mRecordThread = new Thread(fetchAudioRunnable());
        mEncodeThread = new Thread(new EncodeRunnable());
        mAudioRecord.startRecording();
        mEncodeThread.start();
        mRecordThread.start();
    }

    public void btnStop(View view) {
        isRecord = false;
    }


    /**
     * 编码tdjm.pcm文件为tdjm.aac
     *
     * @param view
     */
    public void btnEncodePcmFile(View view) {
        final String pcmFileName = "tdjm.pcm";
        new Thread(new Runnable() {
            @Override
            public void run() {
                FFmpegAudioHandle.getInstance().encodePcmFile(FileUtil.getMainDir() + "/" + pcmFileName,
//                FFmpegAudioHandle.getInstance().encodePcmFile(FileUtil.getMainDir() + "/AudioRecordFFmpegActivity.pcm",
                        FileUtil.getMainDir() + "/tdjm.aac");
            }
        }).start();
    }

    public void btnTest(View view) {
        final String pcmFileName = "tdjm.pcm";
        final File filePcm = new File(FileUtil.getMainDir(), pcmFileName);
//        final File filePcm = new File(FileUtil.getMainDir(), "AudioRecordFFmpegActivity.pcm");
        if (!filePcm.exists()) {
            LogUtils.d(filePcm + " is not exist");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream in = null;
                int readSize = FFmpegAudioHandle.getInstance().initAudio(FileUtil.getMainDir() + "/tdjm.aac");
                if (readSize <= 0) {
                    LogUtils.e("init audio error ");
                    return;
                }
                LogUtils.d("readSize" + readSize);
                audioBuffer = new AudioBuffer(readSize);
                try {
                    byte[] buff = new byte[readSize];
                    in = new FileInputStream(filePcm);
                    while (in.read(buff) >= readSize) {
                        FFmpegAudioHandle.getInstance().encodeAudio(buff);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.close(in);
                    FFmpegAudioHandle.getInstance().close();
                }
            }
        }).start();
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
     * 初始化AudioRecord  这里测试就直接用44100采样率 双声道  16bit。于FFmpeg编码器重保持一致
     * 当然大家可以根据自己的情况来修改，这里只是抛砖引玉。
     */
    private boolean initAudioDevice() {
        int sampleRate = 44100;
        //编码制式
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        // stereo 立体声，
        int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        int buffsize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                audioFormat, buffsize);
        if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED && buffsize <= MAX_BUFFER_SIZE) {
            mAudioSampleRate = sampleRate;
            mAudioChanelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
            mAudioBuffer = new byte[buffsize];
            mSampleRateType = ADTSUtils.getSampleRateType(sampleRate);
            LogUtils.w("编码器参数:" + mAudioSampleRate + " " + mSampleRateType + " " + mAudioChanelCount + " " + buffsize);
            return true;
        }
        return false;
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
//                IOUtils.write(out, audio, 0, audio.length);
                audioBuffer.put(audio, 0, audio.length);

            }
        }
    }


    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.w("编码线程开始");
            while (isRecord || !audioBuffer.isEmpty())
                encodePCM();
            release();
        }
    }

    private void encodePCM() {
        byte[] chunkPCM = audioBuffer.getFrameBuf();//获取解码器所在线程输出的数据 代码后边会贴上
        if (chunkPCM == null) {
            return;
        }
        FFmpegAudioHandle.getInstance().encodeAudio(chunkPCM);
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
        }
        LogUtils.w("release");
//        IOUtils.close(out);
    }
}
