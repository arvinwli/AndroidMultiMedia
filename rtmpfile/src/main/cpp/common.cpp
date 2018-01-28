//
// Created by Administrator on 2018/1/28.
//

#include "common.h"
extern "C" {
#include "libavcodec/avcodec.h"
}


int printAvError(int errNum) {
    char buf[1024];
    //获取错误信息
    av_strerror(errNum, buf, sizeof(buf));
    av_log(NULL,AV_LOG_ERROR,"发生错误:%s",buf);
    return -1;
}
void log_callback_null(void *ptr, int level, const char *fmt, va_list vl) {
    static int print_prefix = 1;
    static char prev[1024];
    char line[1024];
    av_log_format_line(ptr, level, fmt, vl, line, sizeof(line), &print_prefix);

    strcpy(prev, line);

    if (level <= AV_LOG_ERROR) {
        loge(line);
    } else if (level <= AV_LOG_WARNING) {
        logw(line);
    } else {
        logd(line);
    }
}