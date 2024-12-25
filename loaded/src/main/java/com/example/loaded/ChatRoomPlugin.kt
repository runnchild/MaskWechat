package com.example.loaded

import com.lu.magic.util.log.LogUtil
import de.robv.android.xposed.*
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * @author DX
 * 注意：该类不要自己写构造方法，否者可能会hook不成功
 * 开发Xposed模块完成以后，建议修改xposed_init文件，并将起指向这个类,以提升性能
 * 所以这个类需要implements IXposedHookLoadPackage,以防修改xposed_init文件后忘记
 * Created by DX on 2017/10/4.
 */
class ChatRoomPlugin : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private var sharedPreferences: XSharedPreferences? = null

    @Throws(Throwable::class)
    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
//        if ("xxx.xxx.xxx" == loadPackageParam.packageName) {
//            XposedHelpers.findAndHookMethod(
//                "xxx.xxx.xxx.xxxClass",
//                loadPackageParam.classLoader,
//                "xxxMethod",
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        param.result = "Hook succeed"
//                        val x = sharedPreferences!!.getInt("example", 1)
//                    }
//                })
//        }
        LogUtil.i("他来了")
    }

    override fun initZygote(startupParam: StartupParam) {
        sharedPreferences = XSharedPreferences(modulePackageName, "default")
        XposedBridge.log(modulePackageName + " initZygote")
    }

    companion object {
        private val modulePackageName = ChatRoomPlugin::class.java.getPackage().name
    }
}