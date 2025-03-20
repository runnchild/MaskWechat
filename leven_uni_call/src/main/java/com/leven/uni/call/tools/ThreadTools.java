package com.leven.uni.call.tools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadTools {
    private static ThreadTools instance;
    private static ExecutorService executor;
    public static synchronized ThreadTools getInstance(){
        if(instance == null){
            instance = new ThreadTools();
        }
        return instance;
    }

    /**
     * 获取ExecutorService
     */
    private ExecutorService getExecutor(){
        if(executor == null){
            executor = new ThreadPoolExecutor(1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    r -> {
                        Thread t = new Thread(r);
                        t.setName("activity-sub-thread-" + t.getId());
                        return t;
                    });
        }

        return executor;
    }

    /**
     * 执行线程
     */
    public void runOnSubThread(Runnable runnable){
        getExecutor().execute(runnable);
    }
}
