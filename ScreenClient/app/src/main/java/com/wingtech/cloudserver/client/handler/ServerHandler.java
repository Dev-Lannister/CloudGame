package com.wingtech.cloudserver.client.handler;

import android.util.Log;

import com.wingtech.cloudserver.client.MainActivity;
import com.wingtech.cloudserver.client.customview.GameUI;
import com.wingtech.cloudserver.client.protocols.NetworkMessages;

/**
 * Created by lijiwei on 2018/3/14.
 */

public class ServerHandler {

    private static final String TAG = "ServerHandler";

    @com.wingtech.gameserver.network.handler.Handler(reqType = NetworkMessages.serverLandscapeOrPortrait.class)
    public static void handleserverLandscapeOrPortrait (NetworkMessages.serverLandscapeOrPortrait resp){
        android.os.Message m = android.os.Message.obtain();
        if (resp.getIsLandscape()) {
            m.what = 1;
        } else {
            m.what = 0;
        }
        MainActivity.mHandler.sendMessage(m);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = NetworkMessages.AppQuitNotification.class)
    public static void handleAppQuitNotification (NetworkMessages.AppQuitNotification resp){
        Log.d(TAG, "decodeMessage: AppQuitNotification");
        android.os.Message m = android.os.Message.obtain();
        m.what = 4;
        MainActivity.mHandler.sendMessage(m);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = NetworkMessages.ServerCurrentTimestamp.class)
    public static void handleServerCurrentTimestamp (NetworkMessages.ServerCurrentTimestamp resp){
        long serverTime = resp.getTimeStamp();
        long clientTime = System.currentTimeMillis();
        Log.d(TAG, "decodeMessage: clientTime = " + clientTime);
        Log.d(TAG, "decodeMessage: serverTime = " + serverTime);
    }

    @com.wingtech.gameserver.network.handler.Handler(reqType = NetworkMessages.ClientDisplayMetrics.class)
    public static void handleClientDisplayMetrics (NetworkMessages.ClientDisplayMetrics resp){
        long remoteWidth = resp.getWidthPixels();
        long remoteHeight = resp.getHeightPixels();
        GameUI.remoteWidth = Integer.valueOf(String.valueOf(remoteWidth));
        GameUI.remoteHeight = Integer.valueOf(String.valueOf(remoteHeight));
        Log.d(TAG, "decodeMessage: remoteWidth=" + GameUI.remoteWidth);
        Log.d(TAG, "decodeMessage: remoteHeight=" + GameUI.remoteHeight);
    }
}
