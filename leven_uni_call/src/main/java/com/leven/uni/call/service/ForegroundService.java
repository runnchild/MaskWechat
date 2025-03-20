package com.leven.uni.call.service;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.leven.uni.call.tools.ResourceTools;

public class ForegroundService extends Service {
    private Intent notificationIntent;
    private final int pid = Process.myPid();
    private String title;
    private String content;
    private String icon;
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null){
            title = intent.getStringExtra("title");
            content = intent.getStringExtra("content");
            icon = intent.getStringExtra("icon");
        }
        startService();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationIntent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(pid);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startService(){
        // 创建一个PendingIntent，以便将前台服务的通知与服务相关联
        if(notificationIntent == null){
            notificationIntent = new Intent(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            notificationIntent.setClass(getApplicationContext(), MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                    notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            // 创建前台服务通知
            Application application = getApplication();
            ApplicationInfo applicationInfo = application.getApplicationInfo();
            CharSequence notificationTitle = applicationInfo.loadLabel(getApplicationContext().getPackageManager());
            if(!TextUtils.isEmpty(title)){
                notificationTitle = title;
            }
            CharSequence notificationContent = notificationTitle + "正在运行";
            if(!TextUtils.isEmpty(content)){
                notificationContent = content;
            }

            int smallIcon = applicationInfo.icon;
            if(!TextUtils.isEmpty(icon) && ResourceTools.isMipmapResourceExists(getApplicationContext(), icon) > 0){
                smallIcon = ResourceTools.isMipmapResourceExists(getApplicationContext(), icon);
            }
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), getApplicationContext().getPackageName())
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationContent)
                    .setSmallIcon(smallIcon)
                    .setContentIntent(pendingIntent)
                    .build();

            // 启动前台服务
            startForeground(pid, notification);
        }
    }
}
