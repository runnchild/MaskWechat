package com.leven.uni.call.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class NotificationWrapper extends ContextWrapper {
    private NotificationManager manager;
    private final String id;
    private final String name;
    private NotificationChannel channel;
    @SuppressLint("StaticFieldLeak")
    private static NotificationWrapper instance;
    public NotificationWrapper(Context context) {
        super(context);
        id = context.getPackageName();
        name = context.getPackageName();
    }

    public static NotificationWrapper getInstance(Context context) {
        if(instance == null){
            instance = new NotificationWrapper(context);
        }
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void createNotificationChannel() {
        if (channel == null) {
            channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setVibrationPattern(new long[]{0});
            channel.setSound(null, null);
            getManager().createNotificationChannel(channel);
        }
    }

    public NotificationManager getManager() {
        if (manager == null) {
            manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        return manager;
    }
}
