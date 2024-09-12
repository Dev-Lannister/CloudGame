package com.wingtech.cloudserver.netty;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.wingtech.cloudserver.MainActivity;
import com.wingtech.cloudserver.listener.ClearUserDataObserver;
import com.wingtech.cloudserver.protocols.NetworkMessages;
import com.wingtech.cloudserver.service.CheckIsHomeService;
import com.wingtech.cloudserver.service.ScreenCastService;
import com.wingtech.gameserver.network.Session;
import com.wingtech.gameserver.network.handler.Handler;
import com.wingtech.gameserver.protocols.Messages;

/**
 * Created by lijiwei on 18-3-14.
 */

public class ClientHandler {

    private static final String TAG = "ClientHandler";

    public static boolean nomalExit = false;
    public static boolean isReady = false;
    public static String userName = null;

    private int port = 6006;
    long sendedLength = 0;
    long lastTime = System.currentTimeMillis();

    static MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
    static MotionEvent.PointerProperties property1 = new MotionEvent.PointerProperties();
    static MotionEvent.PointerProperties property2 = new MotionEvent.PointerProperties();
    static MotionEvent.PointerCoords[] pointerCoordses = new MotionEvent.PointerCoords[2];
    static MotionEvent.PointerCoords pointerCoord1 = new MotionEvent.PointerCoords();
    static MotionEvent.PointerCoords pointerCoord2 = new MotionEvent.PointerCoords();

    static MotionEvent.PointerProperties[] oneTouchProperties = new MotionEvent.PointerProperties[2];
    static MotionEvent.PointerProperties oneTouchProperty1 = new MotionEvent.PointerProperties();
    static MotionEvent.PointerCoords[] oneTouchCoordses = new MotionEvent.PointerCoords[2];
    static MotionEvent.PointerCoords oneTouchCoord1 = new MotionEvent.PointerCoords();

    @Handler(reqType = NetworkMessages.P2PWebRTCMessage.class)
    public static void handleMessage(NetworkMessages.P2PWebRTCMessage req) {
        String flag = req.getMessage();
        if (flag.equals("1")) {
            MainActivity.mainActivity.startAudioRecord();
            MainActivity.mainActivity.startCaptureScreen();
        } else {
            MainActivity.mainActivity.stopAudioRecord();
            MainActivity.mainActivity.stopScreenCapture();
        }
    }

    @Handler(reqType = NetworkMessages.ClientDisplayMetrics.class)
    public static void handleMessage(NetworkMessages.ClientDisplayMetrics req) {
        Log.d(TAG, "handleMessage: clientWidthPixels=" + req.getWidthPixels());
        Log.d(TAG, "handleMessage: clientHeightPixels=" + req.getHeightPixels());
    }

    @Handler(reqType = NetworkMessages.ClientSetting.class)
    public static void handleClientSetting(NetworkMessages.ClientSetting req) {
        Log.d(TAG, "handleClientSetting: widthDpi=" + req.getWidthDpi());
        Log.d(TAG, "handleClientSetting: heightDpi=" + req.getHeightDpi());
        ScreenCastService.widthDpi = Integer.parseInt(String.valueOf(Long.valueOf(req.getWidthDpi())));
        ScreenCastService.heightDpi = Integer.parseInt(String.valueOf(Long.valueOf(req.getHeightDpi())));
        //MainActivity.mainActivity.stopScreenCapture();
        //MainActivity.mainActivity.setDpi();
    }

    @Handler(reqType = NetworkMessages.App_Setting.class)
    public static void handleApp_Setting(NetworkMessages.App_Setting req) {
        long bitRate = req.getBitRate();
        long dpi = req.getScreenDpi();
        long profile = req.getProfile();
        long level = req.getLevel();
        Log.d(TAG, "handleApp_Setting: profile=" + profile + ",level=" + level);
        android.os.Message message = android.os.Message.obtain();
        message.what = 4;
        Bundle bundle = new Bundle();
        bundle.putInt("dpi", Integer.valueOf(String.valueOf(dpi)));
        bundle.putInt("bitRate", Integer.valueOf(String.valueOf(bitRate)));
        bundle.putInt("profile", Integer.valueOf(String.valueOf(profile)));
        bundle.putInt("level", Integer.valueOf(String.valueOf(level)));
        message.setData(bundle);
        MainActivity.mainActivity.mHandler.sendMessage(message);
    }

    @Handler(reqType = NetworkMessages.UserOperation.class)
    public static void handleUserOperation(NetworkMessages.UserOperation resp) {
        final int actionType = (int) resp.getActionType();
        float rawX = resp.getRawX();
        float rawY = resp.getRawY();
        float x1 = resp.getSecondX();
        float y1 = resp.getSecondY();

        long now = SystemClock.uptimeMillis();
        int inputSource = InputDevice.SOURCE_TOUCHSCREEN;
        if (resp.getIsMultiTouch()) {
            switch (actionType) {
                case MotionEvent.ACTION_POINTER_2_DOWN:
                    injectMultiMotionEvent(inputSource, actionType, now, rawX, rawY, x1, y1, 1.0f);
                    break;
                case MotionEvent.ACTION_POINTER_2_UP:
                    injectMultiMotionEvent(inputSource, actionType, now, rawX, rawY, x1, y1, 0.0f);
                    break;
                case MotionEvent.ACTION_POINTER_1_UP:
                    injectMultiMotionEvent(inputSource, actionType, now, rawX, rawY, x1, y1, 0.0f);
                    break;
                case MotionEvent.ACTION_MOVE:
                    injectMultiMotionEvent(inputSource, actionType, now, rawX, rawY, x1, y1, 1.0f);
                    break;
            }
        } else {
            int pointerId = Integer.valueOf(String.valueOf(resp.getPointerId()));
            switch (actionType) {
                case MotionEvent.ACTION_DOWN:
                    injectMotionEvent(inputSource, MotionEvent.ACTION_DOWN, pointerId, now, rawX, rawY, 1.0f);
                    break;
                case MotionEvent.ACTION_UP:
                    injectMotionEvent(inputSource, MotionEvent.ACTION_UP, pointerId, now, rawX, rawY, 0.0f);
                    break;
                case MotionEvent.ACTION_MOVE:
                    injectMotionEvent(inputSource, MotionEvent.ACTION_MOVE, pointerId, now, rawX, rawY, 1.0f);
                    break;
            }
        }
    }

    @Handler(reqType = NetworkMessages.KeyEvent.class)
    public static void handleKeyEvent(NetworkMessages.KeyEvent req) {
        int keyCode = (int) req.getKeyCode();
        boolean isLongPress = req.getIsLongPress();
        Log.d(TAG, "handleKeyEvent keyCode=" + keyCode + " isLongPress=" + isLongPress);
        sendKeyEvent(InputDevice.SOURCE_KEYBOARD, keyCode, isLongPress);
    }

    @Handler(reqType = NetworkMessages.App_Login.class)
    public static void handleApp_Login(NetworkMessages.App_Login req) {
        userName = req.getUserName();
        Log.d(TAG, "App_Login: userName=" + userName);
    }

    @Handler(reqType = NetworkMessages.App_GameLauncherReq.class)
    public static void handleApp_GameLauncherReq(NetworkMessages.App_GameLauncherReq req, Session session) {
        session.setAttribute("isAdmin", false);
        String pkgName = req.getPackageName();
        String className = req.getMainActivity();
        NettyServer.currentPkgName = pkgName;
        NettyServer.currentClsName = className;
        userName = req.getUserName();
        Log.d(TAG, "handleApp_GameLauncherReq: pkgName=" + pkgName);
        Log.d(TAG, "handleApp_GameLauncherReq: clsName=" + className);
        Log.d(TAG, "handleApp_GameLauncherReq: userName=" + userName);

        /*while (!isReady) {
            Log.d(TAG, "handleApp_GameLauncherReq: readying");
        }*/  //lijiwei.modify 20180502
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        ComponentName componentName = new ComponentName(pkgName, className);
        intent.setComponent(componentName);
        MainActivity.mainActivity.startActivity(intent);

        Messages.Game_AppConnected.Builder builder = Messages.Game_AppConnected.newBuilder();
        MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
        Log.d(TAG, "Game_AppConnected: ");

        android.os.Message message = android.os.Message.obtain();
        message.what = 3;
        message.obj = pkgName;
        MainActivity.mainActivity.mHandler.sendMessageDelayed(message, 8000);
    }

    @Handler(reqType = NetworkMessages.ClientExitGame.class)
    public static void handleClientExitGame(NetworkMessages.ClientExitGame req, Session session) {
        nomalExit = true;
        ClientHandler.isReady = false;
        if (session.getAttribute("isAdmin") != null) {
            //lijiwei.modify 20180421
            Messages.Game_AppDisconnected.Builder builder = Messages.Game_AppDisconnected.newBuilder();
            MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
            Log.d(TAG, "handleClientExitGame Game_AppDisconnected: ");
            Log.d(TAG, "handleClientExitGame: killPkg=" + NettyServer.currentPkgName);
            if (NettyServer.currentPkgName != null) {
                ActivityManager am = (ActivityManager) MainActivity.mainActivity.getSystemService(Context.ACTIVITY_SERVICE);
                am.forceStopPackage(NettyServer.currentPkgName);
            }
        }
        if (MainActivity.mainActivity.messageServer.sessions.size() <= 1) {
            MainActivity.mainActivity.videoServer.setConfigData(null, 0, 0);
            ScreenCastService.isFirstFrame = true;
            Log.d(TAG, "stopCheckIsHomeService: ");
            Intent checkIsHomeIntent = new Intent(MainActivity.mainActivity, CheckIsHomeService.class);
            MainActivity.mainActivity.stopService(checkIsHomeIntent);
            CheckIsHomeService.isNotHome = false;
            Log.d(TAG, "handleClientExitGame: mainActivity.stopAll()");
            MainActivity.mainActivity.stopAll();
            if (MainActivity.mainActivity.wakeLock.isHeld()) {
                MainActivity.mainActivity.wakeLock.release();
            }
        }

        //先做备份操作，然后清除缓存 lijiwei.modify 20180502
        //MainActivity.mainActivity.openAndWriteBakSocket(NettyServer.currentPkgName, 0);
    }

    private static int getInputDeviceId(int inputSource) {
        final int DEFAULT_DEVICE_ID = 0;
        int[] devIds = InputDevice.getDeviceIds();
        for (int devId : devIds) {
            InputDevice inputDev = InputDevice.getDevice(devId);
            if (inputDev.supportsSource(inputSource)) {
                return devId;
            }
        }
        return DEFAULT_DEVICE_ID;
    }

    public static byte[] long2Bytes(long num) {
        byte[] byteNum = new byte[8];
        for (int ix = 0; ix < 8; ++ix) {
            int offset = 64 - (ix + 1) * 8;
            byteNum[ix] = (byte) ((num >> offset) & 0xff);
        }
        return byteNum;
    }

    /**
     * Builds a MotionEvent and injects it into the event stream.
     *
     * @param inputSource the InputDevice.SOURCE_* sending the input event
     * @param action      the MotionEvent.ACTION_* for the event
     * @param when        the value of SystemClock.uptimeMillis() at which the event happened
     * @param x           x coordinate of event
     * @param y           y coordinate of event
     * @param pressure    pressure of event
     */
    private static void injectMotionEvent(int inputSource, int action, int pointerId, long when, float x, float y, float pressure) {
        final float DEFAULT_SIZE = 1.0f;
        final int DEFAULT_META_STATE = 0;
        final float DEFAULT_PRECISION_X = 1.0f;
        final float DEFAULT_PRECISION_Y = 1.0f;
        final int DEFAULT_EDGE_FLAGS = 0;

        oneTouchCoord1.clear();
        oneTouchProperty1.clear();

        oneTouchCoord1.x = x;
        oneTouchCoord1.y = y;
        oneTouchCoord1.pressure = pressure;
        oneTouchCoord1.size = DEFAULT_SIZE;
        oneTouchCoordses[0] = oneTouchCoord1;

        oneTouchProperty1.id = pointerId;
        property1.toolType = MotionEvent.TOOL_TYPE_FINGER;
        oneTouchProperties[0] = oneTouchProperty1;

        MotionEvent event = MotionEvent.obtain(when, when, action, 1, oneTouchProperties, oneTouchCoordses,
                0, 0, 1, 1, 0, 0, 0, 0);
        event.setSource(inputSource);
        boolean injectResult = InputManager.getInstance().injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private static void injectMultiMotionEvent(int inputSource, int action, long when, float x0,
                                               float y0, float x1, float y1, float pressure) {
        final float DEFAULT_SIZE = 1.0f;

        pointerCoord1.clear();
        pointerCoord2.clear();
        property1.clear();
        property2.clear();

        pointerCoord1.x = x0;
        pointerCoord1.y = y0;
        pointerCoord1.pressure = pressure;
        pointerCoord1.size = DEFAULT_SIZE;
        pointerCoordses[0] = pointerCoord1;

        pointerCoord2.x = x1;
        pointerCoord2.y = y1;
        pointerCoord2.pressure = pressure;
        pointerCoord2.size = DEFAULT_SIZE;
        pointerCoordses[1] = pointerCoord2;

        property1.id = 0;
        property1.toolType = MotionEvent.TOOL_TYPE_FINGER;
        properties[0] = property1;

        property2.id = 1;
        property2.toolType = MotionEvent.TOOL_TYPE_FINGER;
        properties[1] = property2;

        MotionEvent event = MotionEvent.obtain(when, when, action, 2, properties, pointerCoordses,
                0, 0, 1, 1, 0, 0, 0, 0);
        event.setSource(inputSource);
        boolean injectResult = InputManager.getInstance().injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private static void injectKeyEvent(KeyEvent event) {
        InputManager.getInstance().injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }

    private static void sendKeyEvent(int inputSource, int keyCode, boolean longpress) {
        long now = SystemClock.uptimeMillis();
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
        if (longpress) {
            injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_LONG_PRESS,
                    inputSource));
        }
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
    }

}
