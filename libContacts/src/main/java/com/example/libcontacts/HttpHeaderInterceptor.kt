package com.example.libcontacts

import okhttp3.Interceptor
import okhttp3.Response

class HttpHeaderInterceptor : Interceptor {

    private var wxId: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        builder.addHeader("wxId", wxId)
        return chain.proceed(builder.build())
    }

    fun setWxId(wxId: String) {
        this.wxId = wxId
    }
}
