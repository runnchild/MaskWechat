package com.leven.uni.call.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.leven.uni.call.UniJSCallback;
import com.leven.uni.call.utils.LevenException;
import com.leven.uni.call.utils.ResultJsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactTools {
    private final Context context;
    private static ContactTools instance;

    public ContactTools(Context context){
        this.context = context;
    }

    public static synchronized ContactTools getInstance(Context context) {
        if(instance == null){
            instance = new ContactTools(context);
        }
        return instance;
    }

    /**
     * 获取手机通讯录
     */
    public void getContacts(JSONObject filter, UniJSCallback callback){
        //线程中处理
        ThreadTools.getInstance().runOnSubThread(new Runnable() {
            @Override
            public void run() {
                ResultJsonObject resultJsonObject = new ResultJsonObject();
                Map<String, Object> resultData = new HashMap<>();
                ContentResolver cr = context.getContentResolver();
                Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                // 构建查询条件
                String selection = "";
                if(filter.containsKey("phoneNumber") && !TextUtils.isEmpty(filter.getString("phoneNumber"))){
                    String phoneNumber = filter.getString("phoneNumber");
                    selection += ContactsContract.CommonDataKinds.Phone.DATA + " = '" + phoneNumber + "'";
                }
                if(filter.containsKey("name") && !TextUtils.isEmpty(filter.getString("name"))){
                    String name = filter.getString("name");
                    if(TextUtils.isEmpty(selection)){
                        selection += ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like '%" + name + "%'";
                    }else{
                        selection += " AND " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like '%" + name + "%'";
                    }
                }
                //排序条件
                String sortOrder = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"; // 设置排序方式
                String[] projection = new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                };
                List<JSONObject> list = new ArrayList<>();
                Cursor cursor = cr.query(uri, projection, selection, null, sortOrder);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        @SuppressLint("Range")
                        String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        @SuppressLint("Range")
                        String contactNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        // 处理获取到的联系人姓名和电话号码，‌例如显示在列表中或进行其他操作
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("name", contactName);
                        jsonObject.put("phoneNumber", contactNumber);
                        list.add(jsonObject);
                    }
                    cursor.close();
                }
                resultData.put("list", list);
                JSONObject result = resultJsonObject.returnSuccess(resultData, "");
                callback.invoke(result);
            }
        });
    }

    /**
     * 添加通讯录
     */
    public void addContact(JSONObject jsonObject) throws LevenException {
        String name = jsonObject.getString("name");
        String phone = jsonObject.getString("phoneNumber");
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        Uri rawContactUri = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, values);
        if(rawContactUri == null){
            throw new LevenException("添加失败");
        }
        long rawContactId = ContentUris.parseId(rawContactUri);
        values.clear();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
        resolver.insert(ContactsContract.Data.CONTENT_URI, values);

        values.clear();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone);
        values.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        resolver.insert(ContactsContract.Data.CONTENT_URI, values);
    }
}
