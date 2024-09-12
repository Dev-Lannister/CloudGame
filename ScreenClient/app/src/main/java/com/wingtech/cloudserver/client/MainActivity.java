package com.wingtech.cloudserver.client;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import com.wingtech.cloudserver.client.applicaiton.MyApplication;
import com.wingtech.cloudserver.client.coder.AudioDecoder;
import com.wingtech.cloudserver.client.customview.GameUI;
import com.wingtech.cloudserver.client.customview.MyFloatView;
import com.wingtech.cloudserver.client.protocols.NetworkMessages;
import com.wingtech.gameserver.network.netty.NettClientListener;
import com.wingtech.gameserver.network.netty.NettyClient;
import com.wingtech.gameserver.network.stream.H264TcpReceiver;
import com.wingtech.gameserver.network.stream.H264TcpReceiverListener;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class MainActivity extends Activity implements ViewTreeObserver.OnGlobalLayoutListener {

    private static final String TAG = "MainActivity";

    public static H264TcpReceiver videoClient;

    public static NettyClient messageClient;

    public H264TcpReceiver audioClient;

    public static DisplayMetrics metrics;

    private String ip;

    private int port;

    private byte[] image;

    public static MainActivity instance;

    AudioTrack audioTrack;
    int sampleRateInHz = 44100;
    int trackBufferSizeInBytes;


    String pkgName;
    String clsName;
    String username;
    int useProxy;
    String token;

    private boolean isBackgroud = false;

    private ImageView img;
    private ProgressBar progressBar;

    public AudioDecoder audioDecoder;
    int FRAME_RATE = 30;
    long mCount = 0;

    private AudioManager audioManager;

    private WindowManager wm = null;
    private WindowManager.LayoutParams wmParams = null;
    private MyFloatView myFV = null;

    static AlertDialog alertDialog;
    static AlertDialog.Builder alertBuilder;

    private static int currentDpi = 2;  //1:m  2:xh  3:xxh
    private static int currentBitRate = 2;

    FrameLayout content;
    long videoTimeStamp = 0;

    private boolean audioFirst = true;

    private long videoAudioDiffTime = 0;
    private long lastFrameLogTime = System.currentTimeMillis();
    private int audioDelayCount = 0;
    int audioFrames = 0;
    int videoFrames = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        hideBottomUIMenu();
        setContentView(R.layout.content_main);
        img = (ImageView) findViewById(R.id.background);
        progressBar = (ProgressBar) findViewById(R.id.progress);

        createView();

        instance = this;
        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        ip = getIntent().getStringExtra("ip");
        Log.d(TAG, "onCreate: serverIp=" + ip);
        port = getIntent().getIntExtra("port", 0);
        Log.d(TAG, "onCreate: port=" + port);
        image = getIntent().getByteArrayExtra("image");
        pkgName = getIntent().getStringExtra("pkgName");
        if (image != null && image.length != 0) {
            img.setImageBitmap(BitmapFactory.decodeByteArray(image, 0, image.length));
        } else {
            img.setImageResource(R.mipmap.common);
        }
        clsName = getIntent().getStringExtra("clsName");
        useProxy = getIntent().getIntExtra("useProxy", 0);
        token = getIntent().getStringExtra("token");
        username = getIntent().getStringExtra("username");
        connectServer();

        audioDecoder = AudioDecoder.newInstance();
        audioDecoder.initAudioDecoder();

        trackBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, trackBufferSizeInBytes * 5, AudioTrack.MODE_STREAM);
        audioTrack.play();

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        content = (FrameLayout) findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnGlobalLayoutListener(this);
        Log.d(TAG, "onCreate: ");

    }

    protected void hideBottomUIMenu() {
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    private void createView() {
        myFV = new MyFloatView(getApplicationContext());
        myFV.setImageResource(R.mipmap.setting);
        //获取WindowManager
        wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        //设置LayoutParams(全局变量）相关参数
        wmParams = ((MyApplication) getApplication()).getMywmParams();
        wmParams.type = WindowManager.LayoutParams.TYPE_PHONE;   //设置window type
        wmParams.format = PixelFormat.RGBA_8888;   //设置图片格式，效果为背景透明
        //设置Window flag
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wmParams.gravity = Gravity.LEFT | Gravity.TOP;   //调整悬浮窗口至左上角
        //以屏幕左上角为原点，设置x、y初始值
        wmParams.x = 0;
        wmParams.y = 0;
        //设置悬浮窗口长宽数据
        wmParams.width = 100;
        wmParams.height = 100;
        //显示myFloatView图像
        wm.addView(myFV, wmParams);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        hideBottomUIMenu();
        if (isBackgroud) {
            /*NetworkMessages.P2PWebRTCMessage.Builder builder = NetworkMessages.P2PWebRTCMessage.newBuilder();
            builder.setMessage("1");
            builder.setUserName("1");
            messageClient.sendMessage(builder.build());*/
            videoClient.resume();
            audioClient.resume();
            isBackgroud = false;
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Message message = Message.obtain();
                    message.what = 2;
                    MainActivity.mHandler.sendMessageDelayed(message, 7000);
                }
            }).start();
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        isBackgroud = true;
        audioFirst = true;
        videoClient.pause();
        audioClient.pause();
        /*NetworkMessages.P2PWebRTCMessage.Builder builder = NetworkMessages.P2PWebRTCMessage.newBuilder();
        builder.setMessage("0");
        builder.setUserName("0");
        messageClient.sendMessage(builder.build());*/
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged: ");
        super.onConfigurationChanged(newConfig);
    }

    private void connectServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (GameUI.mediaCodec == null && GameUI.codecIsCreated == false) {
                    //Log.d(TAG, "MediaCode未初始化完成");
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {

                    }
                }

                videoClient = new H264TcpReceiver(ip, port);
                if (useProxy == 1 && token !=null) {
                    videoClient.setUseProxy(true);
                    videoClient.setToken(token+ "&port=6000");
                } else {
                    videoClient.setUseProxy(false);
                    port++;
                }
                videoClient.setListener(new H264TcpReceiverListener() {
                    @Override
                    public void h264PackageReceived(byte[] bytes, int offset, int length, long serverTime) {
                        videoFrames++;
                        if (GameUI.mediaCodec != null && GameUI.codecIsCreated) {
                            videoTimeStamp = serverTime;
                            MediaCodec mediaCodec = GameUI.mediaCodec;
                            int inputBufferIndex = 0;
                            try {
                                inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return;
                            }
                            if (inputBufferIndex >= 0) {
                                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                                inputBuffer.clear();
                                inputBuffer.put(bytes, offset, length);
                                int value = bytes[offset + 4] & 0x0f;

                                if (value == 7 || value == 8) {
                                    Log.d(TAG, "h264PackageReceived: config frame");
                                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount * 4096000 / FRAME_RATE, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                                } else if (value == 5) {
                                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount * 4096000 / FRAME_RATE, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                                } else {
                                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount * 4096000 / FRAME_RATE, 0);
                                }
                                mCount++;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                            while (outputBufferIndex >= 0) {
                                /*Log.d(TAG, "decodeVideo: width = " + mediaCodec.getOutputFormat().getInteger("width"));
                                Log.d(TAG, "decodeVideo: height = " + mediaCodec.getOutputFormat().getInteger("height"));*/
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                            }
                        }
                    }

                    @Override
                    public void audioPackageReceived(byte[] bytes, int i, int i1, long l) {

                    }

                    @Override
                    public void config(int width, int height, int bitrate, byte orientation) {

                    }
                });
                videoClient.start();

                audioClient = new H264TcpReceiver(ip, port);
                if (useProxy == 1 && token != null) {
                    audioClient.setUseProxy(true);
                    audioClient.setToken(token+ "&port=6001");
                } else {
                    audioClient.setUseProxy(false);
                    port++;
                }
                audioClient.setListener(new H264TcpReceiverListener() {
                    @Override
                    public void h264PackageReceived(byte[] bytes, int i, int i1, long serverTime) {
                    }

                    @Override
                    public void audioPackageReceived(byte[] bytes, int i, int i1, long l) {
                        audioFrames++;
                        printFrameLog();
                        long time = Math.abs(l - videoTimeStamp);
                        videoAudioDiffTime += l - videoTimeStamp;
                        if (!audioFirst) {
                            if (500 < time && l - videoTimeStamp < 0) {
                                Log.d(TAG, "audioPackageReceived: 音视频时间差>500ms，丢弃.lllll=" + time);
                                return;
                            }
                        } else {
                            audioFirst = false;
                        }
                        audioDecoder.decodeAudio(bytes, i, i1);
                    }

                    @Override
                    public void config(int i, int i1, int i2, byte b) {

                    }
                });
                audioClient.start();

                messageClient = new NettyClient(ip, port);
                if (useProxy == 1 && token != null) {
                    messageClient.setUseProxy(true);
                    messageClient.setToken(token+ "&port=6002");
                } else {
                    messageClient.setUseProxy(false);
                }
                messageClient.setListener(new NettClientListener() {
                    @Override
                    public void connected(NettyClient nettyClient) {
                        Log.d(TAG, "connected: **********");
                        NetworkMessages.App_Login.Builder appLogin = NetworkMessages.App_Login.newBuilder();
                        appLogin.setUserName(username);
                        messageClient.sendMessage(appLogin.build());
                    }

                    @Override
                    public void closed(NettyClient nettyClient) {

                    }
                });
                messageClient.start();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                NetworkMessages.App_GameLauncherReq.Builder builder = NetworkMessages.App_GameLauncherReq.newBuilder();
                builder.setPackageName(pkgName);
                builder.setMainActivity(clsName);
                builder.setUserName(username);
                messageClient.sendMessage(builder.build());


            }
        }).start();
    }

    public static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Log.d(TAG, "handleMessage: SCREEN_ORIENTATION_PORTRAIT");

                    if (instance != null) {
                        MainActivity.instance.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        Log.d(TAG, "handleMessage: instance=null");
                    }
                    break;
                case 1:
                    Log.d(TAG, "handleMessage: SCREEN_ORIENTATION_LANDSCAPE");

                    if (instance != null) {
                        MainActivity.instance.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        Log.d(TAG, "handleMessage: instance=null");
                    }
                    break;
                case 2:
                    if (instance != null) {
                        instance.progressBar.setVisibility(View.GONE);
                        instance.img.setVisibility(View.GONE);
                    }
                    break;
                case 4:

                    NetworkMessages.ClientExitGame.Builder builder = NetworkMessages.ClientExitGame.newBuilder();
                    messageClient.sendMessage(builder.build());

                    videoClient.close();
                    instance.audioClient.close();
                    MainActivity.messageClient.close();
                    instance.finish();
                    break;
                case 5:
                    showDpiSetting();
                    break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        wm.removeView(myFV);
        instance = null;
        GameUI.mediaCodec = null;
        currentBitRate = 2;
        currentDpi = 2;
        isBackgroud = false;
        content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                Log.d(TAG, "onKeyDown: KEYCODE_VOLUME_UP");
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
                /*NetworkMessages.KeyEvent.Builder builder = NetworkMessages.KeyEvent.newBuilder();
                builder.setKeyCode(keyCode);
                builder.setIsLongPress(false);
                messageClient.sendMessage(builder.build());*/
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Log.d(TAG, "onKeyDown: KEYCODE_VOLUME_DOWN");
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
                /*NetworkMessages.KeyEvent.Builder b2 = NetworkMessages.KeyEvent.newBuilder();
                b2.setKeyCode(keyCode);
                b2.setIsLongPress(false);
                messageClient.sendMessage(b2.build());*/
                break;
            case KeyEvent.KEYCODE_BACK:
                Log.d(TAG, "onKeyDown: KEYCODE_BACK");
                break;
            case KeyEvent.KEYCODE_MENU:
                Log.d(TAG, "onKeyDown: KEYCODE_MENU");
                break;
        }
        //home power can not observe
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                Log.d(TAG, "onKeyUp: KEYCODE_VOLUME_UP");
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Log.d(TAG, "onKeyUp: KEYCODE_VOLUME_DOWN");
                break;
            case KeyEvent.KEYCODE_BACK:
                Log.d(TAG, "onKeyUp: KEYCODE_BACK");
                NetworkMessages.KeyEvent.Builder builder = NetworkMessages.KeyEvent.newBuilder();
                builder.setKeyCode(keyCode);
                builder.setIsLongPress(false);
                messageClient.sendMessage(builder.build());
                break;
            case KeyEvent.KEYCODE_MENU:
                Log.d(TAG, "onKeyUp: KEYCODE_MENU");
                NetworkMessages.KeyEvent.Builder b2 = NetworkMessages.KeyEvent.newBuilder();
                b2.setKeyCode(keyCode);
                b2.setIsLongPress(false);
                messageClient.sendMessage(b2.build());
                break;
        }
        return true;
    }

    public void initAudioTrack(final byte[] bytes) {
        audioTrack.write(bytes, 0, bytes.length);
        audioTrack.flush();
    }

    private static void showDpiSetting() {
        alertBuilder = new AlertDialog.Builder(MainActivity.instance);
        View view = View.inflate(instance, R.layout.dpi_setting, null);
        alertDialog = alertBuilder.setTitle("游戏设置")
                .setView(view)
                .setCancelable(false)
                .create();
        alertDialog.show();
        RadioGroup dpiGroup = view.findViewById(R.id.dpi_setting_group);
        RadioGroup bitRateGroup = view.findViewById(R.id.bitRate_setting_group);
        if (currentDpi == 0) {
            dpiGroup.check(R.id.xh_dpi);
        } else if (currentDpi == 1) {
            dpiGroup.check(R.id.m_dpi);
        } else if (currentDpi == 2) {
            dpiGroup.check(R.id.xh_dpi);
        } else if (currentDpi == 3) {
            dpiGroup.check(R.id.xxh_dpi);
        }
        if (currentBitRate == 0) {
            bitRateGroup.check(R.id.xh_bitRate);
        } else if (currentBitRate == 1) {
            bitRateGroup.check(R.id.m_bitRate);
        } else if (currentBitRate == 2) {
            bitRateGroup.check(R.id.xh_bitRate);
        } else if (currentBitRate == 3) {
            bitRateGroup.check(R.id.xxh_bitRate);
        } else if (currentBitRate == 4) {
            bitRateGroup.check(R.id.xxxh_bitRate);
        }
        dpiGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.m_dpi) {
                    currentDpi = 1;
                } else if (i == R.id.xh_dpi) {
                    currentDpi = 2;
                } else if (i == R.id.xxh_dpi) {
                    currentDpi = 3;
                }
            }
        });
        bitRateGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.m_bitRate) {
                    currentBitRate = 1;
                } else if (i == R.id.xh_bitRate) {
                    currentBitRate = 2;
                } else if (i == R.id.xxh_bitRate) {
                    currentBitRate = 3;
                } else if (i == R.id.xxxh_bitRate) {
                    currentBitRate = 4;
                }
            }
        });
        LinearLayout exit_game = (LinearLayout) view.findViewById(R.id.exit_game);
        exit_game.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                android.os.Message m = android.os.Message.obtain();
                m.what = 4;
                MainActivity.mHandler.sendMessage(m);
                alertDialog.dismiss();
                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    View v = instance.getWindow().getDecorView();
                    v.setSystemUiVisibility(View.GONE);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    View decorView = instance.getWindow().getDecorView();
                    int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    decorView.setSystemUiVisibility(uiOptions);
                }
            }
        });
        LinearLayout submit_setting = (LinearLayout) view.findViewById(R.id.submit_setting);
        submit_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NetworkMessages.App_Setting.Builder builder = NetworkMessages.App_Setting.newBuilder();
                builder.setScreenDpi(currentDpi);
                builder.setBitRate(currentBitRate);
                messageClient.sendMessage(builder.build());
                alertDialog.dismiss();
                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    View v = instance.getWindow().getDecorView();
                    v.setSystemUiVisibility(View.GONE);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    View decorView = instance.getWindow().getDecorView();
                    int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    decorView.setSystemUiVisibility(uiOptions);
                }
            }
        });
        /*LinearLayout audio_play_test = (LinearLayout) view.findViewById(R.id.audio_play_test);
        audio_play_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NetworkMessages.P2PWebRTCMessage.Builder builder = NetworkMessages.P2PWebRTCMessage.newBuilder();
                builder.setMessage("0");
                builder.setUserName("0");
                messageClient.sendMessage(builder.build());
                alertDialog.dismiss();
                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    View v = instance.getWindow().getDecorView();
                    v.setSystemUiVisibility(View.GONE);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    View decorView = instance.getWindow().getDecorView();
                    int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    decorView.setSystemUiVisibility(uiOptions);
                }
            }
        });
        LinearLayout audio_pause_test = (LinearLayout) view.findViewById(R.id.audio_pause_test);
        audio_pause_test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NetworkMessages.P2PWebRTCMessage.Builder builder = NetworkMessages.P2PWebRTCMessage.newBuilder();
                builder.setMessage("1");
                builder.setUserName("1");
                messageClient.sendMessage(builder.build());
                alertDialog.dismiss();
                if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
                    View v = instance.getWindow().getDecorView();
                    v.setSystemUiVisibility(View.GONE);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    View decorView = instance.getWindow().getDecorView();
                    int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
                    decorView.setSystemUiVisibility(uiOptions);
                }
            }
        });*/
    }

    @Override
    public void onGlobalLayout() {
        Log.d(TAG, "onGlobalLayout: contentChange");
        metrics = new DisplayMetrics();
        try {
            Class c = Class.forName("android.view.Display");
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(MainActivity.instance.getWindowManager().getDefaultDisplay(), metrics);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "onGlobalLayout: widthPixels=" + metrics.widthPixels);
        GameUI.localWidth = metrics.widthPixels;
        GameUI.localHeight = metrics.heightPixels;
    }

    protected void printFrameLog() {
        if (System.currentTimeMillis() - lastFrameLogTime > 3000) {
            long time = (System.currentTimeMillis() - lastFrameLogTime) / 1000;
            //同步视频和音频
            if ((videoAudioDiffTime / time) < -1000 && audioClient.isConnected()) {
                audioDelayCount++;
                if (audioDelayCount > 3) {
                    Log.d(TAG, "printFrameLog: 音频连接重置");
                    audioClient.askReset();
                    audioDelayCount = 0;
                }
            } else {
                audioDelayCount = 0;
            }

            /*Log.d(TAG, "视频接收帧率：" + (videoFrames / time ) + "帧/秒");
            Log.d(TAG, "音频接收帧率：" + (audioFrames / time ) + "帧/秒");
            Log.d(TAG, "视频音频时间差：" + (videoAudioDiffTime / time) + "毫秒");*/
            lastFrameLogTime = System.currentTimeMillis();
            videoFrames = 0;
            audioFrames = 0;
            videoAudioDiffTime = 0;
        }
    }

}
