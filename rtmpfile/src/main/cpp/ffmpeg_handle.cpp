//
// Created by eric on 2017/11/1.
//
#include <jni.h>
#include <string>
#include<android/log.h>

//定义日志宏变量
#define logw(content)   __android_log_write(ANDROID_LOG_WARN,"eric",content)
#define loge(content)   __android_log_write(ANDROID_LOG_ERROR,"eric",content)
#define logd(content)   __android_log_write(ANDROID_LOG_DEBUG,"eric",content)

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
//引入时间
#include "libavutil/time.h"
}

#include <iostream>

using namespace std;

jobject pushCallback = NULL;
jclass cls = NULL;
jmethodID mid = NULL;

int callback(JNIEnv *env, int64_t pts, int64_t dts, int64_t duration, long long index) {
//    logw("=================")
    if (pushCallback == NULL) {
        return -3;
    }
    if (cls == NULL) {
        return -1;
    }
    if (mid == NULL) {
        return -2;
    }
    env->CallVoidMethod(pushCallback, mid, (jlong) pts, (jlong) dts, (jlong) duration,
                        (jlong) index);
    return 0;
}

int avError(int errNum) {
    char buf[1024];
    //获取错误信息
    av_strerror(errNum, buf, sizeof(buf));
    loge(string().append("发生异常：").append(buf).c_str());
    return -1;
}

//获取FFmpeg相关信息
extern "C"
JNIEXPORT jstring JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_getAvcodecConfiguration(JNIEnv *env,
                                                                        jobject instance) {
    char info[10000] = {0};
    sprintf(info, "%s\n", avcodec_configuration());
    return env->NewStringUTF(info);
}

/**
 * 设置回到对象
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_setCallback(JNIEnv *env, jobject instance,
                                                            jobject pushCallback1) {
    //转换为全局变量
    pushCallback = env->NewGlobalRef(pushCallback1);
    if (pushCallback == NULL) {
        return -3;
    }
    cls = env->GetObjectClass(pushCallback);
    if (cls == NULL) {
        return -1;
    }
    mid = env->GetMethodID(cls, "videoCallback", "(JJJJ)V");
    if (mid == NULL) {
        return -2;
    }
    env->CallVoidMethod(pushCallback, mid, (jlong) 0, (jlong) 0, (jlong) 0, (jlong) 0);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_pushRtmpFile(JNIEnv *env, jobject instance,
                                                             jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);
    logw(path);
    int videoindex = -1;
    //所有代码执行之前要调用av_register_all和avformat_network_init
    //初始化所有的封装和解封装 flv mp4 mp3 mov。不包含编码和解码
    av_register_all();

    //初始化网络库
    avformat_network_init();

    const char *inUrl = path;
    //输出的地址
    const char *outUrl = "rtmp://192.168.31.127/live";

    //////////////////////////////////////////////////////////////////
    //                   输入流处理部分
    /////////////////////////////////////////////////////////////////
    //打开文件，解封装 avformat_open_input
    //AVFormatContext **ps  输入封装的上下文。包含所有的格式内容和所有的IO。如果是文件就是文件IO，网络就对应网络IO
    //const char *url  路径
    //AVInputFormt * fmt 封装器
    //AVDictionary ** options 参数设置
    AVFormatContext *ictx = NULL;

    //打开文件，解封文件头
    int ret = avformat_open_input(&ictx, inUrl, 0, NULL);
    if (ret < 0) {
        return avError(ret);
    }
    logw("avformat_open_input success!");
    //获取音频视频的信息 .h264 flv 没有头信息
    ret = avformat_find_stream_info(ictx, 0);
    if (ret != 0) {
        return avError(ret);
    }
    //打印视频视频信息
    //0打印所有  inUrl 打印时候显示，
    av_dump_format(ictx, 0, inUrl, 0);

    //////////////////////////////////////////////////////////////////
    //                   输出流处理部分
    /////////////////////////////////////////////////////////////////
    AVFormatContext *octx = NULL;
    //如果是输入文件 flv可以不传，可以从文件中判断。如果是流则必须传
    //创建输出上下文
    ret = avformat_alloc_output_context2(&octx, NULL, "flv", outUrl);
    if (ret < 0) {
        return avError(ret);
    }
    logw("avformat_alloc_output_context2 success!");

    cout << "nb_streams  " << ictx->nb_streams << endl;
    int i;

    for (i = 0; i < ictx->nb_streams; i++) {

        //获取输入视频流
        AVStream *in_stream = ictx->streams[i];
        //为输出上下文添加音视频流（初始化一个音视频流容器）
        AVStream *out_stream = avformat_new_stream(octx, in_stream->codec->codec);
        if (!out_stream) {
            printf("未能成功添加音视频流\n");
            ret = AVERROR_UNKNOWN;
        }
        if (octx->oformat->flags & AVFMT_GLOBALHEADER) {
            out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
        }
        ret = avcodec_parameters_copy(out_stream->codecpar, in_stream->codecpar);
        if (ret < 0) {
            printf("copy 编解码器上下文失败\n");
        }
        out_stream->codecpar->codec_tag = 0;
        logw("get stream++++++");

//        out_stream->codec->codec_tag = 0;
    }

    //输入流数据的数量循环
    for (i = 0; i < ictx->nb_streams; i++) {
        if (ictx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoindex = i;
            break;
        }
    }

    av_dump_format(octx, 0, outUrl, 1);
    logw("avio_open");

    //////////////////////////////////////////////////////////////////
    //                   准备推流
    /////////////////////////////////////////////////////////////////

    //打开IO
    ret = avio_open(&octx->pb, outUrl, AVIO_FLAG_WRITE);
    if (ret < 0) {
        avError(ret);
    }
    if (ret != 0) {
        loge("avio_open error");
    }

    logw("avformat_write_header");
    //写入头部信息
    ret = avformat_write_header(octx, 0);
    if (ret < 0) {
        avError(ret);
    }
    logw("avformat_write_header Success!");
    //推流每一帧数据
    //int64_t pts  [ pts*(num/den)  第几秒显示]
    //int64_t dts  解码时间 [P帧(相对于上一帧的变化) I帧(关键帧，完整的数据) B帧(上一帧和下一帧的变化)]  有了B帧压缩率更高。
    //uint8_t *data
    //int size
    //int stream_index
    //int flag
    AVPacket pkt;
    //获取当前的时间戳  微妙
    long long start_time = av_gettime();
    long long frame_index = 0;
    logw("start push");
    while (1) {
        //输入输出视频流
        AVStream *in_stream, *out_stream;
        //获取解码前数据
        ret = av_read_frame(ictx, &pkt);
        if (ret < 0) {
            break;
        }

        /*
        PTS（Presentation Time Stamp）显示播放时间
        DTS（Decoding Time Stamp）解码时间
        */
        //没有显示时间（比如未解码的 H.264 ）
        if (pkt.pts == AV_NOPTS_VALUE) {
            //AVRational time_base：时基。通过该值可以把PTS，DTS转化为真正的时间。
            AVRational time_base1 = ictx->streams[videoindex]->time_base;

            //计算两帧之间的时间
            /*
            r_frame_rate 基流帧速率  （不是太懂）
            av_q2d 转化为double类型
            */
            int64_t calc_duration =
                    (double) AV_TIME_BASE / av_q2d(ictx->streams[videoindex]->r_frame_rate);

            //配置参数
            pkt.pts = (double) (frame_index * calc_duration) /
                      (double) (av_q2d(time_base1) * AV_TIME_BASE);
            pkt.dts = pkt.pts;
            pkt.duration =
                    (double) calc_duration / (double) (av_q2d(time_base1) * AV_TIME_BASE);
        }

        //延时
        if (pkt.stream_index == videoindex) {
            AVRational time_base = ictx->streams[videoindex]->time_base;
            AVRational time_base_q = {1, AV_TIME_BASE};
            //计算视频播放时间
            int64_t pts_time = av_rescale_q(pkt.dts, time_base, time_base_q);
            //计算实际视频的播放时间
            int64_t now_time = av_gettime() - start_time;

            AVRational avr = ictx->streams[videoindex]->time_base;
            cout << avr.num << " " << avr.den << "  " << pkt.dts << "  " << pkt.pts << "   "
                 << pts_time << endl;
            if (pts_time > now_time) {
                //睡眠一段时间（目的是让当前视频记录的播放时间与实际时间同步）
                av_usleep((unsigned int) (pts_time - now_time));
            }
        }

        in_stream = ictx->streams[pkt.stream_index];
        out_stream = octx->streams[pkt.stream_index];

        //计算延时后，重新指定时间戳
        pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base,
                                   (AVRounding) (AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
        pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base,
                                   (AVRounding) (AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
        pkt.duration = (int) av_rescale_q(pkt.duration, in_stream->time_base,
                                          out_stream->time_base);
//        __android_log_print(ANDROID_LOG_WARN, "eric", "duration %d", pkt.duration);
        //字节流的位置，-1 表示不知道字节流位置
        pkt.pos = -1;

        if (pkt.stream_index == videoindex) {
            printf("Send %8d video frames to output URL\n", frame_index);
            frame_index++;
        }
        callback(env, pkt.pts, pkt.dts, pkt.duration, frame_index);
        //向输出上下文发送（向地址推送）
        ret = av_interleaved_write_frame(octx, &pkt);

        if (ret < 0) {
            printf("发送数据包出错\n");
            break;
        }

        //释放
        av_packet_unref(&pkt);
    }
    avio_close(octx->pb);
    avformat_free_context(octx);
    avformat_close_input(&ictx);
    octx = NULL;
    ictx = NULL;
    env->ReleaseStringUTFChars(path_, path);
    return 0;

}