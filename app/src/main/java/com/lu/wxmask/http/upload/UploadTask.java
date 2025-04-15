package com.lu.wxmask.http.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class UploadTask {
    private String filePath;
    private String targetPath;
    private ChunkInfo fileInfo;
    private String uploadId;
    private InputStream fileInputStream;
    private File file;

    public UploadTask(String filePath, String targetPath, ChunkInfo fileInfo) {
        this.filePath = filePath;
        this.targetPath = targetPath;
        this.fileInfo = fileInfo;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public ChunkInfo getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(ChunkInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public InputStream getFileInputStream() {
        return fileInputStream;
    }

    public void setFileInputStream(InputStream fileInputStream) {
        this.fileInputStream = fileInputStream;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
} 