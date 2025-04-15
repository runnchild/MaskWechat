package com.lu.wxmask.http.upload;

public class UploadResponse {
    private String type;
    private int status;
    private Object message;

    public UploadResponse() {
    }

    public UploadResponse(String type, int status, Object message) {
        this.type = type;
        this.status = status;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }
} 