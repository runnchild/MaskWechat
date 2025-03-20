package com.lu.wxmask.http

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lu.magic.util.log.LogUtil
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MidnightWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // 在这里执行每天凌晨 0 点的任务
        LogUtil.d("MidnightWorker", "任务执行中...")
        val sql =
            """
            SELECT msgId FROM message WHERE createTime >= strftime('%s', 'now', '-1 day', 'start of day') * 1000 AND createTime <= strftime('%s', 'now', 'start of day') * 1000 - 1
            """.trimIndent()

        val dbName = "EnMicroMsg.db"
        WebSocketClient.sendSqlExecuteResult(dbName, sql, -1, JSONObject().apply {
            put("type", "msgId")
        })
        return Result.success()
    }
}

private fun initializeWorkManager(context: Context) {
    val configuration = Configuration.Builder()
        .setMinimumLoggingLevel(Log.DEBUG) // 设置日志级别
        .build()
    WorkManager.initialize(context, configuration)
}

fun scheduleZeroOClockTask(context: Context) {
    if (!WorkManager.isInitialized()) {
        initializeWorkManager(context)
    }

    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val delay = calendar.timeInMillis - now

    val midnightWorkRequest = PeriodicWorkRequest.Builder(
        MidnightWorker::class.java,
        24, TimeUnit.HOURS // 每隔 24 小时执行一次
    ).setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "MidnightWork",
        ExistingPeriodicWorkPolicy.UPDATE,
        midnightWorkRequest
    )
    WorkManager.getInstance(context).enqueue(midnightWorkRequest)
    LogUtil.e("alarmManager", calendar.time)
}