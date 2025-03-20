package com.leven.uni.call;

public interface UniJSCallback {
    void invoke(Object data);

    void invokeAndKeepAlive(Object data);
}
