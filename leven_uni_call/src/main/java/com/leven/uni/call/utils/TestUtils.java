package com.leven.uni.call.utils;

import java.util.Date;

public class TestUtils {
    //是否是测试包
    public static boolean isTest = false;
    //从什么时间戳开始计算过期时间
    private static final long timestamp = 1740709877;
    //过期的秒数604800
    private static final int outTime = 2 * 86400;

    public static void isTest() throws LevenException {
        long thisTime = new Date().getTime() / 1000;
        if(isTest && thisTime - timestamp > outTime){
            throw new LevenException("当前测试版本已过期，请购买正式版");
        }
    }

    /**
     * 获取当前剩余的天数
     */
    public static int getRemainDay(){
        long thisTime = new Date().getTime() / 1000;
        long remainTime = thisTime - timestamp;
        //剩余的秒
        long remainSeconds = outTime - remainTime;
        return (int) (remainSeconds / 86400);
    }
}
