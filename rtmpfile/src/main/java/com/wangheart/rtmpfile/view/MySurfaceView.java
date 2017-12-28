package com.wangheart.rtmpfile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Author : eric
 * CreateDate : 2017/10/9  16:36
 * Email : ericli_wang@163.com
 * Version : 2.0
 * Desc :
 * Modified :
 */

public class MySurfaceView extends SurfaceView {
    private SurfaceHolder mHolder;

    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mHolder=getHolder();
    }
}
