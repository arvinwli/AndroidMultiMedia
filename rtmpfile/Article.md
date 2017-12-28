## 简介
在前面的两篇文章中：[Android RTMP推流之MediaCodec硬编码一（H.264进行flv封装）](https://www.jianshu.com/p/e607e63fb78f)介绍了如何MediaCodec进行H264硬编码，然后将编码后的数据封装到flv文件中。[Android平台下RTMPDump的使用](https://www.jianshu.com/p/3ee9e5e4d630)介绍了如何将RTMPDump移植到Android平台下，并读取解析flv文件进行推流。有了上面两篇文章的基础后，接下了就是整合，在Android平台下使用MediaCodec进行硬编码，然后使用RTMPDump进行推流。代码还是在上面两篇文章的基础上进行修改。仓库地址不变[**FFmpegSample**](https://github.com/EricLi22/FFmpegSample)，对应版本为v1.5。



### 第一步 jni方法定义

新增jni调用方法。主要增加三个方法：建立连接，推流数据，释放连接。

```java
public class RtmpHandle {
    private static RtmpHandle mInstance;

    private RtmpHandle() {
    }

    public synchronized static RtmpHandle getInstance() {
        if (mInstance == null) {
            mInstance = new RtmpHandle();
        }
        return mInstance;
    }

    static {
        System.loadLibrary("rtmp");
    }

    public native void pushFile(String path);

    public native int connect(String url);

    public native int push(byte[] buf, int length);

    public native int close();
}
```



### 第二步 Android层jni调用

Android层调用，第一步定义了jni方法，那么在哪里调用呢？我们还是使用[Android RTMP推流之MediaCodec硬编码一（H.264进行flv封装）](https://www.jianshu.com/p/e607e63fb78f)里的代码。复制打开摄像头编码的CameraMediaCodecActivity为CameraMediaCodecRtmpActivity，然后只用修改三个地方。

```java
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mFlvPacker.start();
//        mOutStream = IOUtils.open(DATA_DIR + File.separator + "/easy.flv", true);
        CameraInterface.getInstance().startPreview(mHolder, mStreamIt);
        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                int ret = RtmpHandle.getInstance().connect("rtmp://192.168.31.127/live");
                LogUtils.w("打开RTMP连接: " + ret);
            }
        });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mFlvPacker.stop();
        CameraInterface.getInstance().stopPreview();
        CameraInterface.getInstance().releaseCamera();
        int ret = RtmpHandle.getInstance().close();
        LogUtils.w("关闭RTMP连接：" + ret);
//        IOUtils.close(mOutStream);
    }
```

在Surface创建可销毁的时候分别调用建立和关闭连接的方法。

修改flv封装后的数据回调

```java
        mFlvPacker.setPacketListener(new Packer.OnPacketListener() {
            @Override
            public void onPacket(final byte[] data, final int packetType) {
                pushExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int ret = RtmpHandle.getInstance().push(data, data.length);
                        LogUtils.w("type：" + packetType + "  length:" + data.length + "  推流结果:" + ret);
                    }
                });
            }
        });
```

[Android RTMP推流之MediaCodec硬编码一（H.264进行flv封装）](https://www.jianshu.com/p/e607e63fb78f)是将数据直接写到文件中，现在将数据推流出去。到这里Android层的调用就完成了，是不是很容易。



### 第三步  c++层方法实现

c++层推流逻辑的编写。我们将方法写到rtmp_handle.cpp。我们在[Android平台下RTMPDump的使用](https://www.jianshu.com/p/3ee9e5e4d630)这篇文章代码基础上修改，其实就是将推送文件流的方法publish_using_packet拆分成三个部分，新增上面声明的三个方法。

#### connect方法

```cpp
RTMP *rtmp = NULL;
RTMPPacket *packet = NULL;

/*
 * 初始化RTMP连接
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_rtmp_RtmpHandle_connect(JNIEnv *env, jobject instance, jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    int len = strlen(url);

    char *rtmpUrl = NULL;
    rtmpUrl = (char *) malloc(len + 1);
    memset(rtmpUrl, 0, len + 1);
    memcpy(rtmpUrl, url, len);

    __android_log_print(ANDROID_LOG_WARN, "eric",
                        "%d,%s",
                        len, rtmpUrl);
    rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    //set connection timeout,default 30s
    rtmp->Link.timeout = 5;
//    if (!RTMP_SetupURL(rtmp, "rtmp://192.168.31.127/live")) {
    if (!RTMP_SetupURL(rtmp, rtmpUrl)) {
        RTMP_Log(RTMP_LOGERROR, "SetupURL Err\n");
        RTMP_Free(rtmp);
        return -1;
    }
    logw("RTMP_SetupURL");

    //if unable,the AMF command would be 'play' instead of 'publish'
    RTMP_EnableWrite(rtmp);
    logw("RTMP_EnableWrite");
    if (!RTMP_Connect(rtmp, NULL)) {
        RTMP_Log(RTMP_LOGERROR, "Connect Err\n");
        RTMP_Free(rtmp);
        return -2;
    }
    logw("RTMP_Connect");

    if (!RTMP_ConnectStream(rtmp, 0)) {
        RTMP_Log(RTMP_LOGERROR, "ConnectStream Err\n");
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        return -3;
    }
    logw("RTMP_ConnectStream");

    packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, 1024 * 64);
    RTMPPacket_Reset(packet);

    packet->m_hasAbsTimestamp = 0;
    packet->m_nChannel = 0x04;
    packet->m_nInfoField2 = rtmp->m_stream_id;
    logw("Ready to send data ...");
    RTMP_LogPrintf("Ready to send data ...\n");
    env->ReleaseStringUTFChars(url_, url);
    return 0;
}
```

首先声明RTMP和RTMPPacket的全局变量，后面调用推流方法时候会用到。剩下的就是创建RTMP,建立连接等等，代码没有太多变化。



#### push方法

```java
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_rtmp_RtmpHandle_push(JNIEnv *env, jobject instance, jbyteArray buf_,
                                                 jint length) {
    jbyte *buf = env->GetByteArrayElements(buf_, NULL);
    // TODO
    if (length < 15) {
        return -1;
    }
    //packet attributes
    uint32_t type = 0;
    uint32_t datalength = 0;
    uint32_t timestamp = 0;
    uint32_t streamid = 0;

    memcpy(&type, buf, 1);
    buf++;
    memcpy(&datalength, buf, 3);

    datalength = HTON24(datalength);
    buf += 3;
    memcpy(&timestamp, buf, 4);
    timestamp = HTONTIME(timestamp);
    buf += 4;
    memcpy(&streamid, buf, 3);
    streamid = HTON24(streamid);
    buf += 3;

    __android_log_print(ANDROID_LOG_WARN, "eric",
                        "解析包数据：%u,%u,%u,%u,%d",
                        type, datalength, timestamp, streamid, length);

    if (type != 0x08 && type != 0x09) {
        return -2;
    }
    if (datalength != (length - 11 - 4)) {
        return -3;
    }

    memcpy(packet->m_body, buf, length - 11 - 4);

    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = timestamp;
    packet->m_packetType = type;
    packet->m_nBodySize = datalength;
    if (!RTMP_IsConnected(rtmp)) {
        RTMP_Log(RTMP_LOGERROR, "rtmp is not connect\n");
        return -4;
    }
    if (!RTMP_SendPacket(rtmp, packet, 0)) {
        RTMP_Log(RTMP_LOGERROR, "Send Error\n");
        return -5;
    }
    env->ReleaseByteArrayElements(buf_, buf, 0);
    return 0;
}
```

首先我们要知道上层传递过来的数据buf是一个完成的flv TAG。所以我们要先解析这个TAG，对于flv格式不熟悉的请移步到[flv格式详解+实例剖析](https://www.jianshu.com/p/7ffaec7b3be6)。但大家肯定发现读取到datalength后又调用了HTON24这是为什么。大家需要先了解什么叫大小端（小端是地位存在低字节，高位存在高字节；大端相反），如果不知道请先查找资料了解下。还需要知道一点，Java是平台无关的，默认是大端。那么我们知道Android层调用push传递过来的数据是大端对齐的。而到c++我的arm机器底层是小端对齐的。所以需要进行大小端转换。否则得到的数据就是错误的。

举个例子：为什么type没有转换，type定义的是uint32_t为4个字节。而解析flv Tag中type我们只存放了一个字节，加入是8，也就是0x08。那么调用`memcpy(&type, buf, 1);`后，type的内存存储就是0x08 00 00 00。正好通过小端模式读取出来就是8，所以不需要转换。

还有一点要注意网络传输数据都是大端对齐的，那有人问这里都转换成了小端，其实在RTMP_SendPacket推流方法中，推送之前也有做大小端转换，将大于1个字节的数据类型转换成大端对齐。大家可以查看源码就知道了。

接着就是讲解析到的数据存放到packet中。然后通过RTMP_SendPacket推送出去。



#### close方法

```cpp
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_rtmp_RtmpHandle_close(JNIEnv *env, jobject instance) {
    // TODO
    if (rtmp != NULL) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        rtmp = NULL;
    }
    if (packet != NULL) {
        RTMPPacket_Free(packet);
        free(packet);
        packet = NULL;
    }
    return 0;
}
```

这个就很简单了，就是断开连接，释放资源。



**这篇文章主要让大家先整个流程跑起来，至于RTMP协议内容以及RTMPDump的源码我们后面再做介绍**



## 结尾

大家可能发现整个过程涉及的代码很少，因为这个体系涉及内容很多，所以我进行了拆分，每一篇文章只讲一个技术点，都是在上一篇的代码基础上做修改，所以如果只看这一篇肯定是很懵逼了。

