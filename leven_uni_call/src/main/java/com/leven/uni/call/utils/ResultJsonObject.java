package com.leven.uni.call.utils;

import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ResultJsonObject {
    private int code;
    private Object data;
    private String message;

    /**
     * 返回成功
     * @param data  数据
     * @param message   消息提示
     * @return  JSONObject
     */
    public JSONObject returnSuccess(Object data, String message){
        this.code = 0;
        this.data = data;
        this.message = message;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", this.code);
        jsonObject.put("data", this.data);
        jsonObject.put("message", this.message);
        if (TestUtils.isTest) {
            jsonObject.put("warning", getWarningMessage());
        }
        return jsonObject;
    }

    /**
     * 返回成功
     * @return  JSONObject
     */
    public JSONObject returnSuccess(){
        return returnSuccess(new HashMap<>(), "");
    }

    /**
     * 返回成功
     * @return  JSONObject
     */
    public JSONObject returnSuccess(String message){
        return returnSuccess(new HashMap<>(), message);
    }

    /**
     * 返回失败
     */
    public JSONObject returnFailed(int code, Object data, String message){
        this.code = code;
        this.data = data;
        this.message = message;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", this.code);
        jsonObject.put("data", this.data);
        jsonObject.put("message", this.message);
        if (TestUtils.isTest) {
            jsonObject.put("warning", getWarningMessage());
        }
        return jsonObject;
    }

    /**
     * 返回失败
     */
    public JSONObject returnFailed(int code, String message){
        return returnFailed(code, new HashMap<>(), message);
    }

    /**
     * 返回失败
     */
    public JSONObject returnFailed(String message){
        return returnFailed(ErrorEnum.DEFAULT_ERROR, new HashMap<>(), message);
    }

    /**
     * 返回event数据
     */
    public Map<String, Object> returnEventData(Object data){
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("detail", data);
        if (TestUtils.isTest) {
            jsonObject.put("warning", getWarningMessage());
        }
        return jsonObject;
    }

    /**
     * 返回告警信息
     * @return
     */

    private String getWarningMessage(){
        int day = TestUtils.getRemainDay();
        String warning = "当前使用的是测试版本，" + day + "天后将会过期，请尽快更换正式版本";
        if(day < 0){
            warning = "当前使用的是测试版本，版本已过期，请更换正式版本";
        }else if(day == 0){
            warning = "当前使用的是测试版本，试用期已不足一天，请尽快更换正式版本";
        }
        return warning;
    }
}
