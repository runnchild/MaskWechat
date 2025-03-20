package com.leven.uni.call;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

public class BaseModule {

    Context context;
    /**
     * 获取设备信息的所需的权限信息
     */
    protected static final String[] NEEDED_PERMISSIONS_GET_DEVICE_INFO = new String[]{
            Manifest.permission.READ_PHONE_STATE
    };


    /**
     * 获取Context
     */
    protected Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * 获取Activity
     */
    protected Activity getActivity() {
        return (Activity) getContext();
    }


    /**
     * 权限检查
     *
     * @param neededPermissions 需要的权限
     * @return 是否全部被允许
     */
    protected boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(getContext(), neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    //    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean isAllGranted = true;
        for (int grantResult : grantResults) {
            isAllGranted &= (grantResult == PackageManager.PERMISSION_GRANTED);
        }
        afterRequestPermission(requestCode, permissions, grantResults, isAllGranted);
    }

    /**
     * 请求权限的回调
     *
     * @param requestCode  请求码
     * @param permissions  权限列表
     * @param grantResults 权限申请结果
     * @param isAllGranted 是否全部被同意
     */
    public void afterRequestPermission(int requestCode, String[] permissions, int[] grantResults, boolean isAllGranted) {

    }
}
