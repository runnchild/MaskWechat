package com.leven.uni.call.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class FileUtil {
    private static final String TAG = "FileUtil";
    /**
     * 判断SD卡上的文件是否存在
     */
    public static boolean isFileExist(String fileName) {
        File file = new File(fileName);
        return file.exists();
    }

    /**
     * 判断是否是文件
     */
    public static boolean isFile(String fileName){
        File file = new File(fileName);
        return file.exists() && file.isFile();
    }

    /**
     * 判断是否是文件夹
     */
    public static boolean isDirectory(String fileName){
        File file = new File(fileName);
        return file.exists() && file.isDirectory();
    }

    /**
     * searchFile 查找文件并加入到ArrayList 当中去
     *
     * @param context
     * @param keyword
     * @param filepath
     * @return
     */
    public static List<JSONObject> searchFile(Context context, String keyword, File filepath) {
        List<JSONObject> list = new ArrayList<>();
        JSONObject rowItem = null;
        int index = 0;
        // 判断SD卡是否存在
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File[] files = filepath.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (file.getName().replace(" ", "").toLowerCase().contains(keyword)) {
                            rowItem = new JSONObject();
                            rowItem.put("number", index); // 加入序列号
                            rowItem.put("fileName", file.getName());// 加入名称
                            rowItem.put("path", file.getPath()); // 加入路径
                            rowItem.put("size", file.length() + ""); // 加入文件大小
                            list.add(rowItem);
                        }
                        // 如果目录可读就执行（一定要加，不然会挂掉）
                        if (file.canRead()) {
                            list.addAll(searchFile(context, keyword, file)); // 如果是目录，递归查找
                        }
                    } else {
                        // 判断是文件，则进行文件名判断
                        try {
                            if (file.getName().replace(" ", "").contains(keyword) || file.getName().replace(" ", "").contains(keyword.toUpperCase())) {
                                rowItem = new JSONObject();
                                rowItem.put("number", index); // 加入序列号
                                rowItem.put("fileName", file.getName());// 加入名称
                                rowItem.put("path", file.getPath()); // 加入路径
                                rowItem.put("size", file.length() + ""); // 加入文件大小
                                list.add(rowItem);
                                index++;
                            }
                        } catch (Exception e) {
//                            Toast.makeText(context, "查找发生错误!", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * 得到所有文件
     *
     * @param dir
     * @return
     */
    public static ArrayList<String> getAllFiles(File dir, boolean child) {
        ArrayList<String> allFiles = new ArrayList<String>();
        if(dir != null){
            // 递归取得目录下的所有文件及文件夹
            File[] files = dir.listFiles();
            if (files == null) {
                return allFiles;
            }
            if(files.length <= 1000){
                Arrays.sort(files, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });
            }
            for (File file : files) {
                if (file.isDirectory() && child) {
                    if (file.canRead()) {
                        allFiles.addAll(getAllFiles(file, child));
                    }
                } else if (!file.isHidden()) {
                    allFiles.add(file.getPath());
                }
            }
        }
        return allFiles;
    }

    /**
     * 得到所有文件
     *
     * @param dir
     * @return
     */
    public static ArrayList<String> getFiles(File dir, boolean child, FileFilter filter) {
        ArrayList<String> allFiles = new ArrayList<String>();
        if(dir == null){
            return allFiles;
        }
        // 递归取得目录下的所有文件及文件夹
        File[] files = dir.listFiles(filter);
        if (files == null) {
            return allFiles;
        }
        for (int i = 0; i < Objects.requireNonNull(files).length; i++) {
            File file = files[i];
//            allFiles.add(file.getPath());
            if (file.isDirectory() && child) {
                if (file.canRead()) {
                    allFiles.addAll(getAllFiles(file, true));
                }
            } else if(!file.isHidden()) {
                allFiles.add(file.getPath());
            }
        }
        return allFiles;
    }

    /**
     * 拷贝文件
     *
     * @param fromFile
     * @param toFile
     * @throws IOException
     */
    public static boolean copyFile(File fromFile, String toFile) throws IOException {
        FileInputStream from = null;
        FileOutputStream to = null;
        boolean isSuccess = true;
        File toFileObject = new File(toFile);
        try {
            File toFileParent = toFileObject.getParentFile();
            if (toFileParent != null && !toFileParent.exists()) {
                toFileParent.mkdirs();
            }
            from = new FileInputStream(fromFile);
            to = new FileOutputStream(toFile);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytesRead); // write
            }
        } finally {
            if (from != null){
                try {
                    from.close();
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                    isSuccess = false;
                }
            }
            if (to != null) {
                try {
                    to.close();
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                    isSuccess = false;
                }
            }
            //判断移动前的大小和移动后的大小是否一致
            if(fromFile.length() != toFileObject.length()){
                isSuccess = false;
            }
        }
        return isSuccess;
    }

    /**
     * 删除文件
     *
     * @param path
     */
    public static void deleteFile(String path) {
        File file = new File(path);
        deleteFile(file);
    }

    /**
     * 删除文件
     *
     * @param file
     */
    public static void deleteFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File files[] = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteFile(files[i]);
            }
        }
        file.delete();
    }
}