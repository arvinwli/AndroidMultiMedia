package com.wangheart.rtmpfile;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

import com.wangheart.rtmpfile.camera.CameraInterface;
import com.wangheart.rtmpfile.flv.FlvPacker;
import com.wangheart.rtmpfile.flv.Packer;
import com.wangheart.rtmpfile.utils.ADTSUtils;
import com.wangheart.rtmpfile.utils.BytesHexStrTranslate;
import com.wangheart.rtmpfile.utils.FileUtil;
import com.wangheart.rtmpfile.utils.IOUtils;
import com.wangheart.rtmpfile.utils.LogUtils;
import com.wangheart.rtmpfile.utils.PhoneUtils;
import com.wangheart.rtmpfile.video.VideoComponent;
import com.wangheart.rtmpfile.view.MySurfaceView;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author : eric
 * CreateDate : 2017/11/6  10:57
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class VideoCompoundFileActivity extends Activity implements SurfaceHolder.Callback {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    private MySurfaceView sv;
    //建议的视频宽度，不超过这个宽度，自动寻找4：3的尺寸
    private final int SUGGEST_PREVIEW_WIDTH=640;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private SurfaceHolder mHolder;
    //采集到每帧数据时间
    long previewTime = 0;
    //每帧开始编码时间
    long encodeTime = 0;
    //采集数量
    int count = 0;
    //编码数量
    int encodeCount = 0;
    //采集数据回调
    private PreviewFrameCallback mPreviewFrameCallback;
    private MediaCodec mMediaCodec;
    private static final String VCODEC_MIME = "video/avc";
    private FlvPacker mFlvPacker;
    private final int FRAME_RATE = 15;
    private OutputStream mOutStream;
    //视频编码组件封装
    private VideoComponent mVideoComponent;

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
//    private BufferedOutputStream mAudioBos;
    private ArrayBlockingQueue<byte[]> queue;
    private boolean isRecord = false;
    private int MAX_BUFFER_SIZE = 8192;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_compound_file);
        init();
    }

    private void init() {
        sv = findViewById(R.id.sv);
        mVideoComponent = new VideoComponent();
        mPreviewFrameCallback = new PreviewFrameCallback();
        initCamera();
        initMediaCodec();
        initAudioDevice();
        try {
            mAudioEncoder = initAudioEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("audio encoder init fail");
        }
        mFlvPacker = new FlvPacker();
        mFlvPacker.initVideoParams(videoWidth, videoHeight, FRAME_RATE);
        mFlvPacker.initAudioParams(mAudioSampleRate,16,true);
        mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
            @Override
            synchronized public void onPacket(byte[] data, int packetType) {
                IOUtils.write(mOutStream, data, 0, data.length);
                LogUtils.w("flv输出 type:"+packetType+",length:"+data.length);
                if(packetType==3||packetType==2){
                    LogUtils.w("firstVideo:"+ BytesHexStrTranslate.bytesToHex(data));
                }
            }
        });
    }

    private void initMediaCodec() {
        int bitrate = 2 * videoWidth * videoHeight * FRAME_RATE / 20;
        try {
            MediaCodecInfo mediaCodecInfo = mVideoComponent.getSupportMediaCodecInfo(VCODEC_MIME);
            if (mediaCodecInfo == null) {
                throw new RuntimeException("mediaCodecInfo is Empty");
            }
            mMediaCodec = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(VCODEC_MIME, videoWidth, videoHeight);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    mVideoComponent.getSupportMediaCodecColorFormat(mediaCodecInfo));
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void initCamera(){
        CameraInterface.getInstance().openCamera(0);
        Camera.Parameters params = CameraInterface.getInstance().getParams();
        //查找合适的预览尺寸
        Camera.Size size= mVideoComponent.getSupportPreviewSize(params,SUGGEST_PREVIEW_WIDTH);
        if(size==null){
            throw new RuntimeException("not found support preview size");
        }
        videoWidth=size.width;
        videoHeight=size.height;
        params.setPictureFormat(ImageFormat.JPEG);
        params.setPreviewFormat(mVideoComponent.getSupportPreviewColorFormat(params));
//        params.setPictureSize(videoWidth, videoHeight);
        params.setPreviewSize(videoWidth, videoHeight);
        params.setPreviewFpsRange(15000, 20000);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        CameraInterface.getInstance().adjustOrientation(this, new CameraInterface.OnOrientationChangeListener() {
            @Override
            public void onChange(int degree) {
                FrameLayout.LayoutParams lp =
                        (FrameLayout.LayoutParams) sv.getLayoutParams();
                LogUtils.d(PhoneUtils.getWidth() + " " + PhoneUtils.getHeight());
                if (degree == 90) {
                    lp.height = PhoneUtils.getWidth() * videoWidth / videoHeight;
                } else {
                    lp.height = PhoneUtils.getWidth() * videoHeight / videoWidth;
                }
                sv.setLayoutParams(lp);
            }
        });
        CameraInterface.getInstance().resetParams(params);
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraInterface.getInstance().releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHolder != null) {
            CameraInterface.getInstance().startPreview(mHolder, mPreviewFrameCallback);
        }
        startTakeAudio();

    }

    private void startTakeAudio(){
        //开启录音
        mRecordThread = new Thread(fetchAudioRunnable());
//        try {
//            mAudioBos = new BufferedOutputStream(new FileOutputStream(new File(FileUtil.getMainDir(), "record.aac")), 200 * 1024);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
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
    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            LogUtils.w("编码线程开始");
            while (isRecord || !queue.isEmpty())
                encodePCM();
            release();
        }
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
            LogUtils.d("编码音频: ");
            mAudioEncoder.queueInputBuffer(inputIndex, 0, chunkPCM.length, pts, 0);//通知编码器 编码
        }

        outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);//同解码器
        while (outputIndex >= 0) {//同解码器
            outputBuffer = encodeOutputBuffers[outputIndex];
            //进行flv封装
            mFlvPacker.onAudioData(outputBuffer, mAudioEncodeBufferInfo);
//            outBitSize = mAudioEncodeBufferInfo.size;
//            outPacketSize = outBitSize + 7;//7为ADTS头部的大小
//            outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
//            outputBuffer.position(mAudioEncodeBufferInfo.offset);
//            outputBuffer.limit(mAudioEncodeBufferInfo.offset + outBitSize);
//            chunkAudio = new byte[outPacketSize];
//            ADTSUtils.addADTStoPacket(mSampleRateType, chunkAudio, outPacketSize);//添加ADTS 代码后面会贴上
//            outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中 偏移量offset=7 你懂得
//            outputBuffer.position(mAudioEncodeBufferInfo.offset);
//            try {
//                LogUtils.d("接受编码后数据 " + chunkAudio.length);
//                mFlvPacker.onAudioData(chunkAudio);
////                mAudioBos.write(chunkAudio, 0, chunkAudio.length);//BufferOutputStream 将文件保存到内存卡中 *.aac
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            mAudioEncoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
//        try {
//            if (mAudioBos != null) {
//                mAudioBos.flush();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (mAudioBos != null) {
//                try {
//                    mAudioBos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } finally {
//                    mAudioBos = null;
//                }
//            }
//        }
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

    @Override
    protected void onPause() {
        super.onPause();
        isRecord = false;
        CameraInterface.getInstance().stopPreview();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mFlvPacker.start();
        mOutStream = IOUtils.open(FileUtil.getMainDir() + File.separator + "/VideoCompound.flv", false);
        CameraInterface.getInstance().startPreview(mHolder, mPreviewFrameCallback);

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mFlvPacker.stop();
        CameraInterface.getInstance().stopPreview();
        CameraInterface.getInstance().releaseCamera();
        IOUtils.close(mOutStream);
    }


    public class PreviewFrameCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            long endTime = System.currentTimeMillis();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    encodeTime = System.currentTimeMillis();
                    flvPackage(data);
                    LogUtils.w("编码第:" + (encodeCount++) + "帧，耗时:" + (System.currentTimeMillis() - encodeTime));
                }
            });
            LogUtils.v("采集第:" + (++count) + "帧，距上一帧间隔时间:"
                    + (endTime - previewTime) + "  " + Thread.currentThread().getName());
            previewTime = endTime;
        }
    }


    private void flvPackage(byte[] bufSou) {
        //编码格式转换
        byte[] buf = mVideoComponent.convert(bufSou);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        try {
            //查找可用的的input buffer用来填充有效数据
            int bufferIndex = mMediaCodec.dequeueInputBuffer(-1);
            if (bufferIndex >= 0) {
                //数据放入到inputBuffer中
                ByteBuffer inputBuffer = inputBuffers[bufferIndex];
                inputBuffer.clear();
                inputBuffer.put(buf, 0, buf.length);
                //把数据传给编码器并进行编码
                mMediaCodec.queueInputBuffer(bufferIndex, 0,
                        inputBuffers[bufferIndex].position(),
                        System.nanoTime() / 1000, 0);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                //输出buffer出队，返回成功的buffer索引。
                int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    //进行flv封装
                    mFlvPacker.onVideoData(outputBuffer, bufferInfo);
                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                }
            } else {
                LogUtils.w("No buffer available !");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
