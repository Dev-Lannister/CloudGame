package com.wingtech.cloudserver.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import com.wingtech.cloudserver.MainActivity;
import com.wingtech.cloudserver.consts.ActivityServiceMessage;
import com.wingtech.cloudserver.consts.ExtraIntent;
import com.wingtech.cloudserver.protocols.NetworkMessages;
import com.wingtech.cloudserver.writer.IvfWriter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public final class ScreenCastService extends Service {

    private static final int FPS = 30;
    private final String TAG = "ScreenCastService";

    private MediaProjectionManager mediaProjectionManager;
    private Handler handler;
    protected Messenger crossProcessMessenger;

    public MediaProjection mediaProjection;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec encoder;
    private IvfWriter ivf;
    private int mScreenWidth,mScreenHeight,mDpi,mBitrate,mResultCode,mProfile,mLevel;
    private String mFormat;
    private Intent mResultData;

    public static boolean isFirstFrame = true;
    public static byte[] firstFrame;

    public static int widthDpi = 1824,heightDpi = 1080;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message. what:" + msg.what);
                switch (msg.what) {
                    case ActivityServiceMessage.CONNECTED:
                    case ActivityServiceMessage.DISCONNECTED:
                        break;
                    case ActivityServiceMessage.STOP:
                        stopScreenCapture();
                        //stopSelf();
                        break;
                    case ActivityServiceMessage.DPISETTING:
                        Bundle bundle = msg.getData();
                        stopScreenCapture2();
                        mProfile = bundle.getInt("profile");
                        mLevel = bundle.getInt("level");
                        startScreenCapture(mResultCode, mResultData, mFormat, bundle.getInt("widthDpi"),
                                bundle.getInt("heightDpi"), mDpi, bundle.getInt("bitRate"));
                        break;
                }
                return false;
            }
        });
        crossProcessMessenger = new Messenger(handler);
        return crossProcessMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        stopScreenCapture();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final int resultCode = intent.getIntExtra(ExtraIntent.RESULT_CODE.toString(), -1);
        final Intent resultData = intent.getParcelableExtra(ExtraIntent.RESULT_DATA.toString());

        if (resultCode == 0 || resultData == null) {
            return START_NOT_STICKY;
        }

        final String format = mFormat = intent.getStringExtra(ExtraIntent.VIDEO_FORMAT.toString());
        final int screenWidth = mScreenWidth = intent.getIntExtra(ExtraIntent.SCREEN_WIDTH.toString(), 640);
        final int screenHeight = mScreenHeight = intent.getIntExtra(ExtraIntent.SCREEN_HEIGHT.toString(), 360);
        final int screenDpi = mDpi = intent.getIntExtra(ExtraIntent.SCREEN_DPI.toString(), 96);
        final int bitrate = mBitrate = intent.getIntExtra(ExtraIntent.VIDEO_BITRATE.toString(), 1024000);

        Log.i(TAG, "Start casting with format:" + format + ", screen:" + screenWidth + "x" + screenHeight + " @ " + screenDpi + " bitrate:" + bitrate);

        mResultCode = resultCode;
        mResultData = resultData;
        startScreenCapture(resultCode, resultData, format, screenWidth, screenHeight, screenDpi, bitrate);

        return START_STICKY;
    }

    private void startScreenCapture(int resultCode, Intent resultData, String format, int width, int height, int dpi, int bitrate) {
        if (this.mediaProjection == null) {
            this.mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
        }

        Log.d(TAG, "startRecording... this.mediaProjection="+this.mediaProjection);

        this.videoBufferInfo = new MediaCodec.BufferInfo();
        Log.d(TAG, "startScreenCapture: width=" + width);
        Log.d(TAG, "startScreenCapture: height=" + height);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(format, width, height);

        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 0);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        /*if (mProfile != 0) {
            mediaFormat.setInteger(MediaFormat.KEY_PROFILE,mProfile);
            mediaFormat.setInteger("level",mLevel);
        }*/

        try {

            switch (format) {
                case MediaFormat.MIMETYPE_VIDEO_AVC:
                    // AVC
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

                    this.encoder = MediaCodec.createEncoderByType(format);
                    this.encoder.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec codec, int inputBufferId) {
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {

                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            if (info.size > 0 && outputBuffer != null) {
                                outputBuffer.position(info.offset);
                                outputBuffer.limit(info.offset + info.size);
                                byte[] b = new byte[outputBuffer.remaining()];
                                outputBuffer.get(b);
                                sendData(null, b);

                                if (isFirstFrame){
                                    firstFrame = b;
                                    MainActivity.mainActivity.videoServer.setConfigData(b,0,b.length);
                                    isFirstFrame = false;
                                }

                            } else {
                                Log.d(TAG, "onOutputBufferAvailable: info.size=" + info.size);
                                Log.d(TAG, "onOutputBufferAvailable: outputBuffer=" + outputBuffer);
                            }
                            if (encoder != null) {
                                encoder.releaseOutputBuffer(outputBufferId, false);
                            }

                            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "End of Stream");
                                stopScreenCapture();
                            }
                        }

                        @Override
                        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                            Log.i(TAG, "onOutputFormatChanged. CodecInfo:" + codec.getCodecInfo().toString() + " MediaFormat:" + format.toString());
                        }
                    });
                    break;
                default:
                    throw new RuntimeException("Unknown Media Format. You need to add mimetype to string.xml and else if statement");
            }

            this.encoder.configure(mediaFormat
                    , null // surface
                    , null // crypto
                    , MediaCodec.CONFIGURE_FLAG_ENCODE);

            this.inputSurface = this.encoder.createInputSurface();
            this.encoder.start();

        } catch (IOException e) {
            Log.e(TAG, "Failed to initial encoder, e: " + e);
            releaseEncoders();
        }

        this.virtualDisplay = this.mediaProjection.createVirtualDisplay("Recording Display", width, height, dpi, 0, this.inputSurface, null, null);
    }

    private void sendData(byte[] header, byte[] data) {
        //使用Netty发送
        /*if (header != null) {
            System.out.println("header != null");
            MainActivity.mainActivity.videoNettyServer.sendData(header);
        }
        MainActivity.mainActivity.videoNettyServer.sendData(data);*/
        if (header != null) {
            System.out.println("header != null");
            MainActivity.mainActivity.videoServer.sendH264Data(header,0,header.length);
        }
        MainActivity.mainActivity.videoServer.sendH264Data(data,0,data.length);
    }

    protected void stopScreenCapture() {
        Log.d(TAG, "stopScreenCapture: ");
        mProfile = 0;
        mLevel = 0;
        isFirstFrame = true;
        releaseEncoders();
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        virtualDisplay = null;
    }

    private void releaseEncoders() {

        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if (ivf != null) {
            ivf = null;
        }

        videoBufferInfo = null;
    }

    protected void stopScreenCapture2() {
        isFirstFrame = true;
        releaseEncoders2();
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        virtualDisplay = null;
    }

    private void releaseEncoders2() {

        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
//        if (mediaProjection != null) {
//            mediaProjection.stop();
//            mediaProjection = null;
//        }

        if (ivf != null) {
            ivf = null;
        }

        videoBufferInfo = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && mediaProjection != null) {
            Log.i(TAG, "onConfigurationChanged  is landscape");
            stopScreenCapture2();
            MainActivity.mainActivity.metrics  = new DisplayMetrics();
            try
            {
                Class c = Class.forName("android.view.Display");
                Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
                method.invoke(MainActivity.mainActivity.getWindowManager().getDefaultDisplay(), MainActivity.mainActivity.metrics);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            startScreenCapture(mResultCode, mResultData, mFormat, 1280,
                        720, mDpi, mBitrate);
            mScreenWidth = 1280;
            mScreenHeight = 720;
            Log.d(TAG, "onConfigurationChanged: width=" + MainActivity.mainActivity.metrics.widthPixels);

            NetworkMessages.serverLandscapeOrPortrait.Builder builder =
                    NetworkMessages.serverLandscapeOrPortrait.newBuilder();
            builder.setIsLandscape(true);
            try {
                MainActivity.mainActivity.messageServer.sendMessage(builder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
            NetworkMessages.ClientDisplayMetrics.Builder metricsBd =
                    NetworkMessages.ClientDisplayMetrics.newBuilder();
            metricsBd.setWidthPixels(MainActivity.mainActivity.metrics.widthPixels);
            metricsBd.setHeightPixels(MainActivity.mainActivity.metrics.heightPixels);
            try {
                MainActivity.mainActivity.messageServer.sendMessage(metricsBd.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && mediaProjection != null) {
            Log.i(TAG, "onConfigurationChanged  is portrait");
            stopScreenCapture2();
            MainActivity.mainActivity.metrics  = new DisplayMetrics();
            try
            {
                Class c = Class.forName("android.view.Display");
                Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
                method.invoke(MainActivity.mainActivity.getWindowManager().getDefaultDisplay(), MainActivity.mainActivity.metrics);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            startScreenCapture(mResultCode, mResultData, mFormat, 720,
                        1280, mDpi, mBitrate);
            Log.d(TAG, "onConfigurationChanged: width=" + MainActivity.mainActivity.metrics.widthPixels);
            mScreenWidth = 720;
            mScreenHeight = 1280;
            NetworkMessages.serverLandscapeOrPortrait.Builder builder =
                    NetworkMessages.serverLandscapeOrPortrait.newBuilder();
            builder.setIsLandscape(false);
            try {
                MainActivity.mainActivity.messageServer.sendMessage(builder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
            NetworkMessages.ClientDisplayMetrics.Builder metricsBd =
                    NetworkMessages.ClientDisplayMetrics.newBuilder();
            metricsBd.setWidthPixels(MainActivity.mainActivity.metrics.widthPixels);
            metricsBd.setHeightPixels(MainActivity.mainActivity.metrics.heightPixels);
            try {
                MainActivity.mainActivity.messageServer.sendMessage(metricsBd.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
