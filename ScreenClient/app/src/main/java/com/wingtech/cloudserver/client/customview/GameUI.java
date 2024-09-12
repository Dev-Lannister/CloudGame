package com.wingtech.cloudserver.client.customview;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.wingtech.cloudserver.client.MainActivity;
import com.wingtech.cloudserver.client.protocols.NetworkMessages;

import java.io.IOException;

/**
 * Created by lijiwei on 2017/12/27.
 */

public class GameUI extends SurfaceView implements SurfaceHolder.Callback, View.OnTouchListener {

    private static final String TAG = "GameUI";

    private SurfaceHolder holder;
    public static MediaCodec mediaCodec;
    private RenderThread renderThread;
    private boolean isDrawing = false;
    private Surface surface;

    static GameUI instance;

    public static boolean codecIsCreated = false;

    private boolean flag = false;

    public static int remoteWidth;
    public static int remoteHeight;
    public static int localWidth;
    public static int localHeight;

    public GameUI(Context context) {
        super(context);
        Log.d(TAG, "GameUI: ");
        this.holder = getHolder();
        holder.addCallback(this);
        setClickable(true);
        setOnTouchListener(this);
        renderThread = new RenderThread();
        instance = this;
        flag = true;
    }

    public GameUI(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "GameUI: ");
        this.holder = getHolder();
        holder.addCallback(this);
        setClickable(true);
        setOnTouchListener(this);
        renderThread = new RenderThread();
        instance = this;
        flag = true;
    }

    public GameUI(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Log.d(TAG, "GameUI: ");
        this.holder = getHolder();
        holder.addCallback(this);
        setClickable(true);
        setOnTouchListener(this);
        renderThread = new RenderThread();
        instance = this;
        flag = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated: ");
        if (flag){
            isDrawing = true;
            renderThread.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d(TAG, "surfaceChanged: ");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed: ");
        try {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
                surface.release();
                surface = null;
                codecIsCreated = false;
                isDrawing = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getPointerCount() > 1) {
            NetworkMessages.UserOperation.Builder builder = NetworkMessages.UserOperation.newBuilder();
            builder.setActionType(motionEvent.getAction());
            builder.setRawX((motionEvent.getX(0)/localWidth)*remoteWidth);
            builder.setRawY((motionEvent.getY(0)/localHeight)*remoteHeight);
            builder.setIsMultiTouch(true);
            builder.setSecondX((motionEvent.getX(1)/localWidth)*remoteWidth);
            builder.setSecondY((motionEvent.getY(1)/localHeight)*remoteHeight);
            builder.setPointerId(motionEvent.getPointerId(motionEvent.getActionIndex()));
            builder.setTimeStamp(System.currentTimeMillis());
            MainActivity.messageClient.sendMessage(builder.build());
        } else {
            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                    NetworkMessages.UserOperation.Builder builder = NetworkMessages.UserOperation.newBuilder();
                    builder.setActionType(MotionEvent.ACTION_UP);
                    builder.setRawX((motionEvent.getX(0)/localWidth)*remoteWidth);
                    builder.setRawY((motionEvent.getY(0)/localHeight)*remoteHeight);
                    builder.setIsMultiTouch(false);
                    builder.setTimeStamp(System.currentTimeMillis());
                    builder.setPointerId(motionEvent.getPointerId(motionEvent.getActionIndex()));
                    Log.d(TAG, "ACTION_UP: pointerId=" + motionEvent.getPointerId(motionEvent.getActionIndex()));
                    MainActivity.messageClient.sendMessage(builder.build());
                    break;
                case MotionEvent.ACTION_DOWN:
                    NetworkMessages.UserOperation.Builder b2 = NetworkMessages.UserOperation.newBuilder();
                    b2.setActionType(MotionEvent.ACTION_DOWN);
                    b2.setRawX((motionEvent.getX(0)/localWidth)*remoteWidth);
                    b2.setRawY((motionEvent.getY(0)/localHeight)*remoteHeight);
                    b2.setIsMultiTouch(false);
                    b2.setPointerId(motionEvent.getPointerId(motionEvent.getActionIndex()));
                    b2.setTimeStamp(System.currentTimeMillis());
                    MainActivity.messageClient.sendMessage(b2.build());
                    break;
                case MotionEvent.ACTION_MOVE:
                    NetworkMessages.UserOperation.Builder b3 = NetworkMessages.UserOperation.newBuilder();
                    b3.setActionType(MotionEvent.ACTION_MOVE);
                    b3.setRawX((motionEvent.getX(0)/localWidth)*remoteWidth);
                    b3.setRawY((motionEvent.getY(0)/localHeight)*remoteHeight);
                    b3.setIsMultiTouch(false);
                    b3.setPointerId(motionEvent.getPointerId(motionEvent.getActionIndex()));
                    b3.setTimeStamp(System.currentTimeMillis());
                    MainActivity.messageClient.sendMessage(b3.build());
                    break;
            }
        }

        return true;
    }

    public class RenderThread extends Thread {
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: 初始化解码器");
                surface = holder.getSurface();
                mediaCodec = MediaCodec.createDecoderByType("Video/AVC");
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", getWidth(), getHeight());
                Log.d(TAG, "run: 解码器宽高：" + getWidth() + "*" + getHeight());
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4096000);
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                if (surface.isValid()) {
                    mediaCodec.configure(mediaFormat, surface, null, 0);
                    mediaCodec.start();
                    codecIsCreated = true;
                }
            } catch (IOException e) {
                Log.d(TAG, "初始化解码器失败", e);
                e.printStackTrace();
            }
            super.run();
        }
    }

    public static void handleserverLandscapeOrPortrait(NetworkMessages.serverLandscapeOrPortrait resp) {
        Log.d(TAG, "regist handleserverLandscapeOrPortrait: for put map");
    }

    public static void handleServerCurrentTimestamp(NetworkMessages.ServerCurrentTimestamp resp) {
        Log.d(TAG, "regist handleServerCurrentTimestamp: for put map");
    }

    public static void handleClientDisplayMetrics(NetworkMessages.ClientDisplayMetrics resp) {
        Log.d(TAG, "regist handleClientDisplayMetrics: for put map");
    }
}
