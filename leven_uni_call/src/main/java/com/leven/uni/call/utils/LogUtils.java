package com.leven.uni.call.utils;

import android.util.Log;


public class LogUtils {
    private static String LogTag = LogUtils.class.getSimpleName();
    /**
     * 写日志
     */
    public static void d(String TAG, String s){
//        if(BuildConfig.DEBUG){
            Log.d(TAG, s);
//        }
    }

    /**
     * 写日志
     */
    public static void d(String s){
        d(LogTag, s);
    }
}
