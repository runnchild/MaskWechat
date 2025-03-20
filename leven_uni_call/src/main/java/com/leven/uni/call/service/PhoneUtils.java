package com.leven.uni.call.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.CallLog;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.alibaba.fastjson.JSONObject;
import com.leven.uni.call.utils.FileUtil;
import com.leven.uni.call.utils.LogUtils;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhoneUtils {
    private static PhoneUtils instance;
    private static Context mContext;
    private Uri callUri = CallLog.Calls.CONTENT_URI;
    private String[] columns = {
            CallLog.Calls.CACHED_NAME// 通话记录的联系人
            , CallLog.Calls.NUMBER// 通话记录的电话号码
            , CallLog.Calls.DATE// 通话记录的日期
            , CallLog.Calls.DURATION// 通话时长
            , CallLog.Calls.TYPE// 通话类型
            , CallLog.Calls.PHONE_ACCOUNT_ID    //通话的卡槽id
    };

    //判断是否开启自动录音配置
    private String[] isAutoRecorderConfig = {};
    //跳转到开启自动录音页面
    private String[] audioRecorderPageConfig = {};
    //录音路径
    private String[] audioRecorderDirsConfig = {};

    public PhoneUtils(Context context) {
        mContext = context;
    }

    public static synchronized PhoneUtils getInstance(Context context) {
        mContext = context;
        if (instance == null) {
            instance = new PhoneUtils(context);
        }

        return instance;
    }

    /**
     * 初始化录音数据
     */
    public void initRecorderData(String[] isAutoRecorder, String[] audioRecorderPage, String[] audioRecorderDirs){
        isAutoRecorderConfig = isAutoRecorder;
        audioRecorderPageConfig = audioRecorderPage;
        audioRecorderDirsConfig = audioRecorderDirs;
    }

    /**
     * 判断是否开启通话自动录音功能
     */
    public boolean checkIsAutoRecord() throws Settings.SettingNotFoundException {
        if(isAutoRecorderConfig == null || isAutoRecorderConfig.length == 0){
            boolean isOpen = false;
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if (manufacturer.contains("huawei") || manufacturer.contains("honor") || manufacturer.contains("ptac") ||
               manufacturer.contains("wiko") || manufacturer.contains("unicomvsens")) {
                isOpen = checkHuaweiRecord();
            } else if (manufacturer.contains("xiaomi")) {
                isOpen = checkXiaomiRecord();
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
                isOpen = checkOppoRecord();
            } else if (manufacturer.contains("vivo")) {
                isOpen = checkVivoRecord();
            } else if (manufacturer.contains("lenovo")) {
                isOpen = checkLenovoRecord();
            } else if (manufacturer.contains("zte")) {
                isOpen = checkZteRecord();
            } else if (manufacturer.contains("meizu")) {
                isOpen = checkMeizuRecord();
            } else if(manufacturer.contains("oneplus")){
                //一加
                isOpen = checkOnePlusRecord();
            } else if(manufacturer.contains("moto")){
                //摩托罗拉
                isOpen = checkMotoRecord();
            } else if(manufacturer.contains("samsung")){
                //三星
                isOpen = checkSamsungRecord();
            }
            return isOpen;
        }else{
            return isAudioRecorderStatus();
        }
    }

    /**
     * 跳转到通话自动录音页面
     */
    public void toCallAutoRecorderPage() {
        if(audioRecorderPageConfig == null || audioRecorderPageConfig.length == 0){
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if (manufacturer.contains("huawei") || manufacturer.contains("honor") || manufacturer.contains("ptac") ||
                    manufacturer.contains("wiko") || manufacturer.contains("unicomvsens")) {
                startHuaweiRecord();
            } else if (manufacturer.contains("xiaomi")) {
                startXiaomiRecord();
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
                startOppoRecord();
            } else if (manufacturer.contains("vivo")) {
                startVivoRecord();
            } else if(manufacturer.contains("samsung")){
                startSamsungRecord();
            } else {
                startLenovoRecord();
            }
        }else{
            toAudioRecorderPage();
        }
    }

    /**
     * 获取所有录音文件
     */
    public List<String> getAllRecorderFiles() {
        //获取录音文件夹
        File recorderDirFile = getRecorderDirFile();
        return FileUtil.getAllFiles(recorderDirFile, false);
    }

    /**
     * 根据条件获取录音文件
     */
    public List<String> getRecorderFiles(long[] times) {
        //获取录音文件夹
        File recorderDirFile = getRecorderDirFile();
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.lastModified() >= times[0] && file.lastModified() <= times[1];
            }
        };
        return FileUtil.getFiles(recorderDirFile, false, filter);
    }

    /**
     * 获取设置的key和value
     */
    public JSONObject getSettingsKeyValue() {
        JSONObject object = new JSONObject();
        List<String> systemKeyValue = getSettingsKeyValue(Settings.System.CONTENT_URI);
        List<String> secureKeyValue = getSettingsKeyValue(Settings.Secure.CONTENT_URI);
        List<String> globalKeyValue = getSettingsKeyValue(Settings.Global.CONTENT_URI);
        object.put("system", systemKeyValue);
        object.put("secure", secureKeyValue);
        object.put("global", globalKeyValue);
        object.put("manufacturer", Build.MANUFACTURER.toLowerCase());
        return object;
    }


    /**
     * 搜索录音文件
     */
    public File searchRecorderFile(long runningTime) {
        File audioFile = null;
        //获取录音文件夹
        File recorderDirFile = getRecorderDirFile();
        List<String> list = FileUtil.getAllFiles(recorderDirFile, false);
        long thisTime = System.currentTimeMillis();
        if (!list.isEmpty()) {
            for (String path : list) {
                File file = new File(path);
                long lastModified = file.lastModified();
                if (thisTime - lastModified <= 10000 || (lastModified >= runningTime - 2000 && lastModified <= thisTime + 1000)) {
                    audioFile = file;
                    System.out.println("退出循环");
                    break;
                }
            }
        }

        return audioFile;
    }

    /**
     * 获取所有的通话录音
     */
    public List<Map<String, Object>> getAllCalls() {
        Cursor cursor = mContext.getContentResolver().query(callUri, // 查询通话记录的URI
                columns
                , null, null, CallLog.Calls.DEFAULT_SORT_ORDER// 按照时间逆序排列，最近打的最先显示
        );
        LogUtils.d("cursor count:" + cursor.getCount());
//        Log.i(TAG,"cursor count:" + cursor.getCount());
        List<Map<String, Object>> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));  //姓名
            @SuppressLint("Range") String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));  //号码
            @SuppressLint("Range") long dateLong = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)); //获取通话日期
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(dateLong));
            @SuppressLint("Range") int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));//获取通话时长，值为多少秒
            @SuppressLint("Range") int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE)); //获取通话类型：1.呼入2.呼出3.未接
            @SuppressLint("Range") int slotId = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)); //呼叫的卡槽id
            if (TextUtils.isEmpty(name)) name = "";
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("number", number);
            data.put("dateTime", dateLong);
            data.put("date", date);
            data.put("duration", duration);
            data.put("type", type);
            data.put("slotId", slotId);
            list.add(data);
        }
        return list;
    }

    /**
     * 根据条件获取通话录音
     */
    public List<JSONObject> getCalls(int selectType, String value, long[] times, JSONObject jsonObject) {
        String selection = "";
        if (selectType == 1) {
            //根据电话号码查找
            selection = CallLog.Calls.NUMBER + "='" + value + "'";
        } else if(selectType == 2) {
            //根据姓名查找
            selection = CallLog.Calls.CACHED_NAME + "='" + value + "'";
        }else if(selectType == 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            //根据时间段查找
            if(times.length == 2){
                long startTime = times[0];
                long endTime = times[1];
                selection = CallLog.Calls.DATE + " BETWEEN " + startTime + " AND " + endTime;
            }
        }else if(selectType == 4 && jsonObject != null){
            //姓名
            String name = jsonObject.getString("name");
            //电话号码
            String number = jsonObject.getString("number");
            //时间段
            long[] dates = jsonObject.getObject("times", long[].class);
            if(!TextUtils.isEmpty(name)){
                selection += CallLog.Calls.CACHED_NAME + "='" + name + "'";
            }
            if(!TextUtils.isEmpty(number)){
                if(TextUtils.isEmpty(selection)){
                    selection += CallLog.Calls.NUMBER + "='" + number + "'";
                }else{
                    selection += " AND " + CallLog.Calls.NUMBER + "='" + number + "'";
                }
            }
            if(dates != null && dates.length == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                long startDate = dates[0];
                long endDate = dates[1];
                if(TextUtils.isEmpty(selection)){
                    selection += CallLog.Calls.DATE + " BETWEEN " + startDate + " AND " + endDate;
                }else{
                    selection += " AND " + CallLog.Calls.DATE + " BETWEEN " + startDate + " AND " + endDate;
                }
            }
        }

        Cursor cursor = mContext.getContentResolver().query(callUri, // 查询通话记录的URI
                columns
                , selection, null, CallLog.Calls.DEFAULT_SORT_ORDER// 按照时间逆序排列，最近打的最先显示
        );
        LogUtils.d("cursor count:" + cursor.getCount());
//        Log.i(TAG,"cursor count:" + cursor.getCount());
        List<JSONObject> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));  //姓名
            @SuppressLint("Range") String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));  //号码
            @SuppressLint("Range") long dateLong = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)); //获取通话日期
            @SuppressLint("SimpleDateFormat") String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(dateLong));
            @SuppressLint("Range") int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));//获取通话时长，值为多少秒
            @SuppressLint("Range") int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE)); //获取通话类型：1.呼入2.呼出3.未接
            @SuppressLint("Range") int slotId = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)); //呼叫的卡槽id
            if (TextUtils.isEmpty(name)) name = "";
//            Map<String, Object> data = new HashMap<>();
            JSONObject data = new JSONObject();
            data.put("name", name);
            data.put("number", number);
            data.put("dateTime", dateLong);
            data.put("date", date);
            data.put("duration", duration);
            data.put("type", type);
            data.put("slotId", slotId);
            list.add(data);
        }
        return list;
    }

    /**
     * 是否是双卡
     *
     * @return
     */
    @SuppressLint("MissingPermission")
    public boolean isMultiSim() {
        boolean result = false;
        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            List<PhoneAccountHandle> phoneAccountHandleList = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
                result = phoneAccountHandleList.size() >= 2;
            }
        }
        return result;
    }

    /**
     * 指定卡槽拨打电话
     */
    @SuppressLint("MissingPermission")
    public void callPhoneWithSlot(int slotId, String callPhoneNumber, boolean isTelephone) {
        callPhoneNumber = callPhoneNumber.replaceAll("-", "");
        if(isTelephone){
            mContext.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + callPhoneNumber)));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri data = Uri.parse("tel:" + callPhoneNumber);
        intent.setData(data);
        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
        mContext.startActivity(intent);
//        Intent intent = new Intent(mContext, CallService.class);
//        intent.putExtra("phoneNumber", callPhoneNumber);
//        intent.putExtra("slotId", slotId);
//        intent.putExtra("isTelephone", isTelephone);
//        mContext.startService(intent);
    }

    /**
     * 获取手机号（可以获取双卡）
     */
    public List<JSONObject> getPhoneNumbers() throws Exception {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1){
            throw new Exception("当前安卓版本不支持获取手机号，安卓api需大于等于" + Build.VERSION_CODES.LOLLIPOP_MR1);
        }
        //获取当前手机有几个手机号
        SubscriptionManager subscriptionManager = SubscriptionManager.from(mContext);
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            throw new Exception("未获取手机权限");
        }
        List<JSONObject> numbers = new ArrayList<>();
        List<SubscriptionInfo> list = subscriptionManager.getActiveSubscriptionInfoList();
        if (!list.isEmpty()) {
            //多卡
            for (SubscriptionInfo subscriptionInfo : list) {
                JSONObject object = new JSONObject();
                object.put("number", subscriptionInfo.getNumber());
                object.put("subId", subscriptionInfo.getSubscriptionId());
                object.put("displayName", subscriptionInfo.getDisplayName());
                object.put("carrierName", subscriptionInfo.getCarrierName());
                object.put("slotId", subscriptionInfo.getSimSlotIndex());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object.put("mcc", subscriptionInfo.getMccString());
                } else {
                    object.put("mcc", subscriptionInfo.getMcc());
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object.put("mnc", subscriptionInfo.getMncString());
                } else {
                    object.put("mnc", subscriptionInfo.getMnc());
                }
                numbers.add(object);
            }
        }
        return numbers;
    }

    public void setIsAutoRecorderConfig(String[] isAutoRecorderConfig) {
        this.isAutoRecorderConfig = isAutoRecorderConfig;
    }

    public void setAudioRecorderDirsConfig(String[] audioRecorderDirsConfig) {
        this.audioRecorderDirsConfig = audioRecorderDirsConfig;
    }

    public void setAudioRecorderPageConfig(String[] audioRecorderPageConfig) {
        this.audioRecorderPageConfig = audioRecorderPageConfig;
    }

    /**
     * 伪造一个有线耳机插入，并按接听键的广播，让系统开始接听电话。
     */
    public void answerRingingCall(TelephonyManager telmanager) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        //判断是否插上了耳机
        Intent meidaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK);
        meidaButtonIntent.putExtra(Intent.EXTRA_KEY_EVENT,keyEvent);
        mContext.sendOrderedBroadcast(meidaButtonIntent, null);
    }

    /**
     * 检查小米手机自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkXiaomiRecord() throws Settings.SettingNotFoundException {
        try {
            int key = Settings.System.getInt(mContext.getContentResolver(), "button_auto_record_call");
//            XLog.d(TAG, "Xiaomi key:" + key);
            //0是未开启,1是开启
            return key != 0;
        }catch (Exception e){
            int key = Settings.Global.getInt(mContext.getContentResolver(), "enable_record_control");
//            XLog.d(TAG, "Xiaomi key:" + key);
            //0是未开启,1是开启
            return key != 0;
        }
    }

    /**
     * 检查OPPO手机自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkOppoRecord() throws Settings.SettingNotFoundException {
        try {
            int key = Settings.Global.getInt(mContext.getContentResolver(), "oplus_customize_all_call_audio_record");
//        int key = Settings.Global.getInt(mContext.getContentResolver(), "oppo_all_call_audio_record");
//            XLog.d(TAG, "Oppo key:" + key);
            //0代表OPPO自动录音未开启,1代表OPPO自动录音已开启
            return key != 0;
        }catch (Settings.SettingNotFoundException e){
            return checkOppoRecord("oppo_all_call_audio_record");
        }
//        int key = Settings.Global.getInt(mContext.getContentResolver(), "oplus_customize_all_call_audio_record");
////        int key = Settings.Global.getInt(mContext.getContentResolver(), "oppo_all_call_audio_record");
////            XLog.d(TAG, "Oppo key:" + key);
//        //0代表OPPO自动录音未开启,1代表OPPO自动录音已开启
//        return key != 0;
    }

    private boolean checkOppoRecord(String name) throws Settings.SettingNotFoundException {
        int key = Settings.Global.getInt(mContext.getContentResolver(), name);
//            XLog.d(TAG, "Oppo key:" + key);
        //0代表OPPO自动录音未开启,1代表OPPO自动录音已开启
        return key != 0;
    }

    /**
     * 检查VIVO自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkVivoRecord() throws Settings.SettingNotFoundException {
        int key = Settings.Global.getInt(mContext.getContentResolver(), "call_record_state_global");;
        return key == 1;
    }

    /**
     * 检查VIVO自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkLenovoRecord() throws Settings.SettingNotFoundException {
        int key = Settings.Global.getInt(mContext.getContentResolver(), "audio_safe_volume_state");
        return key == 1;
    }

    /**
     * 检查中兴自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkZteRecord() throws Settings.SettingNotFoundException {
        int key = Settings.System.getInt(mContext.getContentResolver(), "auto_recorder");
        return key == 1;
    }

    /**
     * 检查中兴自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkMeizuRecord() throws Settings.SettingNotFoundException {
        int key = Settings.Global.getInt(mContext.getContentResolver(), "auto_record_when_calling");
        return key == 1;
    }

    /**
     * 检查一加自动录音功能是否开启，true已开启  false未开启
     *  global:op_user_used_call_recording
     *  global:oplus_customize_all_call_audio_record
     *  global:oplus_customize_all_stranger_call_audio_record
     *  global:oplus_customize_has_enter_auto_record_activity
     *  system:recorder_status
     * @return
     */
    private boolean checkOnePlusRecord() throws Settings.SettingNotFoundException {
        int key = Settings.Global.getInt(mContext.getContentResolver(), "oplus_customize_all_call_audio_record");
        return key == 1;
    }

    /**
     * 检查华为手机自动录音功能是否开启，true已开启  false未开启
     *
     * @return
     */
    private boolean checkHuaweiRecord() throws Settings.SettingNotFoundException {
        int key = Settings.Secure.getInt(mContext.getContentResolver(), "enable_record_auto_key");
//            XLog.d(TAG, "Huawei key:" + key);
        //0代表华为自动录音未开启,1代表华为自动录音已开启
        return key != 0;
    }

    /**
     * 检查摩托罗拉手机自动录音是否开启，true已开启，false未开启
     */
    private boolean checkMotoRecord() throws Settings.SettingNotFoundException {
        int key = Settings.System.getInt(mContext.getContentResolver(), "recordType");
//            XLog.d(TAG, "Huawei key:" + key);
        //0代表华为自动录音未开启,1代表华为自动录音已开启
        return key != 0;
    }

    /**
     * 检查三星手机自动录音是否开启，true已开启，false未开启
     */
    private boolean checkSamsungRecord() throws Settings.SettingNotFoundException {
        int key = Settings.System.getInt(mContext.getContentResolver(), "record_calls_automatically_on_off");
        return key != 0;
    }

    /**
     * 跳转到VIVO开启通话自动录音功能页面
     */
    private void startVivoRecord() {
        ComponentName componentName = new ComponentName("com.android.incallui", "com.android.incallui.record.CallRecordSetting");
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 跳转到小米开启通话自动录音功能页面
     */
    private void startXiaomiRecord() {
        ComponentName componentName = new ComponentName("com.android.phone", "com.android.phone.settings.CallRecordSetting");
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 跳转到华为开启通话自动录音功能页面
     */
    private void startHuaweiRecord() {
        ComponentName componentName = new ComponentName("com.android.phone", "com.android.phone.MSimCallFeaturesSetting");
//        ComponentName componentName = new ComponentName("com.android.phone", "com.android.phone.recorder");
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 跳转到华为开启通话自动录音功能页面
     */
    private void startSamsungRecord() {
        ComponentName componentName = new ComponentName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.callsettings.ui.preference.CallSettingsActivity");
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 联想跳转到通话记录界面
     */
    private void startLenovoRecord() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CALL_BUTTON);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 跳转到OPPO开启通话自动录音功能页面
     */
    private void startOppoRecord() {
        try {
            ComponentName componentName = new ComponentName("com.android.phone", "com.android.phone.OplusCallFeaturesSetting");
            Intent intent = new Intent();
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }catch (Exception e){
            ComponentName componentName = new ComponentName("com.android.phone", "com.android.phone.oppo.settings.audiorecord.OppoAudioRecordSettingActivity");
            Intent intent = new Intent();
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
    }

    /**
     * 获取录音文件夹
     *
     * @return
     */
    public File getRecorderDirFile() {
        if(audioRecorderDirsConfig == null || audioRecorderPageConfig.length == 0){
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            String parentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            File childFile = null;
            if (manufacturer.contains("huawei") || manufacturer.contains("honor") || manufacturer.contains("ptac") ||
                    manufacturer.contains("wiko") || manufacturer.contains("unicomvsens")) {
                String[] huaweiRecordings = {
                        parentPath + "/Sounds/CallRecord/record",
                        parentPath + "/Sounds/CallRecord",
                        parentPath + "/Sounds/CallReCord/record",
                        parentPath + "/Sounds/CallReCord",
                        parentPath + "/Sounds",
                        parentPath + "/record",
                        parentPath + "/Record"
                };
                for (String recordFile : huaweiRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if (manufacturer.contains("xiaomi")) {
                childFile = new File(parentPath + "/MIUI/sound_recorder/call_rec");
            } else if (manufacturer.contains("meizu")) {
//                childFile = new File(parentPath + "/Recorder");
                String[] meiZuRecordings = {
                        parentPath + "/Recorder/call",
                        parentPath + "/Recorder",
                };
                for (String recordFile : meiZuRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if (manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme")) {
                String[] oppoRecordings = {
                        parentPath + "/Music/Recordings/Call Recordings/ThirdRecord",
                        parentPath + "/Music/Recordings/Call Recordings",
                        parentPath + "/Music/Recordings/Call/Recordings",
                        parentPath + "/Oppo/media/audio/calls",
                        parentPath + "/Recordings/Call Recordings",
                        parentPath + "/Recordings",
                        parentPath + "/Record/PhoneRecord"
                };
                for (String recordFile : oppoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if (manufacturer.contains("vivo")) {
                String[] vivoRecordings = {
                        parentPath + "/Recordings/Record/Call",
                        parentPath + "/Record/Call",
                        parentPath + "/录音/通话录音",
                        parentPath + "/录音"
                };
                for (String recordFile : vivoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("lenovo")){
                String[] lenovoRecordings = {
                        parentPath + "/Audios/VoiceCall",
                        parentPath + "/Sound_recorder/PhoneRecord",
                        parentPath + "/Sound_recorder/Phone_record",
                        parentPath + "/Audio/Recorder/PhoneRecorder",
                        parentPath + "/Audio/Recorder/Phone_recorder"
                };
                for (String recordFile : lenovoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("zte")){
                String[] lenovoRecordings = {
                        parentPath + "/voice",
                        parentPath + "/CallRecord"
                };
                for (String recordFile : lenovoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("moto")){
                String[] motoRecordings = {
                        parentPath + "/Music/Sound recorder/Phone recorder",
                        parentPath + "/Music/Sound_recorder/Phone_recorder",
                        parentPath + "/Music/Sound recorder/Phone_recorder",
                        parentPath + "/Music/Sound_recorder/Phone recorder"
                };
                for (String recordFile : motoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("samsung")){
                String[] motoRecordings = {
                        parentPath + "/Recordings/Call"
                };
                for (String recordFile : motoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("5g")){
                String[] motoRecordings = {
                        parentPath + "/Android/media/com.android.dialer/voicecall"
                };
                for (String recordFile : motoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            } else if(manufacturer.contains("lebest")){
                String[] motoRecordings = {
                        parentPath + "/Music//voicecall"
                };
                for (String recordFile : motoRecordings) {
                    if (FileUtil.isFileExist(recordFile)) {
                        childFile = new File(recordFile);
                        break;
                    }
                }
            }
            return childFile;
        }else{
            return getAudioRecorderDirPath();
        }
    }

    /**
     * 获取key和value
     */
    private List<String> getSettingsKeyValue(Uri uri) {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("Recycle") Cursor cursor = mContext.getContentResolver().query(uri, null, null, null);
            String[] columnNames = cursor.getColumnNames();
            StringBuilder builder = new StringBuilder();
            while (cursor.moveToNext()) {
                for (String columnName : columnNames) {
                    @SuppressLint("Range") String string = cursor.getString(cursor.getColumnIndex(columnName));
                    try {
                        if (string.toLowerCase().contains("record") ||
                                string.toLowerCase().contains("call") ||
                                string.toLowerCase().contains("global") ||
                                string.toLowerCase().contains("auto") ||
                                string.toLowerCase().contains("audio") ||
                                string.toLowerCase().contains("enable")
                        ) {
                            builder.append(columnName).append(":").append(string).append("\n");
                            list.add(columnName + ":" + string);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return list;
    }

    /**
     * 检测自动录音是否开启
     */
    private boolean isAudioRecorderStatus() {
        String[] list = isAutoRecorderConfig;
        if(list == null || list.length == 0){
            list = PhoneConfig.isAutoRecorderConfig;
        }
        for (String item:list) {
            String[] itemList = item.split("/");
            if(itemList.length != 2){
                continue;
            }
            String settingsValue = itemList[0];
            try {
                int key = 0;
                if(settingsValue.equalsIgnoreCase("system")){
                    key = Settings.System.getInt(mContext.getContentResolver(), itemList[1]);
                }else if(settingsValue.equalsIgnoreCase("secure")){
                    key = Settings.Secure.getInt(mContext.getContentResolver(), itemList[1]);
                }else if(settingsValue.equalsIgnoreCase("global")){
                    key = Settings.Global.getInt(mContext.getContentResolver(), itemList[1]);
                }
                return key != 0;
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * 跳转到自动录音页面
     */
    private void toAudioRecorderPage(){
        String[] list = audioRecorderPageConfig;
        if(list == null || list.length == 0){
            list = PhoneConfig.audioRecorderPageConfig;
        }
        for (String item:list) {
            try {
                String[] itemList = item.split("/");
                if(itemList.length != 2){
                    continue;
                }
                String packageName = itemList[0];
                String className = itemList[1];

                ComponentName componentName = new ComponentName(packageName, className);
                Intent intent = new Intent();
                intent.setComponent(componentName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                return;
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        //循环结束页没有跳转则跳转到拨号界面
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_CALL_BUTTON);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /**
     * 获取录音文件路径
     */
    private File getAudioRecorderDirPath(){
        String[] list = audioRecorderDirsConfig;
        if(list == null || list.length == 0){
            list = PhoneConfig.audioRecorderDirsConfig;
        }
        File childFile = null;
        String parentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        for (String item:list) {
            String recordFile = parentPath + item;
            if (FileUtil.isDirectory(recordFile)){
                childFile = new File(recordFile);
                break;
            }
        }
        return childFile;
    }
}
