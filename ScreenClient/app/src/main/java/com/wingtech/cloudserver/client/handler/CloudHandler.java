package com.wingtech.cloudserver.client.handler;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.wingtech.cloudserver.client.LeadActivity;
import com.wingtech.gameserver.network.handler.Handler;
import com.wingtech.gameserver.protocols.Messages;

import java.util.List;

/**
 * Created by lijiwei on 2018/1/17.
 */

public class CloudHandler {

    private static final String TAG = "CloudHandler";

    @Handler(reqType = Messages.App_ListGamesResp.class)
    public static void handleApp_ListGamesResp(Messages.App_ListGamesResp resp) {
        List<Messages.Game> gamesList = resp.getGamesList();
        Log.d(TAG, "handleApp_ListGamesResp: gamesize=" + gamesList.size());
        Message message = Message.obtain();
        message.what = 0;
        message.obj = gamesList;
        LeadActivity.instance.mHandler.sendMessage(message);
    }

    @Handler(reqType = Messages.App_playGameResp.class)
    public static void handleApp_playGameResp(Messages.App_playGameResp resp) {
        Log.d(TAG, "handleApp_playGameResp: ip=" + resp.getIp());
        Log.d(TAG, "handleApp_playGameResp: status=" + resp.getStatus().toString());
        Log.d(TAG, "handleApp_playGameResp: port=" + resp.getStartPort());
        byte[] image = resp.getImages().toByteArray();
        if (resp.getStatus().toString().equals("OK")) {
            Message message = Message.obtain();
            message.what = 1;
            Bundle bundle = new Bundle();
            bundle.putString("ip", resp.getIp());
            bundle.putInt("port", resp.getStartPort());
            bundle.putByteArray("image", image);
            bundle.putString("token",resp.getToken());
            bundle.putInt("useProxy",resp.getUseProxy());
            message.setData(bundle);
            LeadActivity.instance.mHandler.sendMessage(message);
        } else if (resp.getStatus().toString().equals("NO_RESOURCE")) {
            Message message = Message.obtain();
            message.what = 2;
            LeadActivity.instance.mHandler.sendMessage(message);
        }
    }

    @Handler(reqType = Messages.GetIconResp.class)
    public static void handleGetIconResp(Messages.GetIconResp resp) {
        Log.d(TAG, "handleGetIconResp: ");
        byte[] iconBytes = resp.getIcon().toByteArray();
        String pkgName = resp.getPackageName();
        Bundle bundle = new Bundle();
        bundle.putString("pkgName", pkgName);
        bundle.putByteArray("iconBytes", iconBytes);
        Message message = Message.obtain();
        message.what = 3;
        message.setData(bundle);
        LeadActivity.instance.mHandler.sendMessage(message);
    }
}
