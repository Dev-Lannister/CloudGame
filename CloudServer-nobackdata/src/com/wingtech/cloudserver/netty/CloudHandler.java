package com.wingtech.cloudserver.netty;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.wingtech.cloudserver.MainActivity;
import com.wingtech.cloudserver.util.ZipUtil;
import com.wingtech.gameserver.network.handler.Handler;
import com.wingtech.gameserver.protocols.Messages;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lijiwei on 18-2-28.
 */

public class CloudHandler {

    private static final String TAG = "CloudHandler";

    private static List<String> testGameList = new ArrayList<>();

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_InstallAPK.class)
    public static void handleGame_InstallAPKReq(Messages.Game_InstallAPK req) {
        try {
            Messages.Game_InstallAPKRecevieResp.Builder builder = Messages.Game_InstallAPKRecevieResp.newBuilder();
            MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }


        String fileName = req.getFileName();
        long totalLength = req.getTotalLength();
        final int offset = Integer.valueOf(String.valueOf(req.getOffset()));
        final byte[] bytes = req.getData().toByteArray();
        final long moreData = req.getMoreData();

        final String apkPath = Environment.getExternalStorageDirectory().getPath() + File.separator + fileName;
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(apkPath, "rw");
            file.seek(offset);
            file.write(bytes, 0, bytes.length);
            Log.d(TAG, "handleGame_InstallAPKReq: receiving offset=" + offset + " totalLen=" + totalLength);
            if (moreData == 0) {
                Log.d(TAG, "apk receive complete");
                android.os.Message message = android.os.Message.obtain();
                message.what = 1;
                Bundle bundle = new Bundle();
                bundle.putString("apkPath", apkPath);
                bundle.putString("fileName", fileName);
                message.setData(bundle);
                MainActivity.mainActivity.mHandler.sendMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_UninstallApk.class)
    public static void handleGame_UninstallApk(Messages.Game_UninstallApk resp) {
        String packageName = resp.getPackageName();
        Log.d(TAG, "handleGame_UninstallApk: packageName=" + packageName);
        android.os.Message message = android.os.Message.obtain();
        message.what = 5;
        message.obj = packageName;
        MainActivity.mainActivity.mHandler.sendMessage(message);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.GetIconReq.class)
    public static void handleGetIconReq(Messages.GetIconReq req) {
        String pkgName = req.getPackageName();
        Log.d(TAG, "handleGetIconReq: packageName=" + pkgName);
        android.os.Message message = android.os.Message.obtain();
        message.what = 6;
        message.obj = pkgName;
        MainActivity.mainActivity.mHandler.sendMessage(message);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_StartTestApk.class)
    public static void handleGame_StartTestApk(Messages.Game_StartTestApk req) {
        String pkgName = req.getPackageName();
        String className = req.getMainActivity();
        Log.d(TAG, "handleGame_StartTestApk: pkgName=" + pkgName);
        Log.d(TAG, "handleGame_StartTestApk: mainActivity=" + className);
        testGameList.add(pkgName);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        ComponentName componentName = new ComponentName(pkgName, className);
        intent.setComponent(componentName);
        MainActivity.mainActivity.startActivity(intent);
        android.os.Message message = android.os.Message.obtain();
        message.what = 7;
        MainActivity.mainActivity.mHandler.sendMessage(message);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_StopTest.class)
    public static void handleGame_StopTest(Messages.Game_StopTest req) {
        for (String pkgName : testGameList) {
            try {
                ActivityManager am = (ActivityManager) MainActivity.mainActivity.getSystemService(Context.ACTIVITY_SERVICE);
                am.forceStopPackage(pkgName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        testGameList.clear();
        android.os.Message message = android.os.Message.obtain();
        message.what = 7;
        MainActivity.mainActivity.mHandler.sendMessage(message);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_Download_AppData.class)
    public static void handleGame_Download_AppData(Messages.Game_Download_AppData req) {
        /*long totalLength = req.getTotalLength();
        if (totalLength == -1) {
            Log.d(TAG, "handleGame_Download_AppData: cloud server not exists user data");
            ClientHandler.isReady = true;
        } else {
            final int offset = Integer.valueOf(String.valueOf(req.getOffset()));
            final byte[] bytes = req.getData().toByteArray();
            final long moreData = req.getMoreData();
            final String dataPath = Environment.getExternalStorageDirectory().getPath() +
                    File.separator + "gamedata" + File.separator + req.getPackageName() + ".zip";
            RandomAccessFile file = null;
            try {
                file = new RandomAccessFile(dataPath, "rw");
                file.seek(offset);
                file.write(bytes, 0, bytes.length);
                Log.d(TAG, "handleGame_InstallAPKReq: receiving offset=" + offset + " totalLen=" + totalLength);
                if (moreData == 0) {
                    Log.d(TAG, "handleGame_Download_AppData: receive backup complete,start copy to data/data");
                    SystemProperties.set("persist.sys.copy_service", 1 + "");
                    ZipUtil.unzip(dataPath, Environment.getExternalStorageDirectory().getPath() +
                            File.separator + "gamedata" + File.separator + req.getPackageName());
                    file.close();
                    File f = new File(dataPath);
                    ZipUtil.delete(f.getPath());
                    MainActivity.mainActivity.openAndWriteBakSocket(req.getPackageName(), 1);
                }

                Messages.Game_Download_AppDataResp.Builder builder = Messages.Game_Download_AppDataResp.newBuilder();
                builder.setUserName(ClientHandler.userName);
                builder.setPackageName(req.getPackageName());
                builder.setOffset(req.getOffset());
                MainActivity.mainActivity.nettyClient.sendMessage(builder.build());

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (file != null) {
                        file.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }*/ //lijiwei.modify 20180502
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = Messages.Game_Upload_AppDataResp.class)
    public static void handleGame_Upload_AppDataResp(Messages.Game_Upload_AppDataResp req) {
        String pkgName = req.getPackageName();
        int offset = Integer.valueOf(String.valueOf(req.getOffset()));
        UploadFile.receiveResp(pkgName);
    }
}
