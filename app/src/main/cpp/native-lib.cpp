#include <jni.h>
#include <string>

//这里很重要，FFmpeg是C语言写的，如果不使用extern "C"则
//会出现链接出错

extern "C" {
#include "libavcodec/avcodec.h"
}

extern "C"
JNIEXPORT jstring

JNICALL
Java_com_wangheart_ffmpegdemo_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */thiz) {
    char info[10000] = {0};
//    avcodec_version();
    sprintf(info, "%s\n", avcodec_configuration());
    return env->NewStringUTF(info);
}
