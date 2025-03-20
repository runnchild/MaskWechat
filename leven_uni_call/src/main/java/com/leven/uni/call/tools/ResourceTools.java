package com.leven.uni.call.tools;

import android.content.Context;

public class ResourceTools {
    public static int isMipmapResourceExists(Context context, String resourceName) {
        return context.getResources().getIdentifier(resourceName, "mipmap", context.getPackageName());
    }

    public static int isDrawableResourceExists(Context context, String resourceName) {
        return context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
    }
}
