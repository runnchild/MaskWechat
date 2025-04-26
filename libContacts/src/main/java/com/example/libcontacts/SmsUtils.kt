import com.example.libcontacts.ShellUtils
import com.lu.magic.util.log.LogUtil
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsUtils {

    fun getSmsAsJsonArray(
        id: String? = null,
        startTimeMillis: Long,   // 查询起始时间（时间戳，毫秒）
        endTimeMillis: Long      // 查询结束时间（时间戳，毫秒）
    ): JSONArray {
        // 日期转换工具，用于友好日志输出
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 构造 adb 查询命令
        var adbCommand = "content query --uri content://sms " +
                "--projection _id,address,body,date,type "
        adbCommand += if (!id.isNullOrBlank()) {
            "--where \"_id = $id\""
        } else {
            "--where \"date > $startTimeMillis AND date <= $endTimeMillis\""
        }
        LogUtil.e("Executing adb command: $adbCommand")
        LogUtil.e(
            "Fetching SMS from ${dateFormat.format(Date(startTimeMillis))} to ${
                dateFormat.format(
                    Date(endTimeMillis)
                )
            }"
        )

        // 执行 adb 命令获取短信原始输出
//        val process = Runtime.getRuntime().exec(adbCommand)
//        val output = process.inputStream.bufferedReader().readText()
        val output = ShellUtils.execRootCmd1(adbCommand)
        LogUtil.e("adb output: $output")
        // 转换为 JSON 数组
        return convertToJSONArray(output.toString())
    }

    private fun convertToJSONArray(input: String): JSONArray {
        // 定义 JSON 数组用于存放转换结果
        val jsonArray = JSONArray()

        // 按行分割 adb 输出结果
        val rows = input.split("\n").filter { it.isNotBlank() }

        for (row in rows) {
            // 移除 "Row: XX" 前缀
            val data = row.substringAfter("Row: ").substringAfter(" ")  // 跳过 Row: XX 的部分

            // 将逗号分隔的键值对拆分为 Map
            val pairs = data.split(", ")
            val jsonObject = JSONObject()

            pairs.forEach {
                val keyValue = it.split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()  // 键 (去掉多余空格)
                    var value = keyValue[1].trim()  // 值 (去掉多余空格)
//                value = when (key) {
//                    "date" -> value.toLongOrNull() ?: value.toString()  // 将 date 转为 Long 类型
//                    "type", "_id" -> value.toIntOrNull() ?: value.toString()  // 将 _id 和 type 转为 Int 类型
//                    else -> value  // 其他保留为字符串类型
//                }
                    jsonObject.put(key, value)
                }
            }

            // 将 JSON 对象加入数组
            jsonArray.put(jsonObject)
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
    val jsonArray = SmsUtils.getSmsAsJsonArray(null, startTime, endTime)
    println("Result as JSON Array:")
    println(jsonArray.toString(4)) // 打印格式化好的 JSON 数据
}