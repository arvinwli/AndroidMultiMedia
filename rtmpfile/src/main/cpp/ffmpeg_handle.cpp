//
// Created by eric on 2017/11/1.
//
#include <jni.h>
#include <string>
#include<android/log.h>
#include <exception>

//定义日志宏变量
#define logw(content)   __android_log_write(ANDROID_LOG_WARN,"eric",content)
#define loge(content)   __android_log_write(ANDROID_LOG_ERROR,"eric",content)
#define logd(content)   __android_log_write(ANDROID_LOG_DEBUG,"eric",content)

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
//引入时间
#include "libavutil/time.h"
#include "libavutil/imgutils.h"
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
    const char *outUrl = "rtmp://192.168.31.127/live/test";

    //////////////////////////////////////////////////////////////////
    //                   输入流处理部分
    /////////////////////////////////////////////////////////////////
    //打开文件，解封装 avformat_open_input
    //AVFormatContext **ps  输入封装的上下文。包含所有的格式内容和所有的IO。如果是文件就是文件IO，网络就对应网络IO
    //const char *url  路径
    //AVInputFormt * fmt 封装器
    //AVDictionary ** options 参数设置
    AVFormatContext *ictx = NULL;

    AVFormatContext *octx = NULL;

    AVPacket pkt;
    int ret = 0;
    try {
        //打开文件，解封文件头
        ret = avformat_open_input(&ictx, inUrl, 0, NULL);
        if (ret < 0) {
            avError(ret);
            throw ret;
        }
        logd("avformat_open_input success!");
        //获取音频视频的信息 .h264 flv 没有头信息
        ret = avformat_find_stream_info(ictx, 0);
        if (ret != 0) {
            avError(ret);
            throw ret;
        }
        //打印视频视频信息
        //0打印所有  inUrl 打印时候显示，
        av_dump_format(ictx, 0, inUrl, 0);

        //////////////////////////////////////////////////////////////////
        //                   输出流处理部分
        /////////////////////////////////////////////////////////////////
        //如果是输入文件 flv可以不传，可以从文件中判断。如果是流则必须传
        //创建输出上下文
        ret = avformat_alloc_output_context2(&octx, NULL, "flv", outUrl);
        if (ret < 0) {
            avError(ret);
            throw ret;
        }
        logd("avformat_alloc_output_context2 success!");

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
//        out_stream->codec->codec_tag = 0;
        }

        //找到视频流的位置
        for (i = 0; i < ictx->nb_streams; i++) {
            if (ictx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
                videoindex = i;
                break;
            }
        }

        av_dump_format(octx, 0, outUrl, 1);
        //////////////////////////////////////////////////////////////////
        //                   准备推流
        /////////////////////////////////////////////////////////////////

        //打开IO
        ret = avio_open(&octx->pb, outUrl, AVIO_FLAG_WRITE);
        if (ret < 0) {
            avError(ret);
            throw ret;
        }
        logd("avio_open success!");
        //写入头部信息
        ret = avformat_write_header(octx, 0);
        if (ret < 0) {
            avError(ret);
            throw ret;
        }
        logd("avformat_write_header Success!");
        //推流每一帧数据
        //int64_t pts  [ pts*(num/den)  第几秒显示]
        //int64_t dts  解码时间 [P帧(相对于上一帧的变化) I帧(关键帧，完整的数据) B帧(上一帧和下一帧的变化)]  有了B帧压缩率更高。
        //获取当前的时间戳  微妙
        long long start_time = av_gettime();
        long long frame_index = 0;
        logd("start push >>>>>>>>>>>>>>>");
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
            //回调数据
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
        ret = 0;
    } catch (int errNum) {
    }
    logd("finish===============");
    //关闭输出上下文，这个很关键。Linux下FFmpeg编译以及Android平台下使用
    if (octx != NULL)
        avio_close(octx->pb);
    //释放输出封装上下文
    if (octx != NULL)
        avformat_free_context(octx);
    //关闭输入上下文
    if (ictx != NULL)
        avformat_close_input(&ictx);
    octx = NULL;
    ictx = NULL;
    env->ReleaseStringUTFChars(path_, path);
    return ret;
}

//=======================================================================
//
// 摄像头采集数据并输出（文件/RTMP推流）
//
//=======================================================================


AVFormatContext *ofmt_ctx;
AVStream *video_st;
AVCodecContext *pCodecCtx;
AVCodec *pCodec;
AVPacket enc_pkt;
AVFrame *pFrameYUV;
int count = 0;
int yuv_width;
int yuv_height;
int y_length;
int uv_length;
int width = 480;
int height = 320;
int fps = 15;

/**
 * 初始化
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_initVideo(JNIEnv *env, jobject instance,
                                                          jstring url_) {
    const char *out_path = env->GetStringUTFChars(url_, 0);
    logd(out_path);

    //计算yuv数据的长度
    yuv_width = width;
    yuv_height = height;
    y_length = width * height;
    uv_length = width * height / 4;

    av_register_all();

    //output initialize
    avformat_alloc_output_context2(&ofmt_ctx, NULL, "flv", out_path);
    //output encoder initialize
    pCodec = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!pCodec) {
        loge("Can not find encoder!\n");
        return -1;
    }
    pCodecCtx = avcodec_alloc_context3(pCodec);
    //编码器的ID号，这里为264编码器，可以根据video_st里的codecID 参数赋值
    pCodecCtx->codec_id = pCodec->id;
    //像素的格式，也就是说采用什么样的色彩空间来表明一个像素点
    pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    //编码器编码的数据类型
    pCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
    //编码目标的视频帧大小，以像素为单位
    pCodecCtx->width = width;
    pCodecCtx->height = height;
    pCodecCtx->framerate = (AVRational) {fps, 1};
    //帧率的基本单位，我们用分数来表示，
    pCodecCtx->time_base = (AVRational) {1, fps};
    //目标的码率，即采样的码率；显然，采样码率越大，视频大小越大
    pCodecCtx->bit_rate = 400000;
    //固定允许的码率误差，数值越大，视频越小
//    pCodecCtx->bit_rate_tolerance = 4000000;
    pCodecCtx->gop_size = 50;
    /* Some formats want stream headers to be separate. */
    if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        pCodecCtx->flags |= CODEC_FLAG_GLOBAL_HEADER;

    //H264 codec param
//    pCodecCtx->me_range = 16;
    //pCodecCtx->max_qdiff = 4;
    pCodecCtx->qcompress = 0.6;
    //最大和最小量化系数
    pCodecCtx->qmin = 10;
    pCodecCtx->qmax = 51;
    //Optional Param
    //两个非B帧之间允许出现多少个B帧数
    //设置0表示不使用B帧
    //b 帧越多，图片越小
    pCodecCtx->max_b_frames = 0;
    // Set H264 preset and tune
    AVDictionary *param = 0;
    //H.264
    if (pCodecCtx->codec_id == AV_CODEC_ID_H264) {
//        av_dict_set(&param, "preset", "slow", 0);
        /**
         * 这个非常重要，如果不设置延时非常的大
         * ultrafast,superfast, veryfast, faster, fast, medium
         * slow, slower, veryslow, placebo.　这是x264编码速度的选项
       */
        av_dict_set(&param, "preset", "superfast", 0);
        av_dict_set(&param, "tune", "zerolatency", 0);
    }

    if (avcodec_open2(pCodecCtx, pCodec, &param) < 0) {
        loge("Failed to open encoder!\n");
        return -1;
    }

    //Add a new stream to output,should be called by the user before avformat_write_header() for muxing
    video_st = avformat_new_stream(ofmt_ctx, pCodec);
    if (video_st == NULL) {
        return -1;
    }
    video_st->time_base.num = 1;
    video_st->time_base.den = fps;
//    video_st->codec = pCodecCtx;
    video_st->codecpar->codec_tag = 0;
    avcodec_parameters_from_context(video_st->codecpar, pCodecCtx);

    //Open output URL,set before avformat_write_header() for muxing
    if (avio_open(&ofmt_ctx->pb, out_path, AVIO_FLAG_READ_WRITE) < 0) {
        loge("Failed to open output file!\n");
        return -1;
    }

    //Write File Header
    avformat_write_header(ofmt_ctx, NULL);

    return 0;
}

/**
 * H264编码并输出
 */
int64_t startTime = 0;
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_onFrameCallback(JNIEnv *env, jobject instance,
                                                                jbyteArray buffer_) {
//    startTime = av_gettime();
    jbyte *in = env->GetByteArrayElements(buffer_, NULL);

    int ret = 0;

    pFrameYUV = av_frame_alloc();
    int picture_size = av_image_get_buffer_size(pCodecCtx->pix_fmt, pCodecCtx->width,
                                                pCodecCtx->height, 1);
    uint8_t *buffers = (uint8_t *) av_malloc(picture_size);


    //将buffers的地址赋给AVFrame中的图像数据，根据像素格式判断有几个数据指针
    av_image_fill_arrays(pFrameYUV->data, pFrameYUV->linesize, buffers, pCodecCtx->pix_fmt,
                         pCodecCtx->width, pCodecCtx->height, 1);

    //安卓摄像头数据为NV21格式，此处将其转换为YUV420P格式
    ////N21   0~width * height是Y分量，  width*height~ width*height*3/2是VU交替存储
    //复制Y分量的数据
    memcpy(pFrameYUV->data[0], in, y_length); //Y
    pFrameYUV->pts = count;
    for (int i = 0; i < uv_length; i++) {
        //将v数据存到第三个平面
        *(pFrameYUV->data[2] + i) = *(in + y_length + i * 2);
        //将U数据存到第二个平面
        *(pFrameYUV->data[1] + i) = *(in + y_length + i * 2 + 1);
    }

    pFrameYUV->format = AV_PIX_FMT_YUV420P;
    pFrameYUV->width = yuv_width;
    pFrameYUV->height = yuv_height;

    //例如对于H.264来说。1个AVPacket的data通常对应一个NAL
    //初始化AVPacket
    av_init_packet(&enc_pkt);
//    __android_log_print(ANDROID_LOG_WARN, "eric", "编码前时间:%lld",
//                        (long long) ((av_gettime() - startTime) / 1000));
    //开始编码YUV数据
    ret = avcodec_send_frame(pCodecCtx, pFrameYUV);
    if (ret != 0) {
        logw("avcodec_send_frame error");
        return -1;
    }
    //获取编码后的数据
    ret = avcodec_receive_packet(pCodecCtx, &enc_pkt);
//    __android_log_print(ANDROID_LOG_WARN, "eric", "编码时间:%lld",
//                        (long long) ((av_gettime() - startTime) / 1000));
    //是否编码前的YUV数据
    av_frame_free(&pFrameYUV);
    if (ret != 0 || enc_pkt.size <= 0) {
        loge("avcodec_receive_packet error");
        avError(ret);
        return -2;
    }
    enc_pkt.stream_index = video_st->index;
    AVRational time_base = ofmt_ctx->streams[0]->time_base;//{ 1, 1000 };
    enc_pkt.pts = count * (video_st->time_base.den) / ((video_st->time_base.num) * fps);
    enc_pkt.dts = enc_pkt.pts;
    enc_pkt.duration = (video_st->time_base.den) / ((video_st->time_base.num) * fps);
    __android_log_print(ANDROID_LOG_WARN, "eric",
                        "index:%d,pts:%lld,dts:%lld,duration:%lld,time_base:%d,%d",
                        count,
                        (long long) enc_pkt.pts,
                        (long long) enc_pkt.dts,
                        (long long) enc_pkt.duration,
                        time_base.num, time_base.den);
    enc_pkt.pos = -1;
//    AVRational time_base_q = {1, AV_TIME_BASE};
//    //计算视频播放时间
//    int64_t pts_time = av_rescale_q(enc_pkt.dts, time_base, time_base_q);
//    //计算实际视频的播放时间
//    if (count == 0) {
//        startTime = av_gettime();
//    }
//    int64_t now_time = av_gettime() - startTime;
//    __android_log_print(ANDROID_LOG_WARN, "eric", "delt time :%lld", (pts_time - now_time));
//    if (pts_time > now_time) {
//        //睡眠一段时间（目的是让当前视频记录的播放时间与实际时间同步）
//        av_usleep((unsigned int) (pts_time - now_time));
//    }

    ret = av_interleaved_write_frame(ofmt_ctx, &enc_pkt);
    if (ret != 0) {
        loge("av_interleaved_write_frame failed");
    }
    count++;
    env->ReleaseByteArrayElements(buffer_, in, 0);
    return 0;

}

/**
 * 释放资源
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_ffmpeg_FFmpegHandle_close(JNIEnv *env, jobject instance) {
    if (video_st)
        avcodec_close(video_st->codec);
    if (ofmt_ctx) {
        avio_close(ofmt_ctx->pb);
        avformat_free_context(ofmt_ctx);
        ofmt_ctx = NULL;
    }
    return 0;
}