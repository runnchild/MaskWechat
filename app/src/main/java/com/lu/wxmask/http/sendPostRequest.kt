package com.lu.wxmask.http

import android.annotation.SuppressLint
import com.lu.magic.util.log.LogUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

@SuppressLint("HardwareIds")
fun sendPostRequest(method: String, content: String, callback: ((String) -> Unit)? = null) {
    // OkHttpClient 实例
    val client = OkHttpClient()

    LogUtil.e("sendPostRequest: $content, androidId: $androidId")

    // 将 JSON 数据封装成 RequestBody
    val requestBody = RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        content
    )
    // 构建请求
    val request = Request.Builder()
        .url("https://jswx.qychaye.com/$method")
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
                val string = response.body?.string()
                println("Response: ${content}==> $string, androidId=$androidId")
                callback?.invoke(string ?: "")
            } else {
                println("Error: ${content}==> ${response.code}")
            }
        }
    })
}
