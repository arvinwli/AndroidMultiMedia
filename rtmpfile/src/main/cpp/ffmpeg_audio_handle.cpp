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

int avError1(int errNum) {
    char buf[1024];
    //获取错误信息
    av_strerror(errNum, buf, sizeof(buf));
    loge(string().append("发生异常：").append(buf).c_str());
    return -1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_initAudio(JNIEnv *env, jobject instance,
                                                              jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    logd(url);
    logd("start init");
    av_register_all();

    ret = avformat_alloc_output_context2(&audio_ofmc, NULL, "flv", url);
    if (ret < 0) {
        loge("avformat_alloc_output_context2 error\n");
        avError1(ret);
        return ret;
    }
    //output encoder initialize
    audio_codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
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
    audio_codec_ctx->sample_fmt = AV_SAMPLE_FMT_FLTP;
    audio_codec_ctx->sample_rate = 44100;
    audio_codec_ctx->channel_layout = AV_CH_LAYOUT_STEREO;
    audio_codec_ctx->channels = av_get_channel_layout_nb_channels(audio_codec_ctx->channel_layout);
    audio_codec_ctx->bit_rate = 64000;
    audio_codec_ctx->frame_size = 1024;
    ret = avcodec_open2(audio_codec_ctx, audio_codec, NULL);
    if (ret < 0) {
        avError1(ret);
        logw("aac avcodec open fail");
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
                                   (const uint8_t *) audio_frame_buf, audio_buf_size, 0);
    if (ret < 0) {
        avError1(ret);
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
        avError1(ret);
        loge("swr_init error");
    }
    env->ReleaseStringUTFChars(url_, url);
    return ret;
}

int transSample(const uint8_t *buf, AVFrame *out_frame) {
    int ret;
    if (pSwrCtx != NULL) {

        ret = av_samples_alloc(out_frame->data,
                               &out_frame->linesize[0],
                               audio_codec_ctx->channels,
                               out_frame->nb_samples,
                               audio_codec_ctx->sample_fmt, 0);
        __android_log_print(ANDROID_LOG_WARN, "eric", "%d", out_frame->linesize[0]);
        if (ret < 0) {
            av_log(NULL, AV_LOG_WARNING, "[%s.%d %s() Could not allocate samples Buffer\n",
                   __FILE__, __LINE__, __FUNCTION__);
            return -1;
        }
        logw("start onvert");
        //注意这里，out_count和in_count是samples单位，不是byte
        //所以这样av_get_bytes_per_sample(in_fmt_ctx->streams[audio_index]->codec->sample_fmt) * src_nb_samples是错的
        ret = swr_convert(pSwrCtx, out_frame->data, out_frame->nb_samples, (const uint8_t **) buf,
                         1764);
        if (ret < 0) {
            logw("onvert error");
            return ret;
        }

    } else {
        printf("pSwrCtx with out init!\n");
        return -1;
    }
    return 0;
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_wangheart_rtmpfile_audio_FFmpegAudioHandle_encodeAudio(JNIEnv *env, jobject instance,
                                                                jbyteArray buffer_) {
    int ret = 0;
    jbyte *buffer = env->GetByteArrayElements(buffer_, NULL);
    jsize theArrayLengthJ = env->GetArrayLength(buffer_);
    __android_log_print(ANDROID_LOG_WARN, "eric", "size: %d", theArrayLengthJ);
    ret = transSample((const uint8_t *) buffer, audio_frame);

    if (ret < 0) {
        avError1(ret);
        loge("transSample error");
        return -1;
    }
//    memcpy(audio_frame->data[0], buffer, length);
//    audio_frame->pts = (audio_frame_count++) * 100;
    ret = avcodec_send_frame(audio_codec_ctx, audio_frame);
    if (ret != 0) {
        avError1(ret);
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