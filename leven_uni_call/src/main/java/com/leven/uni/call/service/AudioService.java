package com.leven.uni.call.service;

import android.os.Build;
import android.telecom.InCallService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.M)
public class AudioService extends InCallService {
    private static AudioService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static AudioService getInstance(){
        return instance;
    }
}
