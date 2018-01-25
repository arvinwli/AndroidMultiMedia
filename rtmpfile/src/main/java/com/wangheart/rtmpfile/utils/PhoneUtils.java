package com.wangheart.rtmpfile.utils;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Surface;

/**
 * Created by Administrator on 2018/1/25.
 */

public class PhoneUtils {
    private static int width;
    private static int height;

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public static void init(Activity context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        context.getWindow().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        width= displayMetrics.widthPixels;
        height = displayMetrics.heightPixels;
    }

    public static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

}
