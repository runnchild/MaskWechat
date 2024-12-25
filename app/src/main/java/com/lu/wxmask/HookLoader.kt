package com.lu.wxmask

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.lu.magic.util.log.LogUtil
import com.lu.wxmask.plugin.part.ChatRoomPlugin
import dalvik.system.PathClassLoader
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File

/**
 * @author DX
 * 这种方案建议只在开发调试的时候使用，因为这将损耗一些性能(需要额外加载apk文件)，调试没问题后，直接修改xposed_init文件为正确的类即可
 * 可以实现免重启，由于存在缓存，需要杀死宿主程序以后才能生效
 * Created by DX on 2017/10/4.
 * Modified by chengxuncc on 2019/4/16.
 */
class HookLoader {

    /**
     * 实际hook逻辑处理类的入口方法
     */
    private val handleHookMethod = "handleLoadPackage"
    private val initMethod = "initZygote"

    /**
     * 重定向handleLoadPackage函数前会执行initZygote
     *
     * @param loadPackageParam
     * @throws Throwable
     */
    @Throws(Throwable::class)
    fun handleLoadPackage(loadPackageParam: LoadPackageParam, startupparam: StartupParam? ) {
        LogUtil.i("开始执行HookLoader")
        // 排除系统应用
        if (loadPackageParam.appInfo == null ||
            loadPackageParam.appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 1
        ) {
            return
        }
        //将loadPackageParam的classloader替换为宿主程序Application的classloader,解决宿主程序存在多个.dex文件时,有时候ClassNotFound的问题
        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach",
            Context::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as Context
                    loadPackageParam.classLoader = context.classLoader
                    try {
                        val cls = getApkClass(context, modulePackageName, "com.lu.wxmask.plugin.part.ChatRoomPlugin")
                        val instance = cls.newInstance()
                        LogUtil.i("getApkClass instance:$instance")
                        (instance as? ChatRoomPlugin)?.handleLoadPackage(loadPackageParam)
                        cls.getDeclaredMethod(initMethod, startupparam!!.javaClass)
                            .invoke(instance, startupparam)
                        cls.getDeclaredMethod(handleHookMethod, loadPackageParam.javaClass)
                            .invoke(instance, loadPackageParam)
                    } catch (e: Exception) {
                        // 找不到initZygote方法
                        LogUtil.e(e)
                        e.printStackTrace()
                    }
                }
            })
    }


    @Throws(Throwable::class)
    private fun getApkClass(
        context: Context,
        modulePackageName: String,
        handleHookClass: String
    ): Class<*> {
        LogUtil.i("getApkClass : $handleHookClass, $modulePackageName")
        val apkFile = findApkFile(context, modulePackageName)
            ?: throw RuntimeException("寻找模块apk失败")
        LogUtil.i("apkFile : $apkFile")
        //加载指定的hook逻辑处理类，并调用它的handleHook方法
        val pathClassLoader = PathClassLoader(
            apkFile.absolutePath,
            XposedBridge.BOOTCLASSLOADER
        )
        LogUtil.i("apkFile pathClassLoader: $pathClassLoader")
        return Class.forName(handleHookClass, true, pathClassLoader)
    }

    /**
     * 根据包名构建目标Context,并调用getPackageCodePath()来定位apk
     *
     * @param context           context参数
     * @param modulePackageName 当前模块包名
     * @return apk file
     */
    private fun findApkFile(context: Context?, modulePackageName: String): File? {
        if (context == null) {
            return null
        }
        try {
            val moudleContext = context.createPackageContext(
                modulePackageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            val apkPath = moudleContext.packageCodePath
            return File(apkPath)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        //按照实际使用情况修改下面几项的值
        /**
         * 当前Xposed模块的包名,方便寻找apk文件
         */
        private val modulePackageName = "com.lu.wxmask"
    }
}