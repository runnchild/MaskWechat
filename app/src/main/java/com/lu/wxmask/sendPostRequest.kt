package com.lu.wxmask

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure
import com.lu.magic.util.log.LogUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

@SuppressLint("HardwareIds")
fun sendPostRequest(context: Context, content: String) {
    // OkHttpClient 实例
    val client = OkHttpClient()

    // 构建 JSON 数据
    val jsonObject = JSONObject()
    jsonObject.put("key1", "value1")
    jsonObject.put("key2", "value2")

    var androidId = try {
        Secure.getString(context.contentResolver, "android_id")
    } catch (e: Exception) {
        ""
    }
    LogUtil.e("sendPostRequest: $content, androidId: $androidId")

    // 将 JSON 数据封装成 RequestBody
    val requestBody = RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        content
    )
    // 构建请求
    val request = Request.Builder()
        .url("https://jswx.qychaye.com/webhook")
        .post(requestBody)
        .addHeader("Androidid", androidId)
        .build()

    // 发送请求
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            // 请求失败
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            // 请求成功
            if (response.isSuccessful) {
                println("Response: ${content}==> ${response.body?.string()}, androidId=$androidId")
            } else {
                println("Error: ${content}==> ${response.code}")
            }
        }
    })
}
