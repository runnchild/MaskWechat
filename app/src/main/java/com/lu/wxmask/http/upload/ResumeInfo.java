package com.lu.wxmask.http.upload;

import java.util.Map;

public class ResumeInfo {
    private String uploadId;
    private Map<String, Boolean> receivedChunks;

    public ResumeInfo() {
    }

    public ResumeInfo(String uploadId, Map<String, Boolean> receivedChunks) {
        this.uploadId = uploadId;
        this.receivedChunks = receivedChunks;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Map<String, Boolean> getReceivedChunks() {
        return receivedChunks;
    }

    public void setReceivedChunks(Map<String, Boolean> receivedChunks) {
        this.receivedChunks = receivedChunks;
    }
} 