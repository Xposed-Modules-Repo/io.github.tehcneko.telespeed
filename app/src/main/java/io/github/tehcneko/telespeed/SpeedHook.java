package io.github.tehcneko.telespeed;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.res.XModuleResources;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SpeedHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private final static String TAG = "SpeedHook";
    private final static String KEY_SPEED = "speed";
    public final static String BOOST_NONE = "none";
    public final static String BOOST_AVERAGE = "average";
    public final static String BOOST_EXTREME = "extreme";
    private final static int TYPE_ERROR_SUBTITLE = 4;
    private final static long DEFAULT_MAX_FILE_SIZE = 1024L * 1024L * 2000L;
    private XSharedPreferences prefs;
    private XModuleResources res;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences(BuildConfig.APPLICATION_ID, "conf");
        res = XModuleResources.createInstance(startupParam.modulePath, null);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (BuildConfig.APPLICATION_ID.equals(loadPackageParam.packageName)) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod("org.telegram.messenger.FileLoadOperation", loadPackageParam.classLoader, "updateParams", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    int downloadChunkSizeBig;
                    int maxDownloadRequests;
                    int maxDownloadRequestsBig;
                    var speed = prefs.getString(KEY_SPEED, BOOST_AVERAGE);
                    if (BOOST_AVERAGE.equals(speed)) {
                        downloadChunkSizeBig = 1024 * 512;
                        maxDownloadRequests = 8;
                        maxDownloadRequestsBig = 8;
                    } else if (BOOST_EXTREME.equals(speed)) {
                        downloadChunkSizeBig = 1024 * 1024;
                        maxDownloadRequests = 12;
                        maxDownloadRequestsBig = 12;
                    } else {
                        downloadChunkSizeBig = 1024 * 128;
                        maxDownloadRequests = 4;
                        maxDownloadRequestsBig = 4;
                    }
                    var maxCdnParts = (int) (DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);
                    XposedHelpers.setIntField(param.thisObject, "downloadChunkSizeBig", downloadChunkSizeBig);
                    XposedHelpers.setObjectField(param.thisObject, "maxDownloadRequests", maxDownloadRequests);
                    XposedHelpers.setObjectField(param.thisObject, "maxDownloadRequestsBig", maxDownloadRequestsBig);
                    XposedHelpers.setObjectField(param.thisObject, "maxCdnParts", maxCdnParts);

                    if (!BOOST_NONE.equals(speed)) {
                        try {
                            var fileSize = XposedHelpers.getLongField(param.thisObject, "totalBytesCount");
                            if (fileSize >= 1.2 * 1024 * 1024) {
                                String speedString;
                                if (BOOST_AVERAGE.equals(speed)) {
                                    speedString = res.getString(R.string.boost_average);
                                } else if (BOOST_EXTREME.equals(speed)) {
                                    speedString = res.getString(R.string.boost_extreme);
                                } else {
                                    speedString = res.getString(R.string.boost_none);
                                }
                                var title = res.getString(R.string.speed_toast);
                                var subtitle = res.getString(R.string.speed_toast_level, "Nekogram", speedString);
                                try {
                                    var notificationCenterClass = XposedHelpers.findClass("org.telegram.messenger.NotificationCenter", loadPackageParam.classLoader);
                                    var globalInstance = XposedHelpers.callStaticMethod(notificationCenterClass, "getGlobalInstance");
                                    new Handler(Looper.getMainLooper()).post(() -> XposedHelpers.callMethod(
                                            globalInstance,
                                            "postNotificationName",
                                            XposedHelpers.getStaticIntField(notificationCenterClass, "showBulletin"),
                                            new Object[]{
                                                    TYPE_ERROR_SUBTITLE,
                                                    title,
                                                    subtitle}));
                                } catch (Throwable t) {
                                    XposedBridge.log(t);
                                    Toast.makeText(AndroidAppHelper.currentApplication(), title + "\n" + subtitle, Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Toast.makeText((Activity) param.thisObject, "TeleSpeed: " + res.getString(R.string.unsupported), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
