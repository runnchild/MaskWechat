package com.example.libcontacts

import android.util.Log
import com.lu.magic.util.log.LogUtil
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object IMEIHelper {

    private const val TAG = "IMEIHelper"

    // 执行命令并获取 stdout + stderr
    fun execRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()

            var line: String?
            while (stdout.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            while (stderr.readLine().also { line = it } != null) {
                output.appendLine("[stderr] $line")
            }

            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }

    // 方法 1：通过 getprop
    fun tryGetpropIMEI(): String? {
        val props = execRootCommand("su -c getprop | grep -i imei")

        val imeis = props.lines()
            .filter { it.contains("imei", ignoreCase = true) }
            .joinToString("\n")
        return extractFirstIMEI(imeis)
    }

    private fun extractFirstIMEI(getpropOutput: String): String? {
        val regex = Regex("\\b\\d{15}\\b")
        return regex.find(getpropOutput)?.value
    }

    // 方法 2：读取 /efs 目录（部分三星设备）
    private fun tryReadEfsIMEI(): String {
        return execRootCommand("cat /efs/imei/.imei")
    }

    // 方法 3：使用 service call（通用 Android 方法）
    private fun tryServiceCallIMEI(): String {
        val raw = execRootCommand("service call iphonesubinfo 1")
        return parseServiceCallOutput(raw)
    }

    // 把 hex 输出解析成字符串
    private fun parseServiceCallOutput(raw: String): String {
        val regex = Regex("'.*?'")
        val hexParts = regex.findAll(raw).mapNotNull {
            it.value.replace("'", "").replace(".", "").trim().takeIf { it.isNotEmpty() }
        }.joinToString("")
        val chars = hexParts.chunked(4).mapNotNull { chunk ->
            try {
                val hex = chunk.substring(2, 4) + chunk.substring(0, 2) // endian 颠倒
                val intVal = hex.toInt(16)
                if (intVal in 32..126) intVal.toChar().toString() else null
            } catch (e: Exception) {
                null
            }
        }
        return chars.joinToString("").trim()
    }

    // 综合调用
}
