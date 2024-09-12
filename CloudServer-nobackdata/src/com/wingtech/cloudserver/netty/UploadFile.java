package com.wingtech.cloudserver.netty;

import android.os.Environment;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.wingtech.cloudserver.MainActivity;
import com.wingtech.gameserver.protocols.Messages;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Created by lijiwei on 18-4-10.
 */

public class UploadFile {

    private static final String TAG = "UploadFile";

    public static int offset = 0;
    public static int sendCount = 0;
    public static boolean finished = false;

    public static synchronized void sendData(String pkgName) {
        if (isFinished()) {
            return;
        }
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(Environment.getExternalStorageDirectory().getPath()
                    + File.separator + "gamedata" + File.separator + pkgName + ".zip", "rw");
            file.seek(offset);
            int size = 512*1024;
            if ((offset + 512*1024) > file.length()) {
                size = Integer.valueOf(String.valueOf(file.length())) - offset;
            }
            Log.d(TAG, "sendData: size=" + size);
            byte[] bytes = new byte[size];
            file.read(bytes, 0, size);
            Messages.Game_Upload_AppData.Builder builder = Messages.Game_Upload_AppData.newBuilder();
            builder.setOffset(offset);
            offset += size;
            builder.setPackageName(pkgName);
            builder.setTotalLength(file.length());
            builder.setData(ByteString.copyFrom(bytes));
            builder.setMoreData(offset >= file.length() ? 0 : 1);
            if (offset >= file.length()) {
                finished = true;
                sendCount = 0;
                offset = 0;
                builder.setMoreData(0);
                android.os.Message message = android.os.Message.obtain();
                message.what = 8;
                message.obj = NettyServer.currentPkgName;
                MainActivity.mainActivity.mHandler.sendMessage(message);
            } else {
                builder.setMoreData(1);
            }
            builder.setUserName(ClientHandler.userName);
            Log.d(TAG, "sendData: offset=" + builder.getOffset());
            MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
            //sendCount++;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static synchronized void uploadData(String pkgName) {
        //while (!UploadFile.finished && UploadFile.sendCount <= 3) {
            sendData(pkgName);
        //}
        //finished = false;
    }

    public static synchronized void receiveResp(String pkgName) {
        //sendCount--;
        //while (!UploadFile.finished && UploadFile.sendCount <= 3) {
            sendData(pkgName);
        //}
        //finished = false;
    }

    public static boolean isFinished() {
        return finished;
    }
}
