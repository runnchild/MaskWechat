package com.leven.uni.call.service.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVER_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String format = intent.getStringExtra("format");
        String action = intent.getAction();
        if (action.equals(SMS_RECEIVER_ACTION)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    SmsMessage[] messages = new SmsMessage[pdus.length];
                    if (messages.length > 0) {
                        for (int i = 0; i < messages.length; ++i) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                            }
                        }
                        String number = "";
                        StringBuilder content = new StringBuilder();
                        for (SmsMessage message : messages) {
                            number = message.getDisplayOriginatingAddress();
                            content.append(message.getDisplayMessageBody());
                        }
                        SmsMessage message = messages[0];
                        //收到短信通知
                        Intent intentData = new Intent("SMS_RECEIVE_DATA");
                        intentData.putExtra("number", number);
                        intentData.putExtra("content", content.toString());
                        context.sendBroadcast(intentData);
                    }
                }
            }
        }
    }
}
