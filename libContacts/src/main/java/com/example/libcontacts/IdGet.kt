package com.example.libcontacts

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings.Secure
import com.lu.magic.util.log.LogUtil

object IdGet {
    // 新增 SharedPreferences 相关代码
    private val PREFS_NAME = "wxmask_shared_prefs"
    private val KEY_TOKEN = "token"
    private val KEY_WXID = "wxId"

    fun androidId(context: Context): String {
        return try {
            getAndroidId(context)
            Secure.getString(context.contentResolver, "android_id")
        } catch (e: Exception) {
            ""
        }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS)
    }

    fun saveToken(context: Context, token: String) {
        getSharedPreferences(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? {
        val token = getSharedPreferences(context).getString(KEY_TOKEN, "")
        return token
    }

    fun saveWxId(context: Context, wxId: String) {
        getSharedPreferences(context).edit().putString(KEY_WXID, wxId).apply()
    }

    fun getWxId(context: Context): String {
        return getSharedPreferences(context).getString(KEY_WXID, "") ?: ""
    }

    fun setAndroidId(context: Context, androidId: String) {
        LogUtil.i(context, "IdGet", "setAndroidId: $androidId")
        getSharedPreferences(context).edit().putString("android_id", androidId).apply()
    }

    fun getAndroidId(context: Context): String {
        val androidId = getSharedPreferences(context).getString("android_id", "") ?: ""
        LogUtil.i(context, "IdGet", "getAndroidId: $androidId")
        return androidId
    }

    fun getImei(context: Context): String? {
        return getSharedPreferences(context).getString("imei", IMEIHelper.tryGetpropIMEI().apply {
            setImei(context, this)
        })
    }

    fun setImei(context: Context, imei: String?) {
        getSharedPreferences(context).edit().putString("imei", imei).apply()
    }
}