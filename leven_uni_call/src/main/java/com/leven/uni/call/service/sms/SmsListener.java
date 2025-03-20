package com.leven.uni.call.service.sms;

public interface SmsListener {
    void onMessage(String number, String content);
    void onSuccess();
}
