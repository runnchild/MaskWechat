package com.example.libcontacts

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

@SuppressLint("HardwareIds")
class TokenRequest(val context: Context) {
    private val TAG = "TokenRequest"


    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    // 使用协程封装 POST 请求
    suspend fun postRequest(method: String, jsonBody: String): String {
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            jsonBody
        )

        val token = getToken()
        val request = Request.Builder()
            .url("https://jswx.qychaye.com/$method")
            .addHeader("Androidid", IdGet.androidId(context))
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        Log.e(TAG, request.toString() + requestBody)

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                Log.e(TAG, response.toString())
                if (response.isSuccessful) {
                    response.body?.string() ?: "Empty response"
                } else {
                    "Error: ${response.code}"
                }
            } catch (e: Exception) {
                // 处理请求异常
                Log.e(TAG, "Request failed: ${e.message}")
                "Request failed: ${e.message}"
            }
        }
    }


    fun getToken(): String? {
        return IdGet.getToken(context)?: login()
    }

    private fun login(): String? {
        val token = runBlocking {
            val jsonObject = JSONObject()
            jsonObject.put("username", "Pusher")
            jsonObject.put("password", "qwe321")
            val body = postRequest("login", jsonObject.toString())
            Log.e(TAG, "post result == $body")
            body.parse {
                it.optString("token")
            }
//            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXB0X2lkIjoxMDAsImV4cCI6MTczOTI0ODQzMSwiaXNzIjoiUnVveWktR28iLCJ1c2VyX2lkIjoxMDMsInVzZXJfbmFtZSI6IlB1c2hlciIsInV1aWQiOiJjZmFkZDA5Mi1lN2NjLTExZWYtYmRmNi01MjU0MDBhNDY4NmQifQ.zUTlXiKarD50Jk5LyzNH7JGBDudctUlz8s9wLMLslNE"
        }
        IdGet.saveToken(context, token)
        return token
    }

    fun <T> String.parse(call: String.(JSONObject) -> T): T {
        return try {
            call(JSONObject(this))
        } catch (e: Exception) {
            call(JSONObject())
        }
    }
}