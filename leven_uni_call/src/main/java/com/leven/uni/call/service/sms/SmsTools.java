package com.leven.uni.call.service.sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.leven.uni.call.UniJSCallback;
import com.leven.uni.call.tools.ThreadTools;
import com.leven.uni.call.utils.LevenException;
import com.leven.uni.call.utils.ResultJsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmsTools {
    public static final String SMS_SENT = "SMS_SENT";
    public static final String SMS_DELIVERED = "SMS_DELIVERED";
    private static SmsTools instance;
    private Context mContext;
    private static Intent intent;
    private SmsListener smsListener;
    private boolean isRegisterListener;

    //短信监听权限
    public final String[] permissions = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
    };

    public final int SMS_PERMISSION_CODE = 2000;
    public final int READ_SMS_PERMISSION_CODE = 2001;

    final String SMS_URI_ALL = "content://sms/"; // 所有短信
//    final String SMS_URI_INBOX = "content://sms/inbox"; // 收件箱
//    final String SMS_URI_SEND = "content://sms/sent"; // 已发送
//    final String SMS_URI_DRAFT = "content://sms/draft"; // 草稿
//    final String SMS_URI_OUTBOX = "content://sms/outbox"; // 发件箱
//    final String SMS_URI_FAILED = "content://sms/failed"; // 发送失败
//    final String SMS_URI_QUEUED = "content://sms/queued"; // 待发送列表


    public SmsTools(Context context) {
        mContext = context;
    }

    public static SmsTools getInstance(Context context){
        if(instance == null){
            instance = new SmsTools(context);
            intent = new Intent(context, SmsListenerService.class);
        }
        return instance;
    }

    /**
     * 开启监听
     */
    public void register(SmsListener listener) throws LevenException {
        if(isRegisterListener){
            throw new LevenException("已注册监听，请不要重复注册");
        }
        mContext.startService(intent);
        IntentFilter filter = new IntentFilter("SMS_RECEIVE_DATA");
        mContext.registerReceiver(smsReceiveBroadcast, filter);
        smsListener = listener;
        if(smsListener != null){
            smsListener.onSuccess();
        }
        isRegisterListener = true;
    }

    /**
     * 关闭监听
     */
    public void unregister(){
        mContext.stopService(intent);
        mContext.unregisterReceiver(smsReceiveBroadcast);
        isRegisterListener = false;
    }

    /**
     * 获取短信列表
     */
    public void getSmsList(JSONObject filter, UniJSCallback callback){
        ThreadTools.getInstance().runOnSubThread(new Runnable() {
            @Override
            public void run() {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                Uri uri = Uri.parse(SMS_URI_ALL);
                String[] projection = new String[] { "_id", "address", "person", "body", "date", "type", "read"};
                // 构建查询条件
                String selection = "";
                if(filter.containsKey("phoneNumber") && !TextUtils.isEmpty(filter.getString("phoneNumber"))){
                    String phoneNumber = filter.getString("phoneNumber");
                    selection +=  "address = '" + phoneNumber + "'";
                }
                if(filter.containsKey("content") && !TextUtils.isEmpty(filter.getString("content"))){
                    String content = filter.getString("content");
                    if(TextUtils.isEmpty(selection)){
                        selection +=  "body like '%" + content + "%'";
                    }else {
                        selection += " AND body like '%" + content + "%'";
                    }
                }
                @SuppressLint("Recycle")
                Cursor cur = mContext.getContentResolver().query(uri, projection, selection,
                        null, "date desc"); // 获取手机内部短信
                List<JSONObject> list = new ArrayList<>();
                if(cur != null){
                    while (cur.moveToNext()){
                        int indexAddress = cur.getColumnIndex("address");
                        int indexPerson = cur.getColumnIndex("person");
                        int indexBody = cur.getColumnIndex("body");
                        int indexDate = cur.getColumnIndex("date");
                        int indexType = cur.getColumnIndex("type");
                        int indexRead = cur.getColumnIndex("read");

                        String address = cur.getString(indexAddress);
                        int person = cur.getInt(indexPerson);
                        String content = cur.getString(indexBody);
                        long time = cur.getLong(indexDate);
                        int type = cur.getInt(indexType);
                        int read = cur.getInt(indexRead);
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("phoneNumber", address);
                        jsonObject.put("person", person);
                        jsonObject.put("content", content);
                        jsonObject.put("time", time);
                        jsonObject.put("type", type);
                        jsonObject.put("read", read);
                        list.add(jsonObject);
                    }
                    cur.close();
                }
                resultData.put("list", list);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                callback.invoke(result);
            }
        });
    }

    BroadcastReceiver smsReceiveBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals("SMS_RECEIVE_DATA")){
                //收到短信
                String number = intent.getStringExtra("number");
                String content = intent.getStringExtra("content");
                if(smsListener != null){
                    smsListener.onMessage(number, content);
                }
            }
        }
    };
}
