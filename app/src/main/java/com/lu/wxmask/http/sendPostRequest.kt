package com.lu.wxmask.http

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure
import com.lu.magic.util.log.LogUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
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
                println("Response: ${content}==> ${response.body?.string()}, androidId=$androidId")
                callback?.invoke(response.body.toString())
            } else {
                println("Error: ${content}==> ${response.code}")
            }
        }
    })
}
