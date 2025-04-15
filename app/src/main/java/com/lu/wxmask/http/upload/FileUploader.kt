package com.lu.wxmask.http.upload

import android.os.Handler
import android.os.Looper
import com.lu.magic.util.log.LogUtil
import com.example.libcontacts.ShellUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

interface ClientEvent {
    fun send(message: Any)
    fun connect(): Boolean
    fun close()
}

class FileUploader(private val androidId: String, private var event: ClientEvent) {
    //    private lateinit var webSocketClient: WebSocketClient
    private val messageQueue: BlockingQueue<String> = LinkedBlockingQueue()
    private val mainHandler = Handler(Looper.getMainLooper())

    interface UploadListener {
        fun onProgress(filePath: String?, progress: Int, total: Int)
        fun onSuccess(filePath: String?)
        fun onError(filePath: String?, error: String?)
    }

    private var uploadListener: UploadListener? = null

    fun setUploadListener(listener: UploadListener?) {
        this.uploadListener = listener
    }

    // 连接WebSocket
//    fun connect(): Boolean {
//        try {
//            val uri = URI(wsUrl)
//            val headers: MutableMap<String, String> = HashMap()
//            headers["Androidid"] = androidId
//
//            LogUtil.d(
//                TAG,
//                "尝试连接WebSocket: $wsUrl, AndroidID: $androidId"
//            )
//
//            webSocketClient = object : WebSocketClient(uri, headers) {
//                override fun onOpen(handshakedata: ServerHandshake) {
//                    LogUtil.d(
//                        TAG, "WebSocket连接已打开，状态码: " + handshakedata.httpStatus +
//                                ", 状态消息: " + handshakedata.httpStatusMessage
//                    )
//                }
//
    fun handleResponse(message: String) {
        LogUtil.d(
            TAG,
            "收到文本消息: $message"
        )
        try {
            messageQueue.put(message)
        } catch (e: InterruptedException) {
            LogUtil.e(TAG, "消息入队失败", e)
        }
    }
//
//                override fun onMessage(bytes: ByteBuffer) {
//                    LogUtil.d(TAG, "收到二进制消息，长度: " + bytes.remaining())
//                }
//
//                override fun onClose(code: Int, reason: String, remote: Boolean) {
//                    LogUtil.d(
//                        TAG,
//                        "WebSocket连接已关闭: 代码=$code, 原因=$reason, 远程关闭=$remote"
//                    )
//                }
//
//                override fun onError(ex: Exception) {
//                    LogUtil.e(TAG, "WebSocket错误: " + ex.message, ex)
//                }
//            }
//
//            webSocketClient.setConnectionLostTimeout(30)
//            webSocketClient.connect()
//
//
//            // 等待连接建立
//            var retries = 0
//            while (!webSocketClient.isOpen() && retries < 5) {
//                try {
//                    LogUtil.d(TAG, "等待WebSocket连接建立，尝试次数: " + (retries + 1))
//                    Thread.sleep(1000)
//                    retries++
//                } catch (e: InterruptedException) {
//                    LogUtil.e(TAG, "等待WebSocket连接时被中断", e)
//                    return false
//                }
//            }
//
//            if (!webSocketClient.isOpen()) {
//                LogUtil.e(TAG, "WebSocket连接超时")
//                return false
//            }
//
//            LogUtil.d(TAG, "WebSocket连接已成功建立")
//            return true
//        } catch (e: URISyntaxException) {
//            LogUtil.e(TAG, "URI语法错误: " + e.message, e)
//            return false
//        }
//    }

    // 关闭WebSocket连接
//    fun close() {
//        if (webSocketClient.isOpen) {
//            webSocketClient.close()
//        }
//    }

    private fun calculateFileMd5(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        inputStream.close()

        val md5Bytes = md.digest()
        return md5Bytes.joinToString("") { "%02x".format(it) }
    }

    fun File.asInputStream(): InputStream {
        return runBlocking {
            if (canRead()) inputStream() else ShellUtils.readFileAsInputStream(absolutePath)
        }
    }

    // 计算文件MD5
    private fun calculateFileMD5(file: File): String? {
        val digest: MessageDigest
        try {
            digest = MessageDigest.getInstance("MD5")
        } catch (e: NoSuchAlgorithmException) {
            LogUtil.e(TAG, "MD5算法不可用", e)
            return null
        }

        var fis: InputStream? = null
        try {
            fis = file.asInputStream()
            val buffer = ByteArray(8192)
            var read: Int
            while ((fis.read(buffer).also { read = it }) > 0) {
                digest.update(buffer, 0, read)
            }

            val md5sum = digest.digest()
            val sb = StringBuilder()
            for (b in md5sum) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        } catch (e: IOException) {
            LogUtil.e(TAG, "读取文件失败", e)
            return null
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (e: IOException) {
                    LogUtil.e(TAG, "关闭文件流失败", e)
                }
            }
        }
    }

    // 发送上传开始请求
    private fun sendUploadStart(info: ChunkInfo): Boolean {
        try {
            val request = JSONObject()
            request.put("type", "chunk_upload_start")

            val mMap = JSONObject()
            mMap.put("file_id", info.fileId)
            mMap.put("total_chunks", info.totalChunks)
            mMap.put("chunk_size", info.chunkSize)
            mMap.put("path", info.path)
            mMap.put("file_size", info.fileSize)
            mMap.put("total_md5", info.totalMD5)
            mMap.put("last_modified", info.lastModified)

            request.put("mMap", mMap)

            val requestStr = request.toString()
            LogUtil.d(
                TAG,
                "发送上传开始请求: $requestStr"
            )
            event.send(requestStr)
            return true
        } catch (e: JSONException) {
            LogUtil.e(TAG, "构建JSON失败", e)
            return false
        }
    }

    // 读取WebSocket消息，带超时
    private fun readMessage(timeoutSeconds: Int): String? {
        try {
            val message = messageQueue.poll(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            if (message == null) {
                LogUtil.e(TAG, "读取消息超时")
                return null
            }
            return message
        } catch (e: InterruptedException) {
            LogUtil.e(TAG, "读取消息被中断", e)
            return null
        }
    }

    // 发送文件分片
    private fun sendFileChunk(
        uploadId: String,
        fileId: String,
        chunkIndex: Int,
        data: ByteArray,
        expectedSize: Int
    ): Boolean {
        var data = data
        try {
            // 确保分片大小正确
            if (data.size != expectedSize) {
                LogUtil.d(TAG, "调整分片大小: 当前=" + data.size + ", 期望=" + expectedSize)
                if (data.size > expectedSize) {
                    val newData = ByteArray(expectedSize)
                    System.arraycopy(data, 0, newData, 0, expectedSize)
                    data = newData
                }
            }


            // 计算分片的MD5
            val md5Digest = MessageDigest.getInstance("MD5")
            md5Digest.update(data)
            val md5Bytes = md5Digest.digest()
            val chunkMD5Str = bytesToHex(md5Bytes)


            // 构建28字节的头部
            val header = ByteBuffer.allocate(28)
            header.order(ByteOrder.LITTLE_ENDIAN)


            // androidId (8字节，左对齐，右侧补0)
            val androidIdBytes = ByteArray(8)
            System.arraycopy(
                androidId.toByteArray(), 0, androidIdBytes, 0,
                min(androidId.toByteArray().size.toDouble(), 8.0).toInt()
            )
            header.put(androidIdBytes)


            // uploadId (8字节，左对齐，右侧补0)
            val uploadIdBytes = ByteArray(8)
            System.arraycopy(
                uploadId.toByteArray(), 0, uploadIdBytes, 0,
                min(uploadId.toByteArray().size.toDouble(), 8.0).toInt()
            )
            header.put(uploadIdBytes)


            // fileId (8字节，左对齐，右侧补0)
            val fileIdBytes = ByteArray(8)
            System.arraycopy(
                fileId.toByteArray(), 0, fileIdBytes, 0,
                min(fileId.toByteArray().size.toDouble(), 8.0).toInt()
            )
            header.put(fileIdBytes)


            // chunkIndex (4字节，小端序)
            header.putInt(chunkIndex)


            // 重置position以便能够获取整个数组
            header.position(0)
            val headerBytes = ByteArray(28)
            header[headerBytes]


            // 添加MD5（32字节）
            val message = ByteBuffer.allocate(headerBytes.size + chunkMD5Str.length + data.size)
            message.put(headerBytes)
            message.put(chunkMD5Str.toByteArray())
            message.put(data)

            LogUtil.d(
                TAG,
                "分片 " + chunkIndex + " 信息: 大小=" + data.size + " 字节, MD5=" + chunkMD5Str +
                        ", 头部大小=" + (headerBytes.size + chunkMD5Str.length) + ", 总大小=" + message.capacity()
            )


            // 发送二进制消息
            event.send(message.array())
            return true
        } catch (e: NoSuchAlgorithmException) {
            LogUtil.e(TAG, "MD5算法不可用", e)
            return false
        }
    }

    // 字节数组转十六进制字符串
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // 准备上传任务
    private fun prepareUploadTask(filePath: String, targetPath: String?): UploadTask? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            LogUtil.e(
                TAG,
                "文件不存在: $filePath"
            )
            return null
        }

        val totalMD5 = calculateFileMd5(file.asInputStream())

        val fileSize = file.length()
        val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

        LogUtil.d(
            TAG,
            "准备上传任务: 文件=$filePath, 大小=$fileSize, 分片数=$totalChunks"
        )

        // 生成文件ID
        val fileId = System.nanoTime().toString()

        val chunkInfo = ChunkInfo(
            fileId,
            totalChunks,
            CHUNK_SIZE,
            targetPath,
            fileSize,
            totalMD5,
            file.lastModified()
        )

        val task = UploadTask(filePath, targetPath, chunkInfo)
        task.file = file

        try {
            task.fileInputStream = file.asInputStream()
            return task
        } catch (e: IOException) {
            LogUtil.e(TAG, "打开文件流失败", e)
            return null
        }
    }

    // 初始化上传任务
    private fun initializeUploadTask(task: UploadTask): Map<String, Any> {
        val result: MutableMap<String, Any> = HashMap()

        // 发送上传开始请求
        if (!sendUploadStart(task.fileInfo)) {
            result["success"] = false
            result["error"] = "发送上传开始请求失败"
            return result
        }

        // 等待服务器响应
        val responseStr = readMessage(30)
        if (responseStr == null) {
            result["success"] = false
            result["error"] = "读取上传开始响应失败"
            return result
        }

        LogUtil.d(
            TAG,
            "收到上传开始响应: $responseStr"
        )

        try {
            val responseJson = JSONObject(responseStr)
            val response = responseJson

            val status = response.getInt("status")
            val message = response.optJSONObject("message")


            // 处理断点续传响应
            val uploadId: String
            val receivedChunks: MutableMap<String, Boolean> = HashMap()

            when (status) {
                0 -> {
                    if (message == null) {
                        result["success"] = false
                        result["error"] = "无效的响应格式"
                        return result
                    }
                    uploadId = message.optString("upload_id")
                    if (uploadId.isEmpty()) {
                        result["success"] = false
                        result["error"] = "无法获取upload_id"
                        return result
                    }
                }

                2 -> {
                    if (message == null) {
                        result["success"] = false
                        result["error"] = "无效的响应格式"
                        return result
                    }
                    uploadId = message.optString("upload_id")
                    if (uploadId.isEmpty()) {
                        result["success"] = false
                        result["error"] = "无法获取upload_id"
                        return result
                    }


                    // 获取已接收的分片信息
                    val receivedChunksJson = message.optJSONObject("received_chunks")
                    if (receivedChunksJson != null) {
                        val keys = receivedChunksJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            receivedChunks[key] = receivedChunksJson.getBoolean(key)
                        }
                    }

                    LogUtil.d(TAG, "断点续传：已接收 " + receivedChunks.size + " 个分片")
                }

                -1 -> {
                    result["success"] = false
                    result["error"] = "服务器错误: " + response.opt("message")
                    return result
                }

                else -> {
                    result["success"] = false
                    result["error"] = "未知状态码: $status"
                    return result
                }
            }

            LogUtil.d(TAG, "文件 [" + task.filePath + "] 获取到上传ID: " + uploadId)

            task.uploadId = uploadId

            result["success"] = true
            result["uploadId"] = uploadId
            result["receivedChunks"] = receivedChunks
            return result
        } catch (e: JSONException) {
            LogUtil.e(TAG, "解析上传开始响应失败", e)
            result["success"] = false
            result["error"] = "解析上传开始响应失败: " + e.message
            return result
        }
    }

    // 发送单个文件分片
    private fun sendSingleChunk(
        task: UploadTask,
        chunkIndex: Int,
        receivedChunks: Map<String, Boolean>
    ): Boolean {
        // 检查分片是否已上传
        if (receivedChunks.containsKey(chunkIndex.toString()) &&
            java.lang.Boolean.TRUE == receivedChunks[chunkIndex.toString()]
        ) {
            LogUtil.d(TAG, "文件 [" + task.filePath + "] 分片 " + chunkIndex + " 已上传，跳过")
            return true
        }


        // 计算当前分片的预期大小
        var expectedSize = task.fileInfo.chunkSize // 默认为1MB
        if (chunkIndex == task.fileInfo.totalChunks - 1) {
            // 最后一个分片的大小 = 总大小 - (已完成分片数 * 分片大小)
            expectedSize = (task.fileInfo.fileSize -
                    (task.fileInfo.totalChunks - 1).toLong() * task.fileInfo.chunkSize).toInt()
        }

        try {
            // 定位到正确的文件位置
//            val offset = chunkIndex.toLong() * task.fileInfo.chunkSize
//            (task.fileInputStream as FileInputStream).channel.position(offset)

            val offset = chunkIndex.toLong() * task.fileInfo.chunkSize
            var bytesToSkip = offset
            val bufferArray = ByteArray(1024) // Choose an appropriate buffer size.
            while (bytesToSkip > 0) {
                val bytesRead = task.fileInputStream.read(
                    bufferArray,
                    0,
                    minOf(bytesToSkip.toInt(), bufferArray.size)
                )
                if (bytesRead <= 0) {
                    // If read() returns 0 or less, it means the end of the stream has been reached.
                    // Handle this situation based on your logic.
                    break // Or throw an exception
                }
                bytesToSkip -= bytesRead
            }

            // 读取分片数据
            val buffer = ByteArray(expectedSize)
            val n = task.fileInputStream.read(buffer)
            if (n <= 0) {
                LogUtil.e(TAG, "读取文件分片失败")
                return false
            }


            // 准备分片数据
            val chunkData: ByteArray
            if (n == expectedSize) {
                chunkData = buffer
            } else {
                chunkData = ByteArray(n)
                System.arraycopy(buffer, 0, chunkData, 0, n)
            }

            LogUtil.d(
                TAG, "文件 [" + task.filePath + "] 分片 " + chunkIndex +
                        "，实际大小: " + n + " 字节, 预期大小: " + expectedSize + " 字节"
            )


            // 发送分片
            if (!sendFileChunk(
                    task.uploadId, task.fileInfo.fileId,
                    chunkIndex, chunkData, expectedSize
                )
            ) {
                LogUtil.e(TAG, "发送文件分片失败")
                return false
            }


            // 等待服务器确认
            val responseStr = readMessage(30)
            if (responseStr == null) {
                LogUtil.e(TAG, "读取分片响应失败")
                return false
            }

            LogUtil.d(
                TAG,
                "收到分片响应: $responseStr"
            )

            val response = JSONObject(responseStr)
            if (response.getInt("status") == -1) {
                LogUtil.e(TAG, "服务器错误: " + response.opt("message"))
                return false
            }

            return true
        } catch (e: IOException) {
            LogUtil.e(TAG, "发送分片失败", e)
            return false
        } catch (e: JSONException) {
            LogUtil.e(TAG, "发送分片失败", e)
            return false
        }
    }

    // 上传多个文件
    fun uploadMultipleFiles(filePaths: List<String>, targetPaths: List<String?>) {
        if (filePaths.size != targetPaths.size) {
            if (uploadListener != null) {
                mainHandler.post { uploadListener!!.onError("", "文件路径和目标路径数量不匹配") }
            }
            return
        }


        // 连接WebSocket
        if (!event.connect()) {
            if (uploadListener != null) {
                mainHandler.post { uploadListener!!.onError("", "WebSocket连接失败") }
            }
            return
        }

        Thread(Runnable {
            try {
                // 准备所有上传任务
                val tasks: MutableList<UploadTask> = ArrayList()
                val receivedChunksList: MutableList<Map<String, Boolean>?> = ArrayList()

                for (i in filePaths.indices) {
                    val filePath = filePaths[i]


                    // 检查文件是否存在
                    val file = File(filePath)
                    if (!file.exists()) {
                        if (uploadListener != null) {
                            mainHandler.post { uploadListener!!.onError(filePath, "文件不存在") }
                        }
                        continue
                    }

                    val task = prepareUploadTask(filePath, targetPaths[i])
                    if (task == null) {
                        if (uploadListener != null) {
                            mainHandler.post {
                                uploadListener!!.onError(
                                    filePath,
                                    "准备上传任务失败"
                                )
                            }
                        }
                        continue
                    }

                    // 初始化上传任务
                    val initResult = initializeUploadTask(task)
                    if (!(initResult["success"] as Boolean)) {
                        if (uploadListener != null) {
                            val error = initResult["error"] as String?
                            mainHandler.post { uploadListener!!.onError(filePath, error) }
                        }
                        continue
                    }

                    tasks.add(task)
                    receivedChunksList.add(initResult["receivedChunks"] as Map<String, Boolean>?)
                }

                if (tasks.isEmpty()) {
                    if (uploadListener != null) {
                        mainHandler.post { uploadListener!!.onError("", "没有可上传的文件") }
                    }
                    event.close()
                    return@Runnable
                }


                // 获取最大分片数
                var maxChunks = 0
                for (task in tasks) {
                    if (task.fileInfo.totalChunks > maxChunks) {
                        maxChunks = task.fileInfo.totalChunks
                    }
                }


                // 交替上传分片
                for (chunkIndex in 0 until maxChunks) {
                    LogUtil.d(TAG, "开始上传第 " + (chunkIndex + 1) + " 轮分片")

                    for (i in tasks.indices) {
                        val task = tasks[i]

                        if (chunkIndex < task.fileInfo.totalChunks) {
                            LogUtil.d(
                                TAG, "上传文件 [" + task.filePath + "] 的第 " +
                                        (chunkIndex + 1) + "/" + task.fileInfo.totalChunks + " 个分片"
                            )

                            val success = sendSingleChunk(
                                task, chunkIndex,
                                receivedChunksList[i]!!
                            )
                            if (!success) {
                                if (uploadListener != null) {
                                    val path = task.filePath
                                    mainHandler.post {
                                        uploadListener!!.onError(
                                            path,
                                            "发送分片失败"
                                        )
                                    }
                                }
                                continue
                            }


                            // 更新进度
                            if (uploadListener != null) {
                                val path = task.filePath
                                val progress = chunkIndex + 1
                                val total = task.fileInfo.totalChunks
                                mainHandler.post {
                                    uploadListener!!.onProgress(
                                        path,
                                        progress,
                                        total
                                    )
                                }
                            }


                            // 每个分片之间暂停一小段时间
                            Thread.sleep(100)
                        }
                    }
                }

                LogUtil.d(TAG, "所有文件分片上传完成，等待服务器最终确认")


                // 等待最终确认，最多等待3次
                for (i in 0..2) {
                    val responseStr = readMessage(30)
                    if (responseStr == null) {
                        if (i < 2) {
                            LogUtil.d(TAG, "等待最终确认超时，正在重试 (" + (i + 1) + "/3)...")
                            continue
                        }
                        if (uploadListener != null) {
                            mainHandler.post { uploadListener!!.onError("", "等待最终确认失败") }
                        }
                        break
                    }

                    LogUtil.d(
                        TAG,
                        "收到响应: $responseStr"
                    )

                    try {
                        val response = JSONObject(responseStr)
                        if (response.getInt("status") == 1) {
                            LogUtil.d(TAG, "文件上传成功: " + response.opt("message"))
                            if (uploadListener != null) {
                                for (task in tasks) {
                                    val path = task.filePath
                                    mainHandler.post { uploadListener!!.onSuccess(path) }
                                }
                            }
                            break
                        } else if (response.getInt("status") == -1) {
                            if (uploadListener != null) {
                                val error = response.optString("message", "服务器错误")
                                mainHandler.post { uploadListener!!.onError("", error) }
                            }
                            break
                        }

                        LogUtil.d(TAG, "收到中间状态响应，继续等待...")
                    } catch (e: JSONException) {
                        LogUtil.e(TAG, "解析响应失败", e)
                    }
                }


                // 关闭所有文件流
                for (task in tasks) {
                    try {
                        if (task.fileInputStream != null) {
                            task.fileInputStream.close()
                        }
                    } catch (e: IOException) {
                        LogUtil.e(TAG, "关闭文件流失败", e)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "上传过程中发生错误", e)
                if (uploadListener != null) {
                    val error = e.message
                    mainHandler.post { uploadListener!!.onError("", error) }
                }
            } finally {
                event.close()
            }
        }).start()
    }

    // 单文件上传方法（保留以便向后兼容）
    fun uploadFile(filePath: String, targetPath: String?) {
        val filePaths: MutableList<String> = ArrayList()
        val targetPaths: MutableList<String?> = ArrayList()

        filePaths.add(filePath)
        targetPaths.add(targetPath)

        uploadMultipleFiles(filePaths, targetPaths)
    }

    companion object {
        private const val TAG = "FileUploader"
        private const val CHUNK_SIZE = 1024 * 1024 // 1MB
    }
}