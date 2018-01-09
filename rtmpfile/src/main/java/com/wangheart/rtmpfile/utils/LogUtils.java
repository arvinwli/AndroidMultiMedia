package com.wangheart.rtmpfile.utils;

import android.util.Log;

/**
 * Author : eric
 * CreateDate : 2017/11/22  17:44
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class LogUtils {
    public final static String TAG = "eric";

    public static void v(String content) {
        Log.v(TAG, content);
    }
    public static void d(String content) {
        Log.d(TAG, content);
    }
    public static void e(String content) {
        Log.e(TAG, content);
    }

    public static void w(String content) {
        Log.w(TAG, content);
    }
}
