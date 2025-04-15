package com.example.libcontacts

import android.content.Context
import com.lu.magic.util.log.LogUtil
import okhttp3.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

interface WebSocketClientListener {
    fun onOpen(handshake: ServerHandshake) {}
    fun onMessage(message: String)
    fun onMessage(bytes: ByteBuffer) {}
    fun onClose(code: Int, reason: String, remote: Boolean) {}
    fun onError(ex: Exception) {}
}

class WebSocketClient(val context: Context) {
    private val TAG = "FileUploader"

    //    private var webSocket: WebSocket? = null
    private val tokenRequest = TokenRequest(context)
    private var onWebSocketListener: WebSocketClientListener? = null
    private lateinit var webSocketClient: WebSocketClient

    private val androidId by lazy {
        IdGet.androidId(context)
    }

    private val imei by lazy {
        IdGet.getImei(context) ?: ""
    }

    fun setOnWebSocketListener(listener: WebSocketClientListener) {
        onWebSocketListener = listener
    }

    private val headerInterceptor = HttpHeaderInterceptor()

    fun connect(): Boolean {
        try {
            val uri = URI("wss://jswx.qychaye.com/ws")
            val headers: MutableMap<String, String> = HashMap()
            headers["Androidid"] = androidId
            headers["Wxid"] = IdGet.getWxId(context)
            headers["imei"] = imei
            headers["Authorization"] = "Bearer ${tokenRequest.getToken()}"

            LogUtil.d(
                TAG,
                "尝试连接WebSocket: , AndroidID: $androidId"
            )

            webSocketClient = object : WebSocketClient(uri, headers) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    LogUtil.d(
                        TAG, "WebSocket连接已打开，状态码: " + handshakedata.httpStatus +
                                ", 状态消息: " + handshakedata.httpStatusMessage
                    )
                    onWebSocketListener?.onOpen(handshakedata)
                }

                override fun onMessage(message: String) {
                    LogUtil.d(
                        TAG,
                        "收到文本消息: $message"
                    )
                    try {
                        onWebSocketListener?.onMessage(message)
                    } catch (e: InterruptedException) {
                        LogUtil.e(TAG, "消息入队失败", e)
                    }
                }

                override fun onMessage(bytes: ByteBuffer) {
                    LogUtil.d(TAG, "收到二进制消息，长度: " + bytes.remaining())
                    onWebSocketListener?.onMessage(bytes)
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    LogUtil.d(
                        TAG,
                        "WebSocket连接已关闭: 代码=$code, 原因=$reason, 远程关闭=$remote"
                    )
                    onWebSocketListener?.onClose(code, reason, remote)
                }

                override fun onError(ex: Exception) {
                    LogUtil.e(TAG, "WebSocket错误: " + ex.message, ex)
                    onWebSocketListener?.onError(ex)
                }
            }

            webSocketClient.setConnectionLostTimeout(30)
            webSocketClient.connect()


            // 等待连接建立
            var retries = 0
            while (!webSocketClient.isOpen() && retries < 5) {
                try {
                    LogUtil.d(TAG, "等待WebSocket连接建立，尝试次数: " + (retries + 1))
                    Thread.sleep(1000)
                    retries++
                } catch (e: InterruptedException) {
                    LogUtil.e(TAG, "等待WebSocket连接时被中断", e)
                    return false
                }
            }

            if (!webSocketClient.isOpen()) {
                LogUtil.e(TAG, "WebSocket连接超时")
                return false
            }

            LogUtil.d(TAG, "WebSocket连接已成功建立")
            return true
        } catch (e: URISyntaxException) {
            LogUtil.e(TAG, "URI语法错误: " + e.message, e)
            return false
        }
    }

    // 关闭WebSocket连接
    fun close() {
//        if (webSocketClient.isOpen) {
//            webSocketClient.close()
//        }
    }
//    fun connect(): Boolean {
//        val LogUtilging = HttpLoggingInterceptor().apply {
//            level = HttpLoggingInterceptor.Level.BODY
//        }
//        val client = OkHttpClient.Builder()
//            .pingInterval(10, TimeUnit.SECONDS)
//            .addInterceptor(LogUtilging)
//            .addInterceptor(headerInterceptor)
//            .build()
//
//        val request = Request.Builder()
//            .url("wss://jswx.qychaye.com/ws")
//            .addHeader("Authorization", "Bearer ${tokenRequest.getToken()}")
//            .addHeader("Androidid", androidId)
//            .addHeader("Wxid", IdGet.getWxId(context))
//            .build()
//
//        webSocket = client.newWebSocket(request, object : WebSocketListener() {
//            override fun onOpen(webSocket: WebSocket, response: Response) {
//                LogUtil.i(context.packageName, "连接已建立")
//                onWebSocketListener?.onOpen(webSocket, response)
//                // 启动心跳保活
//                startHeartbeat(webSocket)
//            }
//
//            override fun onMessage(webSocket: WebSocket, text: String) {
//                LogUtil.i(context.packageName, "收到消息: $text")
//                onWebSocketListener?.onMessage(webSocket, text)
//            }
//
//            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
//                LogUtil.i(context.packageName, "收到二进制消息: ${bytes.hex()}")
//                onWebSocketListener?.onMessage(webSocket, bytes)
//            }
//
//            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
//                LogUtil.i(context.packageName, "连接关闭中: $code - $reason")
//                onWebSocketListener?.onClosing(webSocket, code, reason)
//            }
//
//            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
//                LogUtil.i(context.packageName, "连接已关闭: $code - $reason")
//                onWebSocketListener?.onClosed(webSocket, code, reason)
//            }
//
//            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
//                LogUtil.e(context.packageName, "连接失败", t)
//                onWebSocketListener?.onFailure(webSocket, t, response)
//                // 重连机制
//                reconnect()
//            }
//        })
//    }

    private fun startHeartbeat(webSocket: WebSocket) {
        // 每30秒发送一次心跳
        thread {
            while (true) {
                try {
                    LogUtil.e(
                        context.packageName,
                        "发送心跳: $androidId, ${IdGet.getWxId(context)}"
                    )
                    if (!webSocket.send("ping")) {
                        connect()
                        return@thread
                    }
                    Thread.sleep(30000)
                } catch (e: Exception) {
                    LogUtil.e(context.packageName, "心跳发送失败", e)
                    break
                }
            }
        }
    }

    private fun reconnect() {
        // 5秒后重连
        Thread.sleep(5000)
        connect()
    }

    fun sendMessage(message: String, wxId: String) {
        send(message, wxId)
    }

    fun sendMessage(message: ByteArray, wxId: String) {
        send(message, wxId)
    }

    private fun send(message: Any, wxId: String) {
        if (!webSocketClient.isOpen) {
            connect()
        }
        webSocketClient.addHeader("Wxid", wxId)
        if (message is String) {
            webSocketClient.send(message)
        } else if (message is ByteArray) {
            webSocketClient.send(message)
        }

        LogUtil.i(context.packageName, "${webSocketClient.isOpen}", "wid=$wxId, aid=$androidId", "imi=$imei")
    }

//    fun close() {
//        webSocket?.close(1000, "正常关闭")
//    }
}
