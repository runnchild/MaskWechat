package com.leven.uni.call.utils;

public class ErrorEnum {
    //默认失败
    public static int DEFAULT_ERROR = -1;
    //权限申请未全部统一
    public static int PERMISSION_REQUEST_NOT_ALL_GRANT = -100;
    //短信发送失败
    public static int SEND_SMS_FAILED = -101;
    //短信发送无服务
    public static int SEND_SMS_NO_SERVICE = -102;
    //短信发送无服务
    public static int SEND_SMS_PUD_IS_NULL = -103;
    //短信发送无线电关闭
    public static int SEND_SMS_RADIO_CLOSE = -104;
    //短信未送达
    public static int SEND_SMS_NO_DELIVERED = -105;
}
