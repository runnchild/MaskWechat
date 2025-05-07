package com.example.libcontacts

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lu.magic.util.log.LogUtil
import okhttp3.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Thread.sleep
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread

interface WebSocketClientListener {
    fun onOpen(handshake: ServerHandshake) {}
    fun onMessage(message: String)
    fun onMessage(bytes: ByteBuffer) {}
    fun onClose(code: Int, reason: String, remote: Boolean) {}
    fun onError(ex: Exception) {}
}

class WebSocketClient(val context: Context) {
    private val TAG = "WebSocketClient"

    private val tokenRequest = TokenRequest(context)
    private var onWebSocketListener: WebSocketClientListener? = null
    private lateinit var webSocketClient: WebSocketClient
    private var heartbeatThread: Thread? = null

    private val androidId by lazy {
        IdGet.androidId(context)
    }

    private val imei by lazy {
        IdGet.getImei(context) ?: ""
    }

    fun setOnWebSocketListener(listener: WebSocketClientListener) {
        onWebSocketListener = listener
    }

    private fun startHeartbeat() {
        heartbeatThread?.interrupt()
        heartbeatThread = thread {
//            while (true) {
                try {
                    sleep(30000)
                    if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
                        LogUtil.e(context.packageName, "发送心跳: $androidId")
                        webSocketClient.send("""{"mMap":{"ping":"ppp"},"mValues":null,"type":"webhook"}""")
                    } else {
                        LogUtil.w(context.packageName, "心跳发送失败, 连接中")
                        connect()
                    }
                } catch (e: InterruptedException) {
                    LogUtil.w(context.packageName, "心跳发送线程被中断")
//                    break
                } catch (e: Exception) {
                    LogUtil.w(context.packageName, "心跳发送失败, 重连中", e)
                    reconnect()
                }
//            }
        }
    }

    private fun reconnect() {
        // 5秒后重连
        thread {
            sleep(5000)
            connect()
        }
    }

    fun isConnected(): Boolean {
        return webSocketClient.isOpen
    }

    fun connect(): Boolean {
        try {
            val uri = URI("wss://jswx.qychaye.com/ws")
            val headers: MutableMap<String, String> = HashMap()
            headers["Androidid"] = androidId
            headers["Wxid"] = IdGet.getWxId(context)
            headers["imei"] = imei
            headers["Authorization"] = "Bearer ${tokenRequest.getToken()}"

            LogUtil.d(TAG, "尝试连接WebSocket: , AndroidID: $androidId")

            webSocketClient = object : WebSocketClient(uri, headers) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    LogUtil.d(
                        TAG, "WebSocket连接已打开，状态码: " + handshakedata.httpStatus +
                                ", 状态消息: " + handshakedata.httpStatusMessage
                    )
                    onWebSocketListener?.onOpen(handshakedata)
                }

                override fun onMessage(message: String) {
                    LogUtil.d(TAG, "收到文本消息: $message", Thread.currentThread())
                    try {
                        onWebSocketListener?.onMessage(message)
                    } catch (e: InterruptedException) {
                        LogUtil.e(TAG, "消息入队失败", e)
                    }
                    startHeartbeat()
                }

                override fun onMessage(bytes: ByteBuffer) {
                    LogUtil.d(TAG, "收到二进制消息，长度: " + bytes.remaining())
                    onWebSocketListener?.onMessage(bytes)
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    LogUtil.i(
                        TAG,
                        "WebSocket连接已关闭: 代码=$code, 原因=$reason, 远程关闭=$remote"
                    )
                    onWebSocketListener?.onClose(code, reason, remote)
                }

                override fun onError(ex: Exception) {
                    LogUtil.e(TAG, "WebSocket错误: " + ex.message, ex)
                    onWebSocketListener?.onError(ex)
                    // 错误时触发重连
//                    this@WebSocketClient.reconnect()
                }
            }

            webSocketClient.connectionLostTimeout = 30
            webSocketClient.connect()
            startHeartbeat()

            // 等待连接建立
            var retries = 0
            while (!webSocketClient.isOpen && retries < 5) {
                try {
                    LogUtil.i(
                        TAG,
                        "等待WebSocket连接建立，尝试次数: " + (retries + 1),
                        Thread.currentThread()
                    )
                    sleep(1000)
                    retries++
                } catch (e: InterruptedException) {
                    LogUtil.e(TAG, "等待WebSocket连接时被中断", e)
                    return false
                }
            }

            if (!webSocketClient.isOpen) {
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
        if (webSocketClient.isOpen) {
            webSocketClient.close()
//            heartbeatTimer.cancel() // 取消心跳定时器
        }
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
        try {
            if (message is String) {
                if (!message.contains(" diskSpace: ")) {
                    webSocketClient.send(message)
                }
            } else if (message is ByteArray) {
                if (!message.toString().contains(" diskSpace: ")) {
                    webSocketClient.send(message)
                }
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "发送消息失败", e)
        }

        LogUtil.i(
            context.packageName,
            "${webSocketClient.isOpen}",
            "wid=$wxId, aid=$androidId",
            "imi=$imei"
        )
    }
}
