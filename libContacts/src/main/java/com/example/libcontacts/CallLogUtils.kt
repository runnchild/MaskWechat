package com.example.libcontacts

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.CallLog
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
        // 发送短信

        try {
            val smsManager = SmsManager.getDefault()
            //            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phone, null, message, null, null)
            LogUtil.e("sendSms: $phone $message")
//            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO)
//            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
//            intent.data = Uri.parse("smsto:$phone")
//            intent.putExtra("sms_body", message)
//            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtil.e("短信发送失败：$e")
        }
    }
}

fun JSONArray?.toStringArray(): Array<String>? {
    return this?.let {
        Array(length()) { i -> optString(i) }
    }
}
