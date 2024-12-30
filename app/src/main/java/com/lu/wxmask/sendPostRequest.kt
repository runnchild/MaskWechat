package com.lu.wxmask

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

@SuppressLint("HardwareIds")
fun sendPostRequest(context: Context, content: String) {
    println("sendPostRequest: $content")
    // OkHttpClient 实例
    val client = OkHttpClient()

    // 构建 JSON 数据
    val jsonObject = JSONObject()
    jsonObject.put("key1", "value1")
    jsonObject.put("key2", "value2")

    // 将 JSON 数据封装成 RequestBody
    val requestBody = RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        content
    )

    var androidId = try {
        Secure.getString(context.contentResolver, "android_id");
    } catch (e: Exception) {
        ""
    }
    // 构建请求
    val request = Request.Builder()
        .url("https://jswx.qychaye.com/webhook")
        .header("androidid", androidId)
        .post(requestBody)
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
                println("Response: ${content}==> ${response.body?.string()}")
            } else {
                println("Error: ${content}==> ${response.code}")
            }
        }
    })
}
