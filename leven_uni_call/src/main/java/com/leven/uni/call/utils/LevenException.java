package com.leven.uni.call.utils;

public class LevenException extends Exception{
    private int code;

    public LevenException(){
        super();
    }

    public LevenException(String message){
        super(message);
        this.code = ErrorEnum.DEFAULT_ERROR;
    }
    public LevenException(int code, String message){
        super(message);
        this.code = code;
    }

    /*获取code*/
    public int getCode(){
        return this.code;
    }
}
