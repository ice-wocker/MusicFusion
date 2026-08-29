package com.musicfusion.app;

import android.content.Context;
import android.content.pm.PackageManager;

/** AutoManifest — Android Auto 最小清单声明
 *  - 此类仅作文档/占位, 实际清单在 AndroidManifest.xml
 *  - 需在 manifest 中声明:
 *    <meta-data android:name="com.google.android.gms.car.application"
 *        android:resource="@xml/automotive_app_desc" />
 *  - res/xml/automotive_app_desc.xml:
 *    <automotiveApp><uses name="media"/></automotiveApp>
 *  - MediaBrowserServiceCompat 实现 (需 androidx, 此处仅声明接口) */
public final class AutoManifest {
    /** 检查设备是否支持 Android Auto */
    public static boolean isAutoSupported(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        return pm.hasSystemFeature("android.hardware.type.automotive") ||
               pm.hasSystemFeature("android.software.automotive_templates_host");
    }

    /** 获取 Android Auto 相关权限/特性要求字符串 (用于设置页显示) */
    public static String getAutoRequirements() {
        return "需安装 Android Auto 应用, 车机支持 Android Auto 投屏, " +
               "应用需声明 media 浏览服务 (MediaBrowserServiceCompat)";
    }
}