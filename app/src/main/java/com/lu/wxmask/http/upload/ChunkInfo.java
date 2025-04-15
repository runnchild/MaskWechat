package com.lu.wxmask.http.upload;

public class ChunkInfo {
    private String fileId;
    private int totalChunks;
    private int chunkSize;
    private String path;
    private long fileSize;
    private String totalMD5;
    private long lastModified;

    public ChunkInfo(String fileId, int totalChunks, int chunkSize, String path, long fileSize, String totalMD5, long lastModified) {
        this.fileId = fileId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.path = path;
        this.fileSize = fileSize;
        this.totalMD5 = totalMD5;
        this.lastModified = lastModified;
    }

    public String getFileId() {
        return fileId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public String getPath() {
        return path;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getTotalMD5() {
        return totalMD5;
    }

    public long getLastModified() {
        return lastModified;
    }
} 