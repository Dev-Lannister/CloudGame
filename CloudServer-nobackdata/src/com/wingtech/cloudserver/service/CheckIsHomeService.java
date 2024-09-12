package com.wingtech.cloudserver.service;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.util.Log;

import com.wingtech.cloudserver.MainActivity;
import com.wingtech.cloudserver.protocols.NetworkMessages;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lijiwei on 18-1-17.
 */

public class CheckIsHomeService extends Service {

    private static final String TAG = "CheckIsHomeService";

    public static boolean isNotHome = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*final String pkgName = intent.getStringExtra("pkgName");
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> names = new ArrayList<>();
                names.add(pkgName);
                Log.d(TAG, "run: names=" +names);
                ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                try {
                    Thread.sleep(4000);
                }catch (Exception e) {
                    e.printStackTrace();
                }
                while (true){
                    if (isNotHome){
                        List<ActivityManager.RunningTaskInfo> runningTaskInfos =
                                activityManager.getRunningTasks(1);
                        if (!names.contains(runningTaskInfos.get(0).topActivity.getPackageName())){
                            Log.d(TAG, "run: runningService is " + runningTaskInfos.get(0).topActivity.getPackageName());
                            NetworkMessages.AppQuitNotification.Builder builder =
                                    NetworkMessages.AppQuitNotification.newBuilder();
                            MainActivity.mainActivity.videoNettyServer.sendMessage(builder.build(),"AppQuitNotification");
                            isNotHome = false;
                            Log.d(TAG, "run: sendMessageAppQuitNotification");
                            break;
                        }
                    }

                }

            }
        }).start();*/
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> names = new ArrayList<>();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                List<ResolveInfo> apklList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : apklList) {
                    String packageStr = resolveInfo.activityInfo.packageName;
                    names.add(packageStr);
                }
                names.add("com.wingtech.cloudserver");
                Log.d(TAG, "run: names=" +names);
                ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                while (true){
                    try {
                        Thread.sleep(300);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (isNotHome){
                        List<ActivityManager.RunningTaskInfo> runningTaskInfos =
                                activityManager.getRunningTasks(1);
                        if (names.contains(runningTaskInfos.get(0).topActivity.getPackageName())){
                            NetworkMessages.AppQuitNotification.Builder builder =
                                    NetworkMessages.AppQuitNotification.newBuilder();
                            try {
                                MainActivity.mainActivity.messageServer.sendMessage(builder.build());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            isNotHome = false;
                            Log.d(TAG, "run: sendMessageAppQuitNotification");
                            break;
                        }
                    }

                }

            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }
}
