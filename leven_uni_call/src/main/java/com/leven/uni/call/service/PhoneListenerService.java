package com.leven.uni.call.service;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONObject;
import com.leven.uni.call.tools.ThreadTools;
import com.leven.uni.call.utils.LogUtils;
import com.leven.uni.call.utils.ResultJsonObject;
import com.leven.uni.call.utils.WeexSourcePath;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leven.uni.call.UniJSCallback;

public class PhoneListenerService {
    private UniJSCallback phoneListenerCallback;
    private final Context mContext;
    private final Activity activity;
    private String mPhoneNumber;
    //当前正在通话的电话号码
    private String callingPhoneNumber;
    private int lastCallState = TelephonyManager.CALL_STATE_IDLE;
    //是否已注册监听
    private boolean isListener;
    //来电时间戳
    private long inComingTime = 0;
    //去电或通话中时间戳
    private long runningTime = 0;
    //挂断电话时间戳
    private long closeTime = 0;
    //是否是来电
    private boolean isIncoming = false;
    //检测是否在通话中的任务
    private Handler checkCallingHandler;
    //电话管理器
    private TelephonyManager telephonyManager;
    private boolean isCheckingIdle = false;


    public PhoneListenerService(Context context) {
        mContext = context;
        activity = (Activity) context;
    }

    /**
     * 注册电话通知
     */
    public void registerPhoneListener(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        phoneListenerCallback = callback;
        if (phoneListenerCallback != null) {
            try {
                //创建录音文件夹
                String audioDir = WeexSourcePath.audioPath;
                File audioDirFile = new File(audioDir);
                if (!audioDirFile.exists() && !audioDirFile.mkdirs()) {
                    throw new Exception("创建录音文件夹失败");
                }
                if (isListener) {
                    throw new Exception("已注册监听，请不要重复注册");
                }
                //注册广播
                IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
                filter.setPriority(Integer.MAX_VALUE);
                mContext.registerReceiver(PhoneStateBroadcastReceiver, filter);
                isListener = true;
                JSONObject result = resultJsonObject.returnSuccess("注册监听成功");
                phoneListenerCallback.invokeAndKeepAlive(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                phoneListenerCallback.invoke(returnResult);
            }
        }

    }

    /**
     * 取消监听
     */
    public void unRegisterPhoneListener() {
        phoneListenerCallback = null;
        if (mContext != null) {
            isListener = false;
            mContext.unregisterReceiver(PhoneStateBroadcastReceiver);
        }
    }

    /**
     * 挂断电话
     */
    public void endCall() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限：android.permission.ANSWER_PHONE_CALLS");
            }
            telecomManager.endCall();
        } else {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            Class<?> classTelephony = Class.forName(tm.getClass().getName());
            Method methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony");
            methodGetITelephony.setAccessible(true);
            Object telephonyInterface = methodGetITelephony.invoke(tm);
            Class<?> telephonyInterfaceClass = Class.forName(telephonyInterface.getClass().getName());
            Method methodEndCall = telephonyInterfaceClass.getDeclaredMethod("endCall");
            methodEndCall.invoke(telephonyInterface);
        }
    }

    /**
     * 接听电话
     */
    public void acceptRingingCall() {
        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telecomManager.acceptRingingCall();
        }
    }

    /**
     * 拨打电话
     */
    public void callPhone(String number) {

    }

    //获取音频时长
    private int getDuration(String path) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(path);
            player.prepare();//缓冲
            int duration = player.getDuration();
            player.release();//释放资源
            return duration;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 注册电话状态广播
     */
    BroadcastReceiver PhoneStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            if (phoneListenerCallback != null) {
                try {
                    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                    if (TextUtils.isEmpty(phoneNumber)) {
                        return;
                    }
                    mPhoneNumber = phoneNumber;
                    long thisTime = System.currentTimeMillis();
                    if (state != null && state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        //保持屏幕常亮
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        isIncoming = true;
                        lastCallState = TelephonyManager.CALL_STATE_RINGING;
                        // 振铃
                        System.out.println("来电话了");
                        resultData.put("isIncoming", isIncoming);
                        resultData.put("status", 1);
                        resultData.put("phoneNumber", mPhoneNumber);
                        System.out.println("来电内容：" + resultData.toString());
                        JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                        phoneListenerCallback.invokeAndKeepAlive(result);
                    } else if (state != null && state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                        if (!TextUtils.isEmpty(callingPhoneNumber)) {
                            return;
                        }
                        //保持屏幕常亮
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        //定时任务监听挂断电话，防止挂断电话后没有监听到
                        checkCallStatus();
                        callingPhoneNumber = mPhoneNumber;
                        runningTime = thisTime;
                        isIncoming = lastCallState == TelephonyManager.CALL_STATE_RINGING;
                        System.out.println("开始通话");
                        resultData.put("isIncoming", isIncoming);
                        resultData.put("status", 2);
                        resultData.put("phoneNumber", mPhoneNumber);
                        System.out.println("开始通话：" + resultData.toString());
                        JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                        phoneListenerCallback.invokeAndKeepAlive(result);
                    } else if (state != null && state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        callingIdle(thisTime);
                    }
                } catch (Exception e) {
                    System.out.println("报错：" + e.getMessage());
                    JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                    phoneListenerCallback.invokeAndKeepAlive(returnResult);
                }
            }
        }
    };

    /**
     * 电话挂断
     */
    private void callingIdle(long thisTime) {
        LogUtils.d("进入挂断电话");
        LogUtils.d("mPhoneNumber=" + mPhoneNumber);
        LogUtils.d("callingPhoneNumber=" + callingPhoneNumber);
        LogUtils.d("isCheckingIdle=" + isCheckingIdle);
        if (TextUtils.isEmpty(mPhoneNumber) || TextUtils.isEmpty(callingPhoneNumber) || isCheckingIdle) {
            return;
        }
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        lastCallState = TelephonyManager.CALL_STATE_IDLE;
        closeTime = thisTime;
        if (checkCallingHandler == null) {
            checkCallingHandler = new Handler();
        }
        System.out.println("mPhoneNumber：" + mPhoneNumber);
        System.out.println("callingPhoneNumber：" + callingPhoneNumber);
        if (!mPhoneNumber.equals(callingPhoneNumber)) {
            resultData.put("status", 6);
            resultData.put("phoneNumber", mPhoneNumber);
            resultData.put("isIncoming", isIncoming);
            System.out.println("通话中来电..." + resultData.toString());
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            phoneListenerCallback.invokeAndKeepAlive(result);
            return;
        }
        isCheckingIdle = true;
        //获取录音文件
        //挂断电话后延迟获取文件
        checkCallingHandler.postDelayed(() -> {
            ThreadTools.getInstance().runOnSubThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //挂断电话和接听电话为同一个电话
                        File recorderFile = PhoneUtils.getInstance(mContext).searchRecorderFile(runningTime);
                        resultData.put("phoneNumber", mPhoneNumber);
                        resultData.put("isIncoming", isIncoming);
                        //如果开启了通话录音
                        if (recorderFile != null) {
                            String audioPath = recorderFile.getAbsolutePath();
                            long time = recorderFile.lastModified();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                BasicFileAttributes attr = Files.readAttributes(recorderFile.toPath(), BasicFileAttributes.class);
                                time = attr.lastAccessTime().toMillis();
                            }
                            resultData.put("status", 3);
                            resultData.put("audioPath", audioPath);
                            resultData.put("duration", getDuration(audioPath));
                            resultData.put("time", time);
                            resultData.put("lastModifiedTime", recorderFile.lastModified());
                            resultData.put("timeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)));
                            resultData.put("lastModifiedTimeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(recorderFile.lastModified())));
                        } else {
                            int status = 3;
                            if (isIncoming) {
                                //呼入
                                boolean isOpen = PhoneUtils.getInstance(mContext).checkIsAutoRecord();
                                if (isOpen) {
                                    status = 4;
                                }
                            } else {
                                //呼出
                                //查找通话记录
                                List<JSONObject> calls = PhoneUtils.getInstance(mContext).getCalls(1, mPhoneNumber, new long[]{}, null);
                                if (!calls.isEmpty()) {
                                    JSONObject call = calls.get(0);
                                    Integer duration = call.getInteger("duration");
                                    if (duration == 0) {
                                        status = 5;
                                    }
                                }
                            }
                            if (status == 3) {
                                resultData.put("duration", thisTime - runningTime);
                            }
                            resultData.put("status", status);
                        }
                        File recorderDirFile = PhoneUtils.getInstance(mContext).getRecorderDirFile();
                        if (recorderDirFile != null) {
                            resultData.put("recorderDirPath", recorderDirFile.getAbsolutePath());
                        }
                        System.out.println("挂断电话：" + resultData.toString());
                        JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                        phoneListenerCallback.invokeAndKeepAlive(result);
                    } catch (Exception e) {
                        JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                        phoneListenerCallback.invokeAndKeepAlive(returnResult);
                    } finally {
                        if (mPhoneNumber.equals(callingPhoneNumber)) {
                            callingPhoneNumber = "";
//                            mPhoneNumber = "";
                        }
                        isCheckingIdle = false;
                        //关闭屏幕常亮
//                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });

        }, 1000);
    }

    /**
     * 定时任务监听电话挂断
     */
    private void checkCallStatus() {
        if (checkCallingHandler == null) {
            checkCallingHandler = new Handler();
        }
        checkCallingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isCallingIdle()) {
                    LogUtils.d("检测到挂断电话");
                    long thisTime = System.currentTimeMillis();
                    callingIdle(thisTime);
                } else {
                    checkCallStatus();
                }
            }
        }, 1000);
    }

    /**
     * 是否挂断电话
     */
    private boolean isCallingIdle() {
        if (telephonyManager == null) {
            telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }
        int status = telephonyManager.getCallState();
//        LogUtils.d("通话状态：" + status);
        return status == TelephonyManager.CALL_STATE_IDLE;
    }


}
