package com.example.libcontacts

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import com.lu.magic.util.log.LogUtil
import org.json.JSONArray
import org.json.JSONObject

object CallLogUtils {

    /**
     * 根据参数查询通话记录
     *   <code>
     *   {
     *   "selection": "type = 1 AND duration > 60",   // 查询条件（自动转换参数化查询）
     *   "sortOrder": "date DESC"                      // 按日期降序
     * }
     *
     *   </code>
     * @param context 上下文
     * @param params  查询参数，包含：
     *                - selection: 查询条件
     *                - sortOrder: 排序方式
     * @return 包含查询结果的JSONArray
     */
    fun queryByParams(context: Context, params: JSONObject): JSONArray {
        val result = convertToParameterized(params.optString("selection"))
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            result.first,
            result.second,
            params.optString("sortOrder")
        )
        return convertToJsonArray(cursor)
    }


    @SuppressLint("Range")
    fun getContactDetails(context: Context): JSONArray {
        val jsonArray = JSONArray()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                val phoneNumbers = getPhoneNumbers(context, id)

                val contactJson = JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("phoneNumbers", phoneNumbers)
                }
                jsonArray.put(contactJson)
            }
        }
        return jsonArray
    }

    @SuppressLint("Range")
    private fun getPhoneNumbers(context: Context, contactId: String): JSONArray {
        val phoneArray = JSONArray()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val phoneCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        phoneCursor?.use {
            while (it.moveToNext()) {
                val phoneNumber =
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
//                val phoneType =
//                    it.getInt(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))

                val phoneJson = JSONObject().apply {
                    put("number", phoneNumber)
//                    put("type", 2)
                }
                phoneArray.put(phoneJson)
            }
        }
        return phoneArray
    }

    /// 查询短信
    fun querySms(context: Context, params: JSONObject): JSONArray {
        val result = convertToParameterized(params.optString("selection"))
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms"),
            null,
            result.first,
            result.second,
            params.optString("sortOrder")
        )
        return convertToJsonArray(cursor)
    }

    // 新增安全转换方法
    private fun convertToParameterized(rawWhere: String): Pair<String, Array<String>> {
        val regex = "(=|<|>|<=|>=|!=)\\s*('?[\\w\\d]+'?)".toRegex()
        val args = mutableListOf<String>()

        val processed = regex.replace(rawWhere) { match ->
            val value = match.groupValues[2].trim().trim('\'')
            args.add(value)
            "${match.groupValues[1]} ?"
        }

        return Pair(processed, args.toTypedArray())
    }

    // 新增方法，将Cursor转换为通话记录JSONArray
    @SuppressLint("Range")
    private fun convertToJsonArray(cursor: android.database.Cursor?): JSONArray {
        val jsonArray = JSONArray()
        cursor?.let {
            // 遍历cursor获取通话记录信息
            while (it.moveToNext()) {
                val number =
                    it.getString(it.getColumnIndex(CallLog.Calls.NUMBER))
                val date = it.getLong(it.getColumnIndex(CallLog.Calls.DATE))
                val duration =
                    it.getInt(it.getColumnIndex(CallLog.Calls.DURATION))
                val type = it.getInt(it.getColumnIndex(CallLog.Calls.TYPE))
                val name = it.getString(it.getColumnIndex(CallLog.Calls.CACHED_NAME))

                val jsonObject = JSONObject()
                jsonObject.put("number", number)
                jsonObject.put("duration", duration)
                jsonObject.put("type", type)
                jsonObject.put("dateTime", date)

                jsonArray.put(jsonObject)
                Log.e(
                    "Call Log: ",
                    "Name=$name, Number=$number, Date=$date, Duration=$duration, Type=$type"
                )
            }
            it.close()
        }
        return jsonArray
    }

    fun makeCall(context: Context, phone: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_CALL)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = Uri.parse("tel:$phone")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtil.e(e)
        }
    }

    fun sendSms(context: Context, phone: String, message: String) {
        LogUtil.e("SMS sent to $phone: $message")
        // 用root权限，直接发送短信
//        val command =
//            "service call isms 54 s16 \"$phone\" s16 null s16 \"$message\" i32 0 i32 0"

        val command = "service call isms 7 i32 0 s16 \"$phone\" s16 \"$message\""

        try {
            val result = ShellUtils.execRootCmd1(command)
            if (result == 0) {
                LogUtil.i("短信发送成功：$phone")
            } else {
                LogUtil.e("短信发送失败，返回码：$result")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtil.e("短信发送异常：${e.message}")
        }
        //// 获取SmsManager实例
        //        val smsManager = context.getSystemService(SmsManager::class.java) as SmsManager
        //        // 发送短信
        //        smsManager.sendTextMessage(phone, null, message, null, null)
        //        LogUtil.e("SMS sent to $phone: $message")
    }
}

fun JSONArray?.toStringArray(): Array<String>? {
    return this?.let {
        Array(length()) { i -> optString(i) }
    }
}
