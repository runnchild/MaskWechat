package com.leven.uni.call.service;

import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PhoneConfig {
    //录音路径
    public static final String[] audioRecorderDirsConfig = {
            //华为或荣耀
            "/Sounds/CallRecord/record",
            "/Sounds/CallRecord",
            "/Sounds/CallReCord/record",
            "/Sounds/CallReCord",
            "/Sounds",
            "/record",
            "/Record",
            //小米
            "/MIUI/sound_recorder/call_rec",
            //oppo
            "/Music/Recordings/Call Recordings/ThirdRecord",
            "/Music/Recordings/Call Recordings",
            "/Music/Recordings/Call/Recordings",
            "/Oppo/media/audio/calls",
            "/Recordings/Call Recordings",
            "/Recordings",
            //VIVO
            "/Recordings/Record/Call",
            "/Record/Call",
            //lenovo
            "/Audios/VoiceCall",
            "/Sound_recorder/PhoneRecord",
            "/Sound_recorder/Phone_record",
            "/Audio/Recorder/PhoneRecorder",
            "/Audio/Recorder/Phone_recorder",
            //zte 中兴
            "/voice",
            //meizu 魅族
            "/Recorder"
    };

    //判断是否开启自动录音
    public static final String[] isAutoRecorderConfig = {
            "secure/enable_record_auto_key",
            "system/button_auto_record_call",
            "global/oplus_customize_all_call_audio_record",
            "global/oppo_all_call_audio_record",
            "global/call_record_state_global",
            "system/auto_recorder",
            "global/auto_record_when_calling",
            "global/audio_safe_volume_state",
    };

    //跳转到开启自动录音页面
    public static final String[] audioRecorderPageConfig = {
            "com.android.phone/com.android.phone.MSimCallFeaturesSetting",
            "com.android.phone/com.android.phone.settings.CallRecordSetting",
            "com.android.phone/com.android.phone.OplusCallFeaturesSetting",
            "com.android.phone/com.android.phone.oppo.settings.audiorecord.OppoAudioRecordSettingActivity",
            "com.android.incallui/com.android.incallui.record.CallRecordSetting"
    };
}
