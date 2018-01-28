//
// Created by eric on 2018/1/11.
//
#include <jni.h>
#include <string>
#include <exception>
#include "common.h"

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include <libswresample/swresample.h>
//引入时间
#include "libavutil/time.h"
#include "libavutil/imgutils.h"
}

AVFormatContext *audio_ofmc;
AVStream *audio_st;
AVCodecContext *audio_codec_ctx;
AVCodec *audio_codec;
AVFrame *audio_frame;
AVPacket audio_packet;
uint8_t *audio_frame_buf;
int audio_buf_size = 0;
int audio_frame_count = 0;
int ret = 0;
SwrContext *pSwrCtx = NULL;

#include <iostream>

using namespace std;

/*
 * =============================================================
 *
 *             编码pcm文件
 *
 * =============================================================
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_encodePcmFile(JNIEnv *env, jobject instance,
                                                                  jstring souPath_,
                                                                  jstring tarPath_) {
    av_log_set_callback(log_callback_null);
    const char *souPath = env->GetStringUTFChars(souPath_, 0);
    const char *tarPath = env->GetStringUTFChars(tarPath_, 0);

    av_log(NULL, AV_LOG_DEBUG, "%s\n%s", souPath, tarPath);
    // TODO
    AVFormatContext *pFormatCtx;
    AVOutputFormat *fmt;
    AVStream *audio_st;
    AVCodecContext *pCodecCtx;
    AVCodec *pCodec;

    uint8_t *frame_buf;
    AVFrame *frame;
    int size;

    FILE *in_file = fopen(souPath, "rb");    //音频PCM采样数据
    const char *out_file = tarPath;                    //输出文件路径

    AVSampleFormat inSampleFmt = AV_SAMPLE_FMT_S16;
    AVSampleFormat outSampleFmt = AV_SAMPLE_FMT_FLTP;
    const int sampleRate = 44100;
    const int channels = 2;
    const int sampleByte = 2;
    int readSize;

    av_register_all();

    avformat_alloc_output_context2(&pFormatCtx, NULL, NULL, out_file);
    fmt = pFormatCtx->oformat;

    //注意输出路径
    if (avio_open(&pFormatCtx->pb, out_file, AVIO_FLAG_READ_WRITE) < 0) {
        av_log(NULL, AV_LOG_ERROR, "%s", "输出文件打开失败！\n");
        return -1;
    }
    //pCodec = avcodec_find_encoder_by_name("libfdk_aac");
    pCodec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    if (!pCodec) {
        av_log(NULL, AV_LOG_ERROR, "%s", "没有找到合适的编码器！");
        return -1;
    }
    audio_st = avformat_new_stream(pFormatCtx, pCodec);
    if (audio_st == NULL) {
        av_log(NULL, AV_LOG_ERROR, "%s", "avformat_new_stream error");
        return -1;
    }
    pCodecCtx = audio_st->codec;
    pCodecCtx->codec_id = fmt->audio_codec;
    pCodecCtx->codec_type = AVMEDIA_TYPE_AUDIO;
    pCodecCtx->sample_fmt = outSampleFmt;
    pCodecCtx->sample_rate = sampleRate;
    pCodecCtx->channel_layout = AV_CH_LAYOUT_STEREO;
    pCodecCtx->channels = av_get_channel_layout_nb_channels(pCodecCtx->channel_layout);
    pCodecCtx->bit_rate = 64000;

    //输出格式信息
    av_dump_format(pFormatCtx, 0, out_file, 1);
    ///2 音频重采样 上下文初始化
    SwrContext *asc = NULL;
    asc = swr_alloc_set_opts(asc,
                             av_get_default_channel_layout(channels), outSampleFmt,
                             sampleRate,//输出格式
                             av_get_default_channel_layout(channels), inSampleFmt, sampleRate, 0,
                             0);//输入格式
    if (!asc) {
        av_log(NULL, AV_LOG_ERROR, "%s", "swr_alloc_set_opts failed!");
        return -1;
    }
    ret = swr_init(asc);

    if (avcodec_open2(pCodecCtx, pCodec, NULL) < 0) {
        av_log(NULL, AV_LOG_ERROR, "%s", "编码器打开失败！\n");
        return -1;
    }
    frame = av_frame_alloc();
    frame->nb_samples = pCodecCtx->frame_size;
    frame->format = pCodecCtx->sample_fmt;
    av_log(NULL, AV_LOG_DEBUG, "sample_rate:%d,frame_size:%d, channels:%d", sampleRate,
           frame->nb_samples, frame->channels);
    //编码每一帧的字节数
    size = av_samples_get_buffer_size(NULL, pCodecCtx->channels, pCodecCtx->frame_size,
                                      pCodecCtx->sample_fmt, 1);
    frame_buf = (uint8_t *) av_malloc(size);
    //一次读取一帧音频的字节数
    readSize = frame->nb_samples * channels * sampleByte;
    char *buf = new char[readSize];

    avcodec_fill_audio_frame(frame, pCodecCtx->channels, pCodecCtx->sample_fmt,
                             (const uint8_t *) frame_buf, size, 1);

    audio_st->codecpar->codec_tag = 0;
    audio_st->time_base = audio_st->codec->time_base;
    //从编码器复制参数
    avcodec_parameters_from_context(audio_st->codecpar, pCodecCtx);

    //写文件头
    avformat_write_header(pFormatCtx, NULL);
    AVPacket pkt;
    av_new_packet(&pkt, size);
    int apts = 0;

    for (int i = 0;; i++) {
        //读入PCM
        if (fread(buf, 1, readSize, in_file) < 0) {
            printf("文件读取错误！\n");
            return -1;
        } else if (feof(in_file)) {
            break;
        }
        frame->pts = apts;
        AVRational av;
        av.num = 1;
        av.den = sampleRate;
        apts += av_rescale_q(frame->nb_samples, av, pCodecCtx->time_base);
        int got_frame = 0;
        //重采样源数据
        const uint8_t *indata[AV_NUM_DATA_POINTERS] = {0};
        indata[0] = (uint8_t *) buf;
        int len = swr_convert(asc, frame->data, frame->nb_samples, //输出参数，输出存储地址和样本数量
                              indata, frame->nb_samples
        );
        //编码
        int ret = avcodec_send_frame(pCodecCtx, frame);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "%s", "avcodec_send_frame error\n");
        }

        ret = avcodec_receive_packet(pCodecCtx, &pkt);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "%s", "avcodec_receive_packet！error \n");
            printAvError(ret);
            continue;
        }
        pkt.stream_index = audio_st->index;
        av_log(NULL, AV_LOG_DEBUG, "第%d帧", i);
        pkt.pts = av_rescale_q(pkt.pts, pCodecCtx->time_base, audio_st->time_base);
        pkt.dts = av_rescale_q(pkt.dts, pCodecCtx->time_base, audio_st->time_base);
        pkt.duration = av_rescale_q(pkt.duration, pCodecCtx->time_base, audio_st->time_base);
        ret = av_write_frame(pFormatCtx, &pkt);
        if (ret < 0) {
            av_log(NULL, AV_LOG_ERROR, "av_write_frame error!");
        }
        av_packet_unref(&pkt);
    }
    //写文件尾
    av_write_trailer(pFormatCtx);
    //清理
    avcodec_close(audio_st->codec);
    av_free(frame);
    av_free(frame_buf);
    avio_close(pFormatCtx->pb);
    avformat_free_context(pFormatCtx);

    fclose(in_file);
    av_log(NULL, AV_LOG_DEBUG, "success !");
    env->ReleaseStringUTFChars(souPath_, souPath);
    env->ReleaseStringUTFChars(tarPath_, tarPath);
    return 0;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_initAudio(JNIEnv *env, jobject instance,
                                                              jstring url_) {
    av_log_set_callback(log_callback_null);
    const char *url = env->GetStringUTFChars(url_, 0);
    logd(url);
    logd("start init");
    av_register_all();

    ret = avformat_alloc_output_context2(&audio_ofmc, NULL, "flv", url);
    if (ret < 0) {
        loge("avformat_alloc_output_context2 error\n");
        printAvError(ret);
        return ret;
    }
    //output encoder initialize
//    audio_codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
    audio_codec = avcodec_find_decoder_by_name("libfdk_aac");
    if (!audio_codec) {
        loge("Can not find encoder!\n");
        return -1;
    }

    audio_codec_ctx = avcodec_alloc_context3(audio_codec);
    if (!audio_codec_ctx) {
        loge("avcodec_alloc_context3 error\n");
        return -1;
    }
    audio_codec_ctx->codec_id = audio_codec->id;
    audio_codec_ctx->codec_type = AVMEDIA_TYPE_AUDIO;
    audio_codec_ctx->sample_fmt = AV_SAMPLE_FMT_S16;
    audio_codec_ctx->sample_rate = 44100;
    audio_codec_ctx->channel_layout = AV_CH_LAYOUT_STEREO;
    audio_codec_ctx->channels = av_get_channel_layout_nb_channels(audio_codec_ctx->channel_layout);
    audio_codec_ctx->bit_rate = 64000;
    audio_codec_ctx->frame_size = 1024;
    ret = avcodec_open2(audio_codec_ctx, audio_codec, NULL);
    if (ret < 0) {
        loge("aac avcodec open fail");
        printAvError(ret);
        return ret;
    }
    //Show some information
    audio_st = avformat_new_stream(audio_ofmc, audio_codec);
    if (!audio_st) {
        loge("avformat_new_stream error\n");
        return -1;
    }
    //Open output URL
    if (avio_open(&audio_ofmc->pb, url, AVIO_FLAG_READ_WRITE) < 0) {
        loge("Failed to open output file!\n");
        return -1;
    }
    avcodec_parameters_from_context(audio_st->codecpar, audio_codec_ctx);

    audio_frame = av_frame_alloc();
    audio_frame->nb_samples = audio_codec_ctx->frame_size;
    audio_frame->format = audio_codec_ctx->sample_fmt;


    audio_buf_size = av_samples_get_buffer_size(NULL, audio_codec_ctx->channels,
                                                audio_codec_ctx->frame_size,
                                                audio_codec_ctx->sample_fmt, 1);
    __android_log_print(ANDROID_LOG_WARN, "eric", "%d,%d,%d", audio_buf_size,
                        audio_codec_ctx->frame_size, audio_codec_ctx->channels);
    audio_frame_buf = (uint8_t *) av_malloc((size_t) audio_buf_size);
    ret = avcodec_fill_audio_frame(audio_frame, audio_codec_ctx->channels,
                                   audio_codec_ctx->sample_fmt,
                                   (const uint8_t *) audio_frame_buf, audio_buf_size, 1);
    if (ret < 0) {
        printAvError(ret);
        loge("avcodec_fill_audio_frame error");
        return ret;
    }
    av_new_packet(&audio_packet, audio_buf_size);
    //Write File Header
    ret = avformat_write_header(audio_ofmc, NULL);
    if (ret < 0) {
        loge("avformat_write_header error");
        return ret;
    }

    pSwrCtx = swr_alloc_set_opts(pSwrCtx,
                                 audio_codec_ctx->channel_layout,
                                 AV_SAMPLE_FMT_FLTP,
                                 audio_codec_ctx->sample_rate,

                                 audio_codec_ctx->channel_layout,
                                 AV_SAMPLE_FMT_S16,
                                 audio_codec_ctx->sample_rate,
                                 0, NULL);
    ret = swr_init(pSwrCtx);
    if (ret < 0) {
        printAvError(ret);
        loge("swr_init error");
    }
    env->ReleaseStringUTFChars(url_, url);
    return ret;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_encodeAudio(JNIEnv *env, jobject instance,
                                                                jbyteArray buffer_) {
    int ret = 0;
    jbyte *buffer = env->GetByteArrayElements(buffer_, NULL);
    jsize theArrayLengthJ = env->GetArrayLength(buffer_);
    __android_log_print(ANDROID_LOG_WARN, "eric", "size: %d", theArrayLengthJ);
//    ret = transSample((const uint8_t *) buffer, audio_frame);
    audio_frame->data[0] = audio_frame_buf;
    //  memccpy(audio_frame->data[0],buffer,theArrayLengthJ);
//    memcpy(audio_frame->data[0], buffer, length);
    audio_frame->pts = (audio_frame_count++) * 100;
    ret = avcodec_send_frame(audio_codec_ctx, audio_frame);
    if (ret != 0) {
        printAvError(ret);
        loge("avcodec_send_frame error");
        return -1;
    }
    //获取编码后的数据
    ret = avcodec_receive_packet(audio_codec_ctx, &audio_packet);
    if (ret < 0) {
        logw("Failed to encode!\n");
        return -1;
    }

    ret = av_write_frame(audio_ofmc, &audio_packet);
    if (ret < 0) {
        logw("write error");
    }
    av_frame_free(&audio_frame);

    env->ReleaseByteArrayElements(buffer_, buffer, 0);
    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_close(JNIEnv *env, jobject instance) {
    if (audio_st)
        avcodec_close(audio_st->codec);
    if (audio_ofmc) {
        avio_close(audio_ofmc->pb);
        avformat_free_context(audio_ofmc);
        audio_ofmc = NULL;
    }
    return 0;
}