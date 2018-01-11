package com.wangheart.rtmpfile.audio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Author : eric
 * CreateDate : 2018/1/4  10:20
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :   音频文件转化为AAC格式文件
 * Modified :
 */

public class AudioCodec {
    private String srcPath;
    private String dstPath;
    private MediaCodec mediaDecode;
    private MediaCodec mediaEncode;
    private MediaExtractor mediaExtractor;
    private ByteBuffer[] decodeInputBuffers;
    private ByteBuffer[] decodeOutputBuffers;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;
    private MediaCodec.BufferInfo decodeBufferInfo;
    private MediaCodec.BufferInfo encodeBufferInfo;
    private BufferedOutputStream bos;
    private OnCompleteListener onCompleteListener;
    private OnProgressListener onProgressListener;
    private long fileTotalSize;
    private long decodeSize;

    private int key_channel_count;
    private int key_bit_rate;
    private int key_sample_rate;
    private int sampleRateType;

    private ArrayBlockingQueue<byte[]> queue;


    public static AudioCodec newInstance() {
        return new AudioCodec();
    }

    /**
     * 设置输入输出文件位置
     *
     * @param srcPath
     * @param dstPath
     */
    public void setIOPath(String srcPath, String dstPath) {
        this.srcPath = srcPath;
        this.dstPath = dstPath;
    }

    /**
     * 此类已经过封装
     * 调用prepare方法 会初始化Decode 、Encode 、输入输出流 等一些列操作
     */
    public void prepare() {

        if (srcPath == null) {
            throw new IllegalArgumentException("srcPath can't be null");
        }

        if (dstPath == null) {
            throw new IllegalArgumentException("dstPath can't be null");
        }

        try {
            bos = new BufferedOutputStream(new FileOutputStream(new File(dstPath)), 200 * 1024);
            File file = new File(srcPath);
            fileTotalSize = file.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        queue = new ArrayBlockingQueue<byte[]>(20);
        initMediaDecode();//解码器
        initAACMediaEncode();//AAC编码器
    }

    /**
     * 初始化解码器
     */
    private void initMediaDecode() {
        try {
            mediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(srcPath);//媒体文件的位置
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {//遍历媒体轨道 此处我们传入的是音频文件，所以也就只有一条轨道
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio")) {//获取音频轨道
                    mediaExtractor.selectTrack(i);//选择此音频轨道
                    LogUtils.d("mime:" + mime);
                    key_channel_count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    key_sample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        key_bit_rate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                    } else {
                        LogUtils.w("MediaFormat not contain KEY_BIT_RATE");
                        key_bit_rate = 128000;
                    }
                    sampleRateType = ADTSUtils.getSampleRateType(key_sample_rate);
                    mediaDecode = MediaCodec.createDecoderByType(mime);//创建Decode解码器
                    mediaDecode.configure(format, null, null, 0);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaDecode == null) {
            LogUtils.e("create mediaDecode failed");
            return;
        }
        mediaDecode.start();//启动MediaCodec ，等待传入数据
        decodeInputBuffers = mediaDecode.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
        decodeOutputBuffers = mediaDecode.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到PCM数据
        decodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
        LogUtils.d("buffers:" + decodeInputBuffers.length);
    }


    /**
     * 初始化AAC编码器
     */
    private void initAACMediaEncode() {
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    key_sample_rate, key_channel_count);//参数对应-> mime type、采样率、声道数
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, key_bit_rate);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);
            LogUtils.w("编码器参数:"+key_bit_rate + " " + key_channel_count + " " + key_sample_rate + " " + sampleRateType);
            mediaEncode = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaEncode == null) {
            LogUtils.e("create mediaEncode failed");
            return;
        }
        mediaEncode.start();
        encodeInputBuffers = mediaEncode.getInputBuffers();
        encodeOutputBuffers = mediaEncode.getOutputBuffers();
        encodeBufferInfo = new MediaCodec.BufferInfo();
    }

    private boolean codeOver = false;

    /**
     * 开始转码
     * 音频数据{@link #srcPath}先解码成PCM  PCM数据在编码成MediaFormat.MIMETYPE_AUDIO_AAC音频格式
     * mp3->PCM->aac
     */
    public void startAsync() {
        LogUtils.w("start");

        new Thread(new DecodeRunnable()).start();
        new Thread(new EncodeRunnable()).start();

    }

    /**
     * 将PCM数据存入队列
     *
     * @param pcmChunk PCM数据块
     */
    private void putPCMData(byte[] pcmChunk) {
        try {
            LogUtils.d("put queue size:" + queue.size());
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


    /**
     * 解码{@link #srcPath}音频文件 得到PCM数据块
     *
     * @return 是否解码完所有数据
     */
    private void srcAudioFormatToPCM() {
        int inputIndex = mediaDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
        if (inputIndex < 0) {
            codeOver = true;
            return;
        }

        ByteBuffer inputBuffer = decodeInputBuffers[inputIndex];//拿到inputBuffer
        inputBuffer.clear();//清空之前传入inputBuffer内的数据
        int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
        if (sampleSize < 0) {//小于0 代表所有数据已读取完成
            codeOver = true;
        } else {
            mediaDecode.queueInputBuffer(inputIndex, 0, sampleSize, 0, 0);//通知MediaDecode解码刚刚传入的数据
            mediaExtractor.advance();//MediaExtractor移动到下一取样处
            decodeSize += sampleSize;
            LogUtils.d("read:" + sampleSize);
            if (onProgressListener != null) {
                onProgressListener.progress(decodeSize, fileTotalSize);
            }
        }
        //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
        //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这 等待
        int outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);

        ByteBuffer outputBuffer;
        byte[] chunkPCM;
        while (outputIndex >= 0) {//每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
            outputBuffer = decodeOutputBuffers[outputIndex];//拿到用于存放PCM数据的Buffer
            chunkPCM = new byte[decodeBufferInfo.size];//BufferInfo内定义了此数据块的大小
            outputBuffer.get(chunkPCM);//将Buffer内的数据取出到字节数组中
            outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据
            putPCMData(chunkPCM);//自己定义的方法，供编码器所在的线程获取数据,下面会贴出代码
            mediaDecode.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
            outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
        }

    }

    /**
     * 编码PCM数据 得到MediaFormat.MIMETYPE_AUDIO_AAC格式的音频文件，并保存到{@link #dstPath}
     */
    private void dstAudioFormatFromPCM() {

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
        inputIndex = mediaEncode.dequeueInputBuffer(-1);//同解码器
        if (inputIndex >= 0) {
            inputBuffer = encodeInputBuffers[inputIndex];//同解码器
            inputBuffer.clear();//同解码器
            inputBuffer.limit(chunkPCM.length);
            inputBuffer.put(chunkPCM);//PCM数据填充给inputBuffer
            mediaEncode.queueInputBuffer(inputIndex, 0, chunkPCM.length, 0, 0);//通知编码器 编码
        }
        outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);//同解码器
        while (outputIndex >= 0) {//同解码器

            outBitSize = encodeBufferInfo.size;
            outPacketSize = outBitSize + 7;//7为ADTS头部的大小
            outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
            outputBuffer.position(encodeBufferInfo.offset);
            outputBuffer.limit(encodeBufferInfo.offset + outBitSize);
            chunkAudio = new byte[outPacketSize];
            ADTSUtils.addADTStoPacket(sampleRateType, chunkAudio, outPacketSize);//添加ADTS
            outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
            outputBuffer.position(encodeBufferInfo.offset);
            try {
                bos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
                LogUtils.d("write " + chunkAudio.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaEncode.releaseOutputBuffer(outputIndex, false);
            outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 10000);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        IOUtils.close(bos);
        if (mediaEncode != null) {
            mediaEncode.stop();
            mediaEncode.release();
            mediaEncode = null;
        }

        if (mediaDecode != null) {
            mediaDecode.stop();
            mediaDecode.release();
            mediaDecode = null;
        }

        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }

        if (onCompleteListener != null) {
            onCompleteListener = null;
        }

        if (onProgressListener != null) {
            onProgressListener = null;
        }
        LogUtils.w("release");
    }

    /**
     * 解码线程
     */
    private class DecodeRunnable implements Runnable {

        @Override
        public void run() {
            while (!codeOver) {
                srcAudioFormatToPCM();
            }
        }
    }

    /**
     * 编码线程
     */
    private class EncodeRunnable implements Runnable {

        @Override
        public void run() {
            long t = System.currentTimeMillis();
            while (!codeOver || !queue.isEmpty()) {
                dstAudioFormatFromPCM();
            }
            if (onCompleteListener != null) {
                onCompleteListener.completed();
            }
            LogUtils.w("size:" + fileTotalSize + " decodeSize:" + decodeSize + "time:" + (System.currentTimeMillis() - t));
        }
    }


    /**
     * 转码完成回调接口
     */
    public interface OnCompleteListener {
        void completed();
    }

    /**
     * 转码进度监听器
     */
    public interface OnProgressListener {
        void progress(long current, long total);
    }

    /**
     * 设置转码完成监听器
     *
     * @param onCompleteListener
     */
    public void setOnCompleteListener(OnCompleteListener onCompleteListener) {
        this.onCompleteListener = onCompleteListener;
    }

    public void setOnProgressListener(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
    }

}
