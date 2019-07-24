package com.ts.app3;

import android.app.Application;

import com.ts.app3.utils.ScreenUtil;

/**
 * Created by 77167 on 2018/7/6.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //屏幕适配
        ScreenUtil.resetDensity(this);
    }
}
