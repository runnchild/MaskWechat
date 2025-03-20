package com.leven.uni.call.tools;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;

import com.leven.uni.call.service.ForegroundService;
import com.leven.uni.call.service.NotificationWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ForegroundServiceTools {
    public static int ACTION_NOTIFICATION_CODE = 500;
    private Context context;
    private final Activity activity;
    @SuppressLint("StaticFieldLeak")
    private static ForegroundServiceTools instance;
    private Intent foregroundServiceIntent;

    public ForegroundServiceTools(Context context){
        this.context = context;
        this.activity = (Activity) context;
    }

    public static ForegroundServiceTools getInstance(Context context) {
        if(instance == null){
            instance = new ForegroundServiceTools(context);
        }
        return instance;
    }

    /**
     * 创建前台通知
     */
    public void startNotification(String title, String content, String icon){
        if(foregroundServiceIntent == null){
            foregroundServiceIntent = new Intent(context, ForegroundService.class);
            foregroundServiceIntent.putExtra("title", title);
            foregroundServiceIntent.putExtra("content", content);
            foregroundServiceIntent.putExtra("icon", icon);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationWrapper.getInstance(context).createNotificationChannel();
            context.startForegroundService(foregroundServiceIntent);
        }else{
            context.startService(foregroundServiceIntent);
        }
    }

    /**
     * 关闭前台通知
     */
    public void stopNotification(){
        if(foregroundServiceIntent != null){
            //关闭前台通知
            context.stopService(foregroundServiceIntent);
        }
    }

    //前台通知权限是否开启
    public boolean hasNotificationPermission(){
        if (Build.VERSION.SDK_INT >= 24) {
            return NotificationWrapper.getInstance(context).getManager().areNotificationsEnabled();
        } else {
            return true;
        }
    }

    //跳转到通知权限页面
    public void toNotificationPermissionPage(){
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        }else{
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
        }
        activity.startActivityForResult(intent, ACTION_NOTIFICATION_CODE);
    }
}
