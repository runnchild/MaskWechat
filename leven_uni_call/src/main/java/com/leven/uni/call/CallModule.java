package com.leven.uni.call;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONObject;
import com.leven.uni.call.service.PhoneConfig;
import com.leven.uni.call.service.PhoneListenerService;
import com.leven.uni.call.service.PhoneUtils;
import com.leven.uni.call.service.sms.SmsListener;
import com.leven.uni.call.service.sms.SmsTools;
import com.leven.uni.call.tools.ContactTools;
import com.leven.uni.call.tools.ForegroundServiceTools;
import com.leven.uni.call.tools.ThreadTools;
import com.leven.uni.call.utils.ErrorEnum;
import com.leven.uni.call.utils.FileUtil;
import com.leven.uni.call.utils.LevenException;
import com.leven.uni.call.utils.ResultJsonObject;
import com.leven.uni.call.utils.TestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallModule extends BaseModule {
    private UniJSCallback phoneListenerCallback;
    private PhoneListenerService phoneListenerService;
    //申请权限列表
    private final String[] permissions = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    //申请权限结果
    private final int ACTION_REQUEST_PERMISSIONS_PHONE_CODE = 100;
    //获取通话记录的回调
    private UniJSCallback callLogCallback;
    //获取通话记录权限结果
    private final int ACTION_REQUEST_PERMISSIONS_CALL_LOG_CODE = 101;
    //拨打电话权回调
    private UniJSCallback callPhoneCallback;
    //拨打电话的申请权限结果
    private final int ACTION_REQUEST_PERMISSIONS_CALL_PHONE_CODE = 102;
    //根据条件获取通话记录回调
    private UniJSCallback callLogFilterCallback;
    //根据条件获取通话记录的条件
    private JSONObject callLogFilterJsonObject;
    //根据条件获取通话记录权限结果
    private final int ACTION_REQUEST_PERMISSIONS_CALL_LOG_FILTER_CODE = 103;
    //拨打电话的权限
    private final String[] callPhonePermission = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
    };
    //判断是否是双卡
    private final int ACTION_REQUEST_PERMISSIONS_IS_MULTI_SIM_CODE = 104;
    //判断是否是双卡回调
    private UniJSCallback isMultiSimCallback;
    //拨打电话的电话号码
    private String callPhoneNumber = "";
    //拨打电话选择的卡槽
    private int callPhoneSlot = 1;
    //短信监听的回调
    private UniJSCallback smsListenerCallback;
    //获取短信列表
    private UniJSCallback getSmsCallback;
    private JSONObject getSmsJson;
    //挂断电话需要申请的权限
    private final String[] endCallPermission = {
            Manifest.permission.ANSWER_PHONE_CALLS,
    };
    //挂断电话
    private final int ACTION_REQUEST_PERMISSIONS_END_CALL_CODE = 105;
    //接听电话
    private final int ACTION_REQUEST_PERMISSIONS_ANSWER_PHONE_CODE = 108;
    //挂断电话的回调
    private UniJSCallback endCallCallback;
    //接听电话的回调
    private UniJSCallback answerPhoneCallback;
    //获取电话号码申请权限
    private final String[] phoneNumberPermissions = {
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS"
    };
    //获取电话号码权限状态码
    private static final int ACTION_REQUEST_PERMISSIONS_PHONE_NUMBERS_CODE = 106;
    //获取手机号码的回调
    private UniJSCallback phoneNumberCallback;
    //是否是座机
    private boolean isTelephone = false;
    //打开所有文件访问权限回调
    private UniJSCallback allFilesPermissionsCallback;
    //所有文件访问权限状态码
    private static final int ACTION_REQUEST_PERMISSIONS_ALL_FILES_CODE = 107;
    //发送短信权限
    private final String[] sendSmsPermissions = {
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_PHONE_NUMBERS"
    };
    //发送短信权限状态码
    private static final int ACTION_REQUEST_PERMISSIONS_SEND_SMS_CODE = 108;
    //发送短信回调
    private UniJSCallback sendSmsCallback;
    //发送短信的数据
    private static JSONObject sendSmsJson;
    //获取通讯录所需权限
    private String[] contactPermissions = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
    };
    //获取通讯录权限状态码
    private static final int ACTION_REQUEST_PERMISSIONS_CONTACT_CODE = 109;
    //获取通讯录的回调
    private UniJSCallback contactCallback;
    //获取通讯录参数
    private static JSONObject contactJson;
    //添加通讯录权限状态码
    private static final int ACTION_REQUEST_PERMISSIONS_ADD_CONTACT_CODE = 110;
    //获取通讯录的回调
    private UniJSCallback addContactCallback;
    //获取通讯录参数
    private static JSONObject addContactJson;

    //打开前台通知页面
    private UniJSCallback notificationPermissionCallback;

    //获取通讯录所需权限
    private String[] toggleSpeakerPermissions = {
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };
    private UniJSCallback toggleSpeakerCallback;
    private static final int ACTION_REQUEST_PERMISSIONS_TOGGLE_SPEAKER_CODE = 111;
    private AudioManager audioManager;

    /**
     * 初始化通话录音配置
     */
    
    public void initCallRecorderConfig(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            String[] isAutoRecorderConfig = PhoneConfig.isAutoRecorderConfig;
            String[] audioRecorderPageConfig = PhoneConfig.audioRecorderPageConfig;
            String[] audioRecorderDirsConfig = PhoneConfig.audioRecorderDirsConfig;
            if (jsonObject.containsKey("isAutoRecorderConfig")) {
                isAutoRecorderConfig = jsonObject.getObject("isAutoRecorderConfig", String[].class);
            }
            if (jsonObject.containsKey("audioRecorderPageConfig")) {
                audioRecorderPageConfig = jsonObject.getObject("audioRecorderPageConfig", String[].class);
            }
            if (jsonObject.containsKey("audioRecorderDirsConfig")) {
                audioRecorderDirsConfig = jsonObject.getObject("audioRecorderDirsConfig", String[].class);
            }
            PhoneUtils.getInstance(getContext()).setIsAutoRecorderConfig(isAutoRecorderConfig);
            PhoneUtils.getInstance(getContext()).setAudioRecorderPageConfig(audioRecorderPageConfig);
            PhoneUtils.getInstance(getContext()).setAudioRecorderDirsConfig(audioRecorderDirsConfig);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 检测是否开启通话录音权限
     */
    
    public void checkCallAutoRecorder(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            boolean isOpen = PhoneUtils.getInstance(getContext()).checkIsAutoRecord();
            resultData.put("isOpen", isOpen);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 跳转到开启自动通话录音页面
     */
    
    public void toCallAutoRecorderPage(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            PhoneUtils.getInstance(getContext()).toCallAutoRecorderPage();
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 获取所有录音文件
     */
    
    public void getAllRecorderFiles(UniJSCallback callback) {
        ThreadTools.getInstance().runOnSubThread(new Runnable() {
            @Override
            public void run() {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                try {
                    TestUtils.isTest();
                    List<String> list = PhoneUtils.getInstance(getContext()).getAllRecorderFiles();
                    List<Map<String, Object>> files = new ArrayList<>();
                    if (!list.isEmpty()) {
                        for (String path : list) {
                            Map<String, Object> data = new HashMap<>();
                            File file = new File(path);
                            long time = file.lastModified();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                time = attr.lastAccessTime().toMillis();
                            }
                            data.put("path", file.getAbsolutePath());
                            data.put("time", time);
                            data.put("lastModifiedTime", file.lastModified());
                            data.put("timeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)));
                            data.put("lastModifiedTimeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
                            files.add(data);
                        }
                    }
                    resultData.put("list", list);
                    resultData.put("files", files);
                    JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                    callback.invoke(result);
                } catch (LevenException e) {
                    JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
                    callback.invoke(returnResult);
                } catch (Exception e) {
                    JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                    callback.invoke(returnResult);
                }
            }
        });
    }

    /**
     * 根据条件获取录音文件
     */
    
    public void getRecorderFiles(JSONObject jsonObject, UniJSCallback callback) {
        ThreadTools.getInstance().runOnSubThread(new Runnable() {
            @Override
            public void run() {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                try {
                    TestUtils.isTest();
                    long[] times = new long[]{0, System.currentTimeMillis()};
                    if (jsonObject.containsKey("times")) {
                        times = jsonObject.getObject("times", long[].class);
                    }
                    List<String> list = PhoneUtils.getInstance(getContext()).getRecorderFiles(times);
                    List<Map<String, Object>> files = new ArrayList<>();
                    if (list.size() > 0) {
                        for (String path : list) {
                            Map<String, Object> data = new HashMap<>();
                            File file = new File(path);
                            long time = file.lastModified();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                time = attr.lastAccessTime().toMillis();
                            }
                            data.put("path", file.getAbsolutePath());
                            data.put("time", time);
                            data.put("lastModifiedTime", file.lastModified());
                            data.put("timeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)));
                            data.put("lastModifiedTimeDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
                            files.add(data);
                        }
                    }
                    resultData.put("list", list);
                    resultData.put("files", files);
                    JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                    callback.invoke(result);
                } catch (LevenException e) {
                    JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
                    callback.invoke(returnResult);
                } catch (Exception e) {
                    JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                    callback.invoke(returnResult);
                }
            }
        });
    }

    /**
     * 获取设置的key和value
     */
    
    public void getSettingsKeyValue(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            JSONObject list = PhoneUtils.getInstance(getContext()).getSettingsKeyValue();
            resultData.put("system", list.getObject("system", List.class));
            resultData.put("secure", list.getObject("secure", List.class));
            resultData.put("global", list.getObject("global", List.class));
            resultData.put("manufacturer", list.getString("manufacturer"));
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 注册电话监听
     */
    
    public void registerListener(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            phoneListenerCallback = callback;
            if(phoneListenerService != null){
                throw new LevenException("已注册监听，请不要重复注册");
            }
            //判断是否有权限
            if (checkPermissions(permissions)) {
                startRegisterPhoneListener();
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), permissions, ACTION_REQUEST_PERMISSIONS_PHONE_CODE);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 取消电话监听
     */
    
    public void unRegisterListener(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            if (phoneListenerService == null) {
                throw new LevenException("暂未注册监听");
            }
            phoneListenerService.unRegisterPhoneListener();
            phoneListenerService = null;
            JSONObject result = resultJsonObject.returnSuccess("取消监听成功");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 移动文件
     */
    
    public void moveFile(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("fromFilename") || TextUtils.isEmpty(jsonObject.getString("fromFilename"))) {
                throw new LevenException("复制的文件名不能为空");
            }
            if (!jsonObject.containsKey("toFilename") || TextUtils.isEmpty(jsonObject.getString("toFilename"))) {
                throw new LevenException("目的文件名不能为空");
            }
            //需要拷贝的文件名
            String fromFilename = jsonObject.getString("fromFilename");
            String toFilename = jsonObject.getString("toFilename");
            File fromFile = new File(fromFilename);
            if (!fromFile.exists()) {
                throw new LevenException("移动的文件不存在");
            }
            //开启线程移动文件
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //判断是否复制成功
                        boolean isMoveSuccess = FileUtil.copyFile(fromFile, toFilename);
                        if (!isMoveSuccess) {
                            throw new Exception("移动失败");
                        }
                        //删除源文件
                        FileUtil.deleteFile(fromFilename);
                        JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                        callback.invoke(result);
                    } catch (Exception e) {
                        JSONObject result = resultJsonObject.returnFailed(e.getMessage());
                        callback.invoke(result);
                    }
                }
            }).start();
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 删除文件或目录
     */
    
    public void deleteFile(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("filePath") || TextUtils.isEmpty(jsonObject.getString("filePath"))) {
                throw new LevenException("文件名或路径不能为空");
            }
            //需要拷贝的文件名
            String filePath = jsonObject.getString("filePath");
            File file = new File(filePath);
            if (!file.exists()) {
                throw new LevenException("文件不存在");
            }
            FileUtil.deleteFile(file);
            if (file.exists()) {
                throw new LevenException("删除失败");
            }
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 复制文件
     */
    
    public void copyFile(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("fromFilename") || TextUtils.isEmpty(jsonObject.getString("fromFilename"))) {
                throw new LevenException("复制的文件名不能为空");
            }
            if (!jsonObject.containsKey("toFilename") || TextUtils.isEmpty(jsonObject.getString("toFilename"))) {
                throw new LevenException("目的文件名不能为空");
            }
            //需要拷贝的文件名
            String fromFilename = jsonObject.getString("fromFilename");
            String toFilename = jsonObject.getString("toFilename");
            File fromFile = new File(fromFilename);
            if (!fromFile.exists()) {
                throw new LevenException("复制的文件不存在");
            }
            //开启线程移动文件
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //判断复式的文件夹是否存在
                        boolean isCopySuccess = FileUtil.copyFile(fromFile, toFilename);
                        if (!isCopySuccess) {
                            throw new Exception("复制失败");
                        }
                        JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                        callback.invoke(result);
                    } catch (Exception e) {
                        JSONObject result = resultJsonObject.returnFailed(e.getMessage());
                        callback.invoke(result);
                    }
                }
            }).start();
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 挂断电话
     */
    
    public void endCall(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            if (phoneListenerService == null) {
                throw new LevenException("暂未注册监听");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //申请权限
                endCallCallback = callback;
                ActivityCompat.requestPermissions(getActivity(), endCallPermission, ACTION_REQUEST_PERMISSIONS_END_CALL_CODE);
            } else {
                phoneListenerService.endCall();
                JSONObject result = resultJsonObject.returnSuccess("操作成功");
                callback.invoke(result);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 拨打电话
     */
    
    public void callPhone(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("number") || TextUtils.isEmpty(jsonObject.getString("number"))) {
                throw new LevenException("拨打的电话号码不能为空");
            }
            if (jsonObject.containsKey("slot")) {
                int slot = jsonObject.getInteger("slot");
                if (slot != 1 && slot != 2) {
                    slot = 1;
                }
                callPhoneSlot = slot;
            }
            //拨打的电话号码
            callPhoneNumber = jsonObject.getString("number");
            callPhoneCallback = callback;
            if (jsonObject.containsKey("isTelephone")) {
                isTelephone = jsonObject.getBoolean("isTelephone");
            }
            //判断是否有权限
            if (checkPermissions(callPhonePermission)) {
                startCallPhone();
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), callPhonePermission, ACTION_REQUEST_PERMISSIONS_CALL_PHONE_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 注册短信监听
     */
    
    public void registerSmsListener(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            smsListenerCallback = callback;
            //判断是否有权限
            SmsTools smsTools = SmsTools.getInstance(getContext());
            if (checkPermissions(smsTools.permissions)) {
                smsTools.register(smsListener);
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), smsTools.permissions, smsTools.SMS_PERMISSION_CODE);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 取消短信监听
     */
    
    public void unRegisterSmsListener(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            smsListenerCallback = null;
            SmsTools.getInstance(getContext()).unregister();
            JSONObject result = resultJsonObject.returnSuccess("操作成功");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 获取所有的通话记录
     */
    
    public void getAllCalls(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            callLogCallback = callback;
            //判断是否有权限
            if (checkPermissions(permissions)) {
                startGetAllCalls();
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), permissions, ACTION_REQUEST_PERMISSIONS_CALL_LOG_CODE);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 根据条件查找通话记录
     */
    
    public void getCalls(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("type") || TextUtils.isEmpty(jsonObject.getString("type"))) {
                throw new LevenException("查找类型不能为空");
            }
            int type = jsonObject.getInteger("type");
            if (type == 1 || type == 2) {
                if (!jsonObject.containsKey("value") || TextUtils.isEmpty(jsonObject.getString("value"))) {
                    throw new LevenException("查找的值不能为空");
                }
            }
            callLogFilterJsonObject = jsonObject;
            callLogFilterCallback = callback;
            //判断是否有权限
            if (checkPermissions(permissions)) {
                startGetCalls();
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), permissions, ACTION_REQUEST_PERMISSIONS_CALL_LOG_FILTER_CODE);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 获取是否是双卡
     */
    
    public void isMultiSim(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            isMultiSimCallback = callback;
            //判断是否有权限
            if (checkPermissions(permissions)) {
                startIsMultiSim();
            } else {
                //申请权限
                ActivityCompat.requestPermissions(getActivity(), permissions, ACTION_REQUEST_PERMISSIONS_IS_MULTI_SIM_CODE);
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 获得手机号
     */
    
    public void getPhoneNumber(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                throw new LevenException("当前安卓版本不支持获取手机号，安卓api需大于等于" + Build.VERSION_CODES.LOLLIPOP_MR1);
            }
            phoneNumberCallback = callback;
            if (checkPermissions(phoneNumberPermissions)) {
                startGetPhoneNumbers();
            } else {
                ActivityCompat.requestPermissions(getActivity(), phoneNumberPermissions, ACTION_REQUEST_PERMISSIONS_PHONE_NUMBERS_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 获取当前通话状态
     */
    
    public void getCallState(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            int state = telephonyManager.getCallState();
            resultData.put("state", state);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 检查是否有“所有文件”访问权限
     */
    
    public void checkAllFilesPermission(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                boolean status = Environment.isExternalStorageManager();
                resultData.put("status", status);
            } else {
                String[] permissionsList = {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };
                boolean status = checkPermissions(permissionsList);
                resultData.put("status", status);
            }
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    /**
     * 跳转到所有文件访问权限页面
     */
    
    public void toAllFilesPermissionPage(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            allFilesPermissionsCallback = callback;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                getActivity().startActivityForResult(intent, ACTION_REQUEST_PERMISSIONS_ALL_FILES_CODE);
            } else {
                Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                appIntent.setData(Uri.parse("package:" + getContext().getPackageName()));
                try {
                    getActivity().startActivityForResult(appIntent, ACTION_REQUEST_PERMISSIONS_ALL_FILES_CODE);
                } catch (ActivityNotFoundException ex) {
                    ex.printStackTrace();
                    Intent allFileIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    getActivity().startActivityForResult(allFileIntent, ACTION_REQUEST_PERMISSIONS_ALL_FILES_CODE);
                }
            }
        } catch (LevenException e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(returnResult);
        } catch (Exception e) {
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(returnResult);
        }
    }

    //获取音频时长
    
    public void getDuration(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (!jsonObject.containsKey("path") || TextUtils.isEmpty(jsonObject.getString("path"))) {
                throw new LevenException("音频路径不能为空");
            }
            String path = jsonObject.getString("path");
            if (!path.startsWith("http://") && !path.startsWith("https://")) {
                //本地文件，判断文件是否存在
                File file = new File(path);
                if (!file.exists()) {
                    throw new LevenException("音频文件不存在");
                }
            }
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(path);
            player.prepare();//缓冲
            int duration = player.getDuration();
            player.release();//释放资源
            resultData.put("duration", duration);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.DEFAULT_ERROR, resultData, e.getMessage());
            callback.invoke(result);
        }
    }

    //接听电话
    
    public void answerRingingCall(UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                throw new LevenException("当前版本不支持自动接听");
            }
            if (phoneListenerService == null) {
                throw new LevenException("暂未注册监听");
            }
            if(checkPermissions(endCallPermission)){
                phoneListenerService.acceptRingingCall();
                JSONObject result = resultJsonObject.returnSuccess("操作成功");
                callback.invoke(result);
            }else{
                //申请权限
                answerPhoneCallback = callback;
                ActivityCompat.requestPermissions(getActivity(), endCallPermission, ACTION_REQUEST_PERMISSIONS_ANSWER_PHONE_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.DEFAULT_ERROR, resultData, e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 判断电话监听状态
     */
    
    public void isRegisterListener(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            boolean isRegisterListener = phoneListenerService != null;
            resultData.put("isRegisterListener", isRegisterListener);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), resultData, e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.DEFAULT_ERROR, resultData, e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 静默发送短信
     */
    
    public void hideSendSms(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            sendSmsCallback = callback;
            sendSmsJson = jsonObject;
            if (checkPermissions(sendSmsPermissions)) {
                sendSms();
//                JSONObject result = resultJsonObject.returnSuccess("");
//                callback.invoke(result);
            } else {
                ActivityCompat.requestPermissions(getActivity(), sendSmsPermissions, ACTION_REQUEST_PERMISSIONS_SEND_SMS_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 获取手机通讯录
     */
    
    public void getContacts(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            contactCallback = callback;
            contactJson = jsonObject;
            if (checkPermissions(contactPermissions)) {
                ContactTools.getInstance(getContext()).getContacts(jsonObject, callback);
            } else {
                ActivityCompat.requestPermissions(getActivity(), contactPermissions, ACTION_REQUEST_PERMISSIONS_CONTACT_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 获取短信列表
     */
    
    public void getSms(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            getSmsCallback = callback;
            getSmsJson = jsonObject;
            SmsTools smsTools = SmsTools.getInstance(getContext());
            if (checkPermissions(smsTools.permissions)) {
                SmsTools.getInstance(getContext()).getSmsList(jsonObject, callback);
            } else {
                ActivityCompat.requestPermissions(getActivity(), smsTools.permissions, smsTools.READ_SMS_PERMISSION_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 添加通讯录
     */
    
    public void addContact(JSONObject jsonObject, UniJSCallback callback) {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            if(!jsonObject.containsKey("name") || TextUtils.isEmpty(jsonObject.getString("name"))){
                throw new LevenException("name不能为空");
            }
            if(!jsonObject.containsKey("phoneNumber") || TextUtils.isEmpty(jsonObject.getString("phoneNumber"))){
                throw new LevenException("phoneNumber不能为空");
            }
            addContactCallback = callback;
            addContactJson = jsonObject;
            if (checkPermissions(contactPermissions)) {
                ContactTools.getInstance(getContext()).addContact(jsonObject);
                JSONObject result = resultJsonObject.returnSuccess("");
                callback.invoke(result);
            } else {
                ActivityCompat.requestPermissions(getActivity(), contactPermissions, ACTION_REQUEST_PERMISSIONS_ADD_CONTACT_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 开启前台通知
     */
    
    public void startForeground(JSONObject jsonObject, UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            String title = "";
            String content = "";
            String icon = "";
            if(jsonObject.containsKey("title")){
                title = jsonObject.getString("title");
            }
            if(jsonObject.containsKey("content")){
                content = jsonObject.getString("content");
            }
            if(jsonObject.containsKey("icon")){
                icon = jsonObject.getString("icon");
            }
            ForegroundServiceTools.getInstance(getContext()).startNotification(title, content, icon);
            JSONObject result = resultJsonObject.returnSuccess();
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 关闭前台通知
     */
    
    public void stopForeground(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        try {
            TestUtils.isTest();
            ForegroundServiceTools.getInstance(getContext()).stopNotification();
            JSONObject result = resultJsonObject.returnSuccess();
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 是否有前台通知权限
     */
    
    public void isForegroundPermission(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            boolean status = ForegroundServiceTools.getInstance(getContext()).hasNotificationPermission();;
            resultData.put("status", status);
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 跳转到通知权限页面
     */
    
    public void toForegroundPage(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            notificationPermissionCallback = callback;
            ForegroundServiceTools.getInstance(getContext()).toNotificationPermissionPage();
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 打开或关闭免提
     */
    
    public void toggleSpeaker(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            TestUtils.isTest();
            toggleSpeakerCallback = callback;
            if (checkPermissions(toggleSpeakerPermissions)) {
                priToggleSpeaker(callback);
            } else {
                ActivityCompat.requestPermissions(getActivity(), toggleSpeakerPermissions, ACTION_REQUEST_PERMISSIONS_TOGGLE_SPEAKER_CODE);
            }
        } catch (LevenException e) {
            JSONObject result = resultJsonObject.returnFailed(e.getCode(), e.getMessage());
            callback.invoke(result);
        } catch (Exception e) {
            JSONObject result = resultJsonObject.returnFailed(e.getMessage());
            callback.invoke(result);
        }
    }

    /**
     * 开始注册电话监听
     */
    private void startRegisterPhoneListener() {
        phoneListenerService = new PhoneListenerService(getContext());
        phoneListenerService.registerPhoneListener(phoneListenerCallback);
    }

    /**
     * 开始获取通话记录
     */
    private void startGetAllCalls() {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        if (callLogCallback != null) {
            try {
                List<Map<String, Object>> list = PhoneUtils.getInstance(getContext()).getAllCalls();
                resultData.put("list", list);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                callLogCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callLogCallback.invoke(returnResult);
            } finally {
                callLogCallback = null;
            }
        }
    }

    /**
     * 根据条件获取通话记录
     */
    private void startGetCalls() {
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        if (callLogFilterCallback != null) {
            try {
                Integer type = callLogFilterJsonObject.getInteger("type");
                String value = callLogFilterJsonObject.getString("value");
                long[] times = callLogFilterJsonObject.getObject("times", long[].class);
                JSONObject params = callLogFilterJsonObject.getObject("params", JSONObject.class);
                List<JSONObject> list = PhoneUtils.getInstance(getContext()).getCalls(type, value, times, params);
                resultData.put("list", list);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                callLogFilterCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callLogFilterCallback.invoke(returnResult);
            } finally {
                callLogFilterCallback = null;
            }
        }
    }

    /**
     * 开始拨打电话
     */
    private void startCallPhone() {
        if (callPhoneCallback != null) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                PhoneUtils.getInstance(getContext()).callPhoneWithSlot(callPhoneSlot - 1, callPhoneNumber, isTelephone);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                callPhoneCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callPhoneCallback.invoke(returnResult);
            } finally {
                callPhoneNumber = "";
                callPhoneSlot = 1;
            }
        }

    }

    /**
     * 开始获取是否是双卡
     */
    private void startIsMultiSim() {
        if (isMultiSimCallback != null) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                boolean isMultiSim = PhoneUtils.getInstance(getContext()).isMultiSim();
                resultData.put("isMultiSim", isMultiSim);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                isMultiSimCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                isMultiSimCallback.invoke(returnResult);
            } finally {
                isMultiSimCallback = null;
            }
        }
    }

    /**
     * 开始获取电话号码
     */
    private void startGetPhoneNumbers() {
        if (phoneNumberCallback != null) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                List<JSONObject> numbers = PhoneUtils.getInstance(getContext()).getPhoneNumbers();
                resultData.put("numbers", numbers);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                phoneNumberCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                phoneNumberCallback.invoke(returnResult);
            } finally {
                phoneNumberCallback = null;
            }
        }
    }


    /**
     * 页面返回
     * @param requestCode   请求码
     * @param resultCode   请求值
     * @param data  数据
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_REQUEST_PERMISSIONS_ALL_FILES_CODE && allFilesPermissionsCallback != null) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                //所有文件访问权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    boolean status = Environment.isExternalStorageManager();
                    resultData.put("status", status);
                } else {
                    String[] permissionsList = {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    };
                    boolean status = checkPermissions(permissionsList);
                    resultData.put("status", status);
                }
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                allFilesPermissionsCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                allFilesPermissionsCallback.invoke(returnResult);
                allFilesPermissionsCallback = null;
            }
        }else if(requestCode == ForegroundServiceTools.ACTION_NOTIFICATION_CODE && notificationPermissionCallback != null){
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                //是否有权限
                boolean status = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    status = ForegroundServiceTools.getInstance(getContext()).hasNotificationPermission();
                }
                resultData.put("status", status);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                notificationPermissionCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                notificationPermissionCallback.invoke(returnResult);
                notificationPermissionCallback = null;
            }
        }
    }

    /**
     * 权限请求结果
     *
     * @param requestCode  请求码
     * @param permissions  权限列表
     * @param grantResults 权限申请结果
     * @param isAllGranted 是否全部被同意
     */
    @Override
    public void afterRequestPermission(int requestCode, String[] permissions, int[] grantResults, boolean isAllGranted) {
        super.afterRequestPermission(requestCode, permissions, grantResults, isAllGranted);
        if (requestCode == ACTION_REQUEST_PERMISSIONS_PHONE_CODE && phoneListenerCallback != null) {
            //通话监听
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startRegisterPhoneListener();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    phoneListenerCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                phoneListenerCallback.invoke(returnResult);
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_CALL_LOG_CODE && callLogCallback != null) {
            //获取通话记录
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startGetAllCalls();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    callLogCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callLogCallback.invoke(returnResult);
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_CALL_PHONE_CODE && callPhoneCallback != null) {
            //拨打电话
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startCallPhone();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    callPhoneCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callPhoneCallback.invoke(returnResult);
            }
        } else if (requestCode == SmsTools.getInstance(getContext()).SMS_PERMISSION_CODE && smsListenerCallback != null) {
            //短信监听
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    SmsTools.getInstance(getContext()).register(smsListener);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    smsListenerCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                smsListenerCallback.invoke(returnResult);
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_CALL_LOG_FILTER_CODE && callLogFilterCallback != null) {
            //根据条件获取通话记录
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startGetCalls();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    callLogFilterCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                callLogFilterCallback.invoke(returnResult);
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_IS_MULTI_SIM_CODE && isMultiSimCallback != null) {
            //是否是双卡
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startIsMultiSim();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    isMultiSimCallback.invoke(returnResult);
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                isMultiSimCallback.invoke(returnResult);
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_END_CALL_CODE && endCallCallback != null) {
            //挂断电话
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            try {
                phoneListenerService.endCall();
                JSONObject result = resultJsonObject.returnSuccess("操作成功");
                endCallCallback.invoke(result);
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                endCallCallback.invoke(returnResult);
            } finally {
                endCallCallback = null;
            }
        } else if (requestCode == ACTION_REQUEST_PERMISSIONS_ANSWER_PHONE_CODE && answerPhoneCallback != null) {
            //接听电话
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            try {
                phoneListenerService.acceptRingingCall();
                JSONObject result = resultJsonObject.returnSuccess("操作成功");
                answerPhoneCallback.invoke(result);
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                answerPhoneCallback.invoke(returnResult);
            }finally {
                answerPhoneCallback = null;
            }
        }else if(requestCode == ACTION_REQUEST_PERMISSIONS_PHONE_NUMBERS_CODE && phoneNumberCallback != null){
            //获取电话号码
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    startGetPhoneNumbers();
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    phoneNumberCallback.invoke(returnResult);
                    phoneNumberCallback = null;
                }
            } catch (Exception e) {
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                phoneNumberCallback.invoke(returnResult);
                phoneNumberCallback = null;
            }
        }else if(requestCode == ACTION_REQUEST_PERMISSIONS_SEND_SMS_CODE && sendSmsCallback != null){
            //静默发送短信
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    sendSms();
//                    JSONObject result = resultJsonObject.returnSuccess("操作成功");
//                    sendSmsCallback.invoke(result);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    sendSmsCallback.invoke(returnResult);
                    sendSmsCallback = null;
                }
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                sendSmsCallback.invoke(returnResult);
                sendSmsCallback = null;
            }
        }else if(requestCode == ACTION_REQUEST_PERMISSIONS_CONTACT_CODE && contactCallback != null){
            //获取通讯录
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    ContactTools.getInstance(getContext()).getContacts(contactJson, contactCallback);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    contactCallback.invoke(returnResult);
                    contactCallback = null;
                }
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                contactCallback.invoke(returnResult);
                contactCallback = null;
            }
        }else if(requestCode == ACTION_REQUEST_PERMISSIONS_ADD_CONTACT_CODE && addContactCallback != null){
            //添加通讯录
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    ContactTools.getInstance(getContext()).addContact(addContactJson);
                    JSONObject returnResult = resultJsonObject.returnSuccess();
                    addContactCallback.invoke(returnResult);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    addContactCallback.invoke(returnResult);
                }
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                addContactCallback.invoke(returnResult);
            }finally {
                addContactCallback = null;
            }
        }else if(requestCode == SmsTools.getInstance(getContext()).READ_SMS_PERMISSION_CODE && getSmsCallback != null){
            //获取短信列表
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    SmsTools.getInstance(getContext()).getSmsList(getSmsJson, getSmsCallback);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    getSmsCallback.invoke(returnResult);
                    getSmsCallback = null;
                }
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                getSmsCallback.invoke(returnResult);
                getSmsCallback = null;
            }
        }else if(requestCode == ACTION_REQUEST_PERMISSIONS_TOGGLE_SPEAKER_CODE && toggleSpeakerCallback != null){
            //开启或关闭免提
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            try {
                if (isAllGranted) {
                    priToggleSpeaker(contactCallback);
                } else {
                    //权限未全部同意
                    resultData.put("permissions", permissions);
                    resultData.put("grantResults", grantResults);
                    JSONObject returnResult = resultJsonObject.returnFailed(ErrorEnum.PERMISSION_REQUEST_NOT_ALL_GRANT, resultData, "权限申请未全部同意");
                    toggleSpeakerCallback.invoke(returnResult);
                    toggleSpeakerCallback = null;
                }
            }catch (Exception e){
                JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
                toggleSpeakerCallback.invoke(returnResult);
                toggleSpeakerCallback = null;
            }
        }
    }

    /**
     * 短信监听
     */
    SmsListener smsListener = new SmsListener() {
        @Override
        public void onMessage(String number, String content) {
            ResultJsonObject resultJsonObject = new ResultJsonObject();
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("number", number);
            resultData.put("content", content);
            JSONObject returnResult = resultJsonObject.returnSuccess(resultData, "收到短信");
            smsListenerCallback.invokeAndKeepAlive(returnResult);
        }

        @Override
        public void onSuccess() {
            if (smsListenerCallback != null) {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                JSONObject returnResult = resultJsonObject.returnSuccess("注册监听成功");
                smsListenerCallback.invokeAndKeepAlive(returnResult);
            }
        }
    };

    /**
     * 发送短信
     */
    private void sendSms() throws LevenException {
        if (!sendSmsJson.containsKey("phoneNumber") || TextUtils.isEmpty(sendSmsJson.getString("phoneNumber"))) {
            throw new LevenException("发送号码不能为空");
        }
        if (!sendSmsJson.containsKey("message") || TextUtils.isEmpty(sendSmsJson.getString("message"))) {
            throw new LevenException("发送内容不能为空");
        }
        //注册短信发送通知
        registerSmsSend();
        // 创建发送和接收的PendingIntent
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(getContext(), 0, new Intent(SmsTools.SMS_SENT), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(getContext(), 0, new Intent(SmsTools.SMS_DELIVERED), PendingIntent.FLAG_IMMUTABLE);

        int subId = 1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            subId = SmsManager.getDefault().getSubscriptionId();
        }
        if (sendSmsJson.containsKey("subId")) {
            subId = sendSmsJson.getInteger("subId");
        }
        String phoneNumber = sendSmsJson.getString("phoneNumber");
        String message = sendSmsJson.getString("message");
        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        }else{
            smsManager = SmsManager.getDefault();
        }
        // 如果短信内容超过70个字符需要分割
        if (message.length() > 70) {
            List<String> messages = smsManager.divideMessage(message);
            for (String msg : messages) {
                smsManager.sendTextMessage(phoneNumber, null, msg, sentPendingIntent, deliveredPendingIntent);
            }
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, sentPendingIntent, deliveredPendingIntent);
        }
    }

    /**
     * 开启或关闭免提
     */
    private void priToggleSpeaker(UniJSCallback callback){
        ResultJsonObject resultJsonObject = new ResultJsonObject();
        Map<String, Object> resultData = new HashMap<>();
        try {
            if(audioManager == null){
                audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            }
            boolean toggle = !audioManager.isSpeakerphoneOn();
//            audioManager.setMode(AudioManager.MODE_IN_CALL);
            audioManager.setSpeakerphoneOn(toggle);
            getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
            resultData.put("status", audioManager.isSpeakerphoneOn());
            JSONObject result = resultJsonObject.returnSuccess(resultData, "");
            callback.invoke(result);
        }catch (Exception e){
            JSONObject returnResult = resultJsonObject.returnFailed(e.getMessage());
            toggleSpeakerCallback.invoke(returnResult);
        }
    }

    //注册短信发送通知
    private void registerSmsSend(){
        //注册发送结果
        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                switch (getResultCode()) {
                    case -1:
//                        Toast.makeText(context, "短信发送成功", Toast.LENGTH_SHORT).show();
                        System.out.println("短信发送成功");
                        if(sendSmsCallback != null){
                            resultData.put("type", "send");
                            JSONObject result = resultJsonObject.returnSuccess(resultData, "短信发送成功");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
//                        Toast.makeText(context, "短信发送失败", Toast.LENGTH_SHORT).show();
                        System.out.println("短信发送失败");
                        if(sendSmsCallback != null){
                            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.SEND_SMS_FAILED, "短信发送失败");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
//                        Toast.makeText(context, "无服务", Toast.LENGTH_SHORT).show();
                        System.out.println("无服务");
                        if(sendSmsCallback != null){
                            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.SEND_SMS_NO_SERVICE, "无服务");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
//                        Toast.makeText(context, "PDU为空", Toast.LENGTH_SHORT).show();
                        System.out.println("PDU为空");
                        if(sendSmsCallback != null){
                            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.SEND_SMS_PUD_IS_NULL, "PDU为空");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
//                        Toast.makeText(context, "无线电关闭", Toast.LENGTH_SHORT).show();
                        System.out.println("无线电关闭");
                        if(sendSmsCallback != null){
                            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.SEND_SMS_RADIO_CLOSE, "无线电关闭");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                }
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(SmsTools.SMS_SENT));

        //注册接收结果
        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                switch (getResultCode()) {
                    case -1:
//                        Toast.makeText(context, "短信已送达", Toast.LENGTH_SHORT).show();
                        if(sendSmsCallback != null){
                            resultData.put("type", "sendResult");
                            JSONObject result = resultJsonObject.returnSuccess(resultData, "短信已发送");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                    case 0:
//                        Toast.makeText(context, "短信未送达", Toast.LENGTH_SHORT).show();
                        System.out.println("短信未送达");
                        if(sendSmsCallback != null){
                            JSONObject result = resultJsonObject.returnFailed(ErrorEnum.SEND_SMS_NO_DELIVERED, "无线电关闭");
                            sendSmsCallback.invokeAndKeepAlive(result);
                        }
                        break;
                }
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(SmsTools.SMS_DELIVERED));
    }
}
