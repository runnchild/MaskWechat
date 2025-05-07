import com.example.libcontacts.ShellUtils
import com.lu.magic.util.log.LogUtil
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

object SmsUtils {

    suspend fun getSmsAsJsonArray(
        id: String? = null,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): JSONArray = withContext(Dispatchers.IO) {
        // 在 IO 线程执行方法
        getSms(id, startTimeMillis, endTimeMillis)
    }

    private fun getSms(id: String?, startTimeMillis: Long, endTimeMillis: Long): JSONArray {
        // 原来的同步逻辑保持不变
        val adbCommand = if (!id.isNullOrBlank()) {
            "content query --uri content://sms --projection _id,address,body,date,type --where \"_id = $id\""
        } else {
            "content query --uri content://sms --projection _id,address,body,date,type --where \"date > $startTimeMillis AND date <= $endTimeMillis\""
        }
        LogUtil.e("Executing ADB command: ${adbCommand.take(500)}")

        val output = ShellUtils.execRootCmd1(adbCommand)
        if (output.toString().isBlank()) {
            LogUtil.e("ADB output is empty or null. No messages were fetched.")
            return JSONArray()
        }
        return try {
            convertToJSONArray(output.toString())
        } catch (e: Exception) {
            LogUtil.e("Failed to parse ADB output into JSON Array: ${e.message}")
            JSONArray()
        }
    }

    private fun convertToJSONArray(input: String): JSONArray {
        // 省略重复内容 ...
        val jsonArray = JSONArray()
        val rows = input.split("\n").filter { it.isNotBlank() }
        for (row in rows) {
            try {
                val data = row.substringAfter("Row: ").trim()
                val pairs = data.split(", ")
                val jsonObject = JSONObject()
                pairs.forEach { pair ->
                    val separatorIndex = pair.indexOf("=")
                    if (separatorIndex != -1) {
                        val key = pair.substring(0, separatorIndex).trim()
                        val value = pair.substring(separatorIndex + 1).trim()
                        jsonObject.put(key, value)
                    }
                }
                jsonArray.put(jsonObject)
            } catch (e: Exception) {
                LogUtil.e("Failed to process row: \"$row\", error: ${e.message}")
            }
        }
        return jsonArray
    }
}


// 测试函数
fun main() {
    // 示例：获取过去 1 天的短信
    val currentTimeMillis = System.currentTimeMillis()
    val oneDayMillis = 24 * 60 * 60 * 1000L
    val startTime = currentTimeMillis - oneDayMillis
    val endTime = currentTimeMillis

    // 调用方法获取短信数据
    GlobalScope.launch(Dispatchers.Main) {
        val jsonArray = SmsUtils.getSmsAsJsonArray(null, startTime, endTime)
        println("Result as JSON Array:")
        println(jsonArray.toString(4)) // 打印格式化好的 JSON 数据
    }
}