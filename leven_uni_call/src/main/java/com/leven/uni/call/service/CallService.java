package com.leven.uni.call.service;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.List;

//拨号服务
public class CallService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String phoneNumber = intent.getStringExtra("phoneNumber");
        int slotId = intent.getIntExtra("slotId", 0);
        boolean isTelephone = intent.getBooleanExtra("isTelephone", false);
        dialPhoneNumber(slotId, phoneNumber, isTelephone);
        return START_NOT_STICKY;
    }

    /**
     * 拨号服务
     * @param slotId   卡槽
     * @param callPhoneNumber   电话号码
     * @param isTelephone   是否是座机
     */
    private void dialPhoneNumber(int slotId, String callPhoneNumber, boolean isTelephone) {
        callPhoneNumber = callPhoneNumber.replaceAll("-", "");
        if (isTelephone) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + callPhoneNumber)));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri data = Uri.parse("tel:" + callPhoneNumber);
        intent.setData(data);
        TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
                PhoneAccountHandle phoneAccountHandle = null;
                if (phoneAccountHandleList.size() >= 2) {
                    phoneAccountHandle = phoneAccountHandleList.get(slotId);
                }
                if(phoneAccountHandle != null){
                    intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
                }
            }
        }
        startActivity(intent);
    }
}
