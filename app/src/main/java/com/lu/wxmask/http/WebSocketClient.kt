package com.lu.wxmask.http

import android.annotation.SuppressLint
import com.lu.magic.util.kxt.toJson
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.util.ext.toJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.ByteString
import org.json.JSONObject

var androidId: String = ""
var token: String = ""

class WebSocketClient {

    private val wsUrl = "wss://jswx.qychaye.com/ws" // 替换成你服务器的 WebSocket URL
    private val client: OkHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    // 使用协程封装 POST 请求
    suspend fun postRequest(method: String, jsonBody: String): String {
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            jsonBody
        )

        val request = Request.Builder()
            .url("https://jswx.qychaye.com/$method")
            .addHeader("Androidid", androidId)
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        LogUtil.e(request.toString(), requestBody)

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            LogUtil.e(response.toString())
            if (response.isSuccessful) {
                response.body?.string() ?: "Empty response"
            } else {
                "Error: ${response.code}"
            }
        }
    }

    @SuppressLint("HardwareIds")
    fun start() {
        LogUtil.e("post result == launch start")
        runBlocking {
            val jsonObject = JSONObject()
            jsonObject.put("username", "test")
            jsonObject.put("password", "1q2w3e")
            val body = postRequest("login", jsonObject.toJson())
            LogUtil.e("post result == $body")
        }

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Androidid", androidId)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                println("WebSocket connected!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                println("Received message: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                println("Received binary message")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                println("WebSocket closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                println("WebSocket closed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                println("WebSocket failure: ${t.message}")
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    fun sendMessage(message: String) {
        webSocket?.send(message)
    }

    fun stop() {
        webSocket?.close(1000, "Goodbye")
    }

    fun getToken() {
        // 构建 JSON 数据
        val jsonObject = JSONObject()
        jsonObject.put("username", "test")
        jsonObject.put("password", "1q2w3e")

        sendPostRequest("login", jsonObject.toJson()) {

        }
    }
}
