
最近工作比较忙，很久没有更新这个系列的文章。我们先回顾一下上一篇[MediaCodec进行AAC编解码（文件格式转换）](https://www.jianshu.com/p/875049a5b40f)
的内容，里面介绍了MediaExtractor的使用，MediaCodec进行音频文件的解码和编码，ADTS的介绍和封装。今天这篇文章在此基础上跟大家一起学习如何通过Android设备
进行音频的采集，然后使用MediaCodec进行AAC编码，最后输出到文件。这部分我们关注的重点就是在如何进行音频的采集。

音频的采集涉及一个类AudioRecord。我们先介绍下这个类
## AudioRecord
我们还是先看下官方的说明。AudioRecord类在Java应用程序中管理音频资源，用来记录从平台音频输入设备产生的数据。通过AudioRecord对象来完成"pulling"（读取）数据。
应用通过以下几个方法负责立即从AudioRecord对象读取：read(byte[], int, int)，read(short[], int, int)或read(ByteBuffer, int).无论使用哪种音频格式，使用AudioRecord是最方便的。
在创建AudioRecord对象时，AudioRecord会初始化，并和音频缓冲区连接，用来缓冲新的音频数据。根据构造时指定的缓冲区大小，来决定AudioRecord能够记录多长的数据。从硬件设备读取的数据，应小于整个记录缓冲区。
AudioRecord的使用我们分一下几个步骤：
### 第一步 创建AudioRecord
AudioRecord直接使用new来创建，我们看一下构造方法：
```java
    //---------------------------------------------------------
    // Constructor, Finalize
    //--------------------
    /**
     * Class constructor.
     * Though some invalid parameters will result in an {@link IllegalArgumentException} exception,
     * other errors do not.  Thus you should call {@link #getState()} immediately after construction
     * to confirm that the object is usable.
     * @param audioSource the recording source.
     *   See {@link MediaRecorder.AudioSource} for the recording source definitions.
     * @param sampleRateInHz the sample rate expressed in Hertz. 44100Hz is currently the only
     *   rate that is guaranteed to work on all devices, but other rates such as 22050,
     *   16000, and 11025 may work on some devices.
     *   {@link AudioFormat#SAMPLE_RATE_UNSPECIFIED} means to use a route-dependent value
     *   which is usually the sample rate of the source.
     *   {@link #getSampleRate()} can be used to retrieve the actual sample rate chosen.
     * @param channelConfig describes the configuration of the audio channels.
     *   See {@link AudioFormat#CHANNEL_IN_MONO} and
     *   {@link AudioFormat#CHANNEL_IN_STEREO}.  {@link AudioFormat#CHANNEL_IN_MONO} is guaranteed
     *   to work on all devices.
     * @param audioFormat the format in which the audio data is to be returned.
     *   See {@link AudioFormat#ENCODING_PCM_8BIT}, {@link AudioFormat#ENCODING_PCM_16BIT},
     *   and {@link AudioFormat#ENCODING_PCM_FLOAT}.
     * @param bufferSizeInBytes the total size (in bytes) of the buffer where audio data is written
     *   to during the recording. New audio data can be read from this buffer in smaller chunks
     *   than this size. See {@link #getMinBufferSize(int, int, int)} to determine the minimum
     *   required buffer size for the successful creation of an AudioRecord instance. Using values
     *   smaller than getMinBufferSize() will result in an initialization failure.
     * @throws java.lang.IllegalArgumentException
     */
    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat,
            int bufferSizeInBytes)
    throws IllegalArgumentException {
        this((new AudioAttributes.Builder())
                    .setInternalCapturePreset(audioSource)
                    .build(),
                (new AudioFormat.Builder())
                    .setChannelMask(getChannelMaskFromLegacyConfig(channelConfig,
                                        true/*allow legacy configurations*/))
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRateInHz)
                    .build(),
                bufferSizeInBytes,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
    }
```
这个注释写的还是比较清楚的。如果参数无效可能会抛出异常，所以创建后要通过`getState()`方法来判断是否可用，我们看到参数

-  audioSource 音频录制源
-  sampleRateInHz 默认采样率，单位Hz。44100Hz是当前唯一能保证在所有设备上工作的采样率，在一些设备上还有22050, 16000或11025。
- channelConfig 描述音频通道设置
- audioFormat 音频数据保证支持此格式。请见ENCODING_PCM_16BIT和ENCODING_PCM_8BIT。
- bufferSizeInBytes 
这个是最难理解又最重要的一个参数，它配置的是 AudioRecord 内部的音频缓冲区的大小，该缓冲区的值不能低于一帧“音频帧”
（Frame）的大小，一帧音频帧的大小计算如下：
int size = 采样率 x 位宽 x 采样时间 x 通道数
采样时间一般取 2.5ms~120ms 之间，由厂商或者具体的应用决定，我们其实可以推断，每一帧的采样时间取得越短，产生的延时就应该会越小，当然，碎片化的数据也就会越多。在Android开发中，AudioRecord 类提供了一个帮助你确定这个 bufferSizeInBytes 的函数
设置的值比getMinBufferSize()还小则会导致初始化失败。

前面说到创建完后要通过`getState()`判断是否可用，判断返回值是否等于` AudioRecord.STATE_INITIALIZED`

### 第二步 开始采集
这一步很简单，直接调用`startRecording()`即可。

### 第三步 读取数据
通过read方法读取采集到的音频数据,看下方法的定义：
```java
    //---------------------------------------------------------
    // Audio data supply
    //--------------------
    /**
     * Reads audio data from the audio hardware for recording into a byte array.
     * The format specified in the AudioRecord constructor should be
     * {@link AudioFormat#ENCODING_PCM_8BIT} to correspond to the data in the array.
     * @param audioData the array to which the recorded audio data is written.
     * @param offsetInBytes index in audioData from which the data is written expressed in bytes.
     * @param sizeInBytes the number of requested bytes.
     * @return zero or the positive number of bytes that were read, or one of the following
     *    error codes. The number of bytes will not exceed sizeInBytes.
     * <ul>
     * <li>{@link #ERROR_INVALID_OPERATION} if the object isn't properly initialized</li>
     * <li>{@link #ERROR_BAD_VALUE} if the parameters don't resolve to valid data and indexes</li>
     * <li>{@link #ERROR_DEAD_OBJECT} if the object is not valid anymore and
     *    needs to be recreated. The dead object error code is not returned if some data was
     *    successfully transferred. In this case, the error is returned at the next read()</li>
     * <li>{@link #ERROR} in case of other error</li>
     * </ul>
     */
    public int read(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes) {
        return read(audioData, offsetInBytes, sizeInBytes, READ_BLOCKING);
    }
```
把从硬件录制采集到的音频数据读取到byte数组中。 返回值是读入缓冲区的总byte数。如果发生错误则返回值小于0，如果对象属性没有初始化，则返回ERROR_INVALID_OPERATION，如果参数不能解析成有效的数据或索引，则返回ERROR_BAD_VALUE。读取的总byte数不会超过sizeInBytes。

### 最后一步 释放资源
直接调用`release()`方法即可，对象不能经常使用此方法，而且在调用release()后，必须设置引用为null。


## 实战
AudioRecord 学习后，那么使用Android设备采集编码并封装输出到文件所需要的技术知识储备我们已经都具备了。现在到了如何在代码中体现的阶段了。
看到`AudioRecordActivity`。我们还是分步骤看：

### 初始化
初始化涉及两个方面，AudioRecord的创建和MediaCodec的创建
```java
        initAudioDevice();
        try {
            mAudioEncoder = initAudioEncoder();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("audio encoder init fail");
        }
```
先看到`initAudioDevice()`，AudioRecord的创建
```java
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
            mAudioSampleRate = sampleRate;
            mAudioChanelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
            mAudioBuffer = new byte[Math.min(4096, buffsize)];
            mSampleRateType = ADTSUtils.getSampleRateType(sampleRate);
            LogUtils.w("编码器参数:" + mAudioSampleRate + " " + mSampleRateType + " " + mAudioChanelCount);
        }
    }
```
这里的逻辑和我们刚刚介绍的AudioRecord一致。只是循环查找有效的采样率。



