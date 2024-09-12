package com.wingtech.cloudserver;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.wingtech.cloudserver.codec.AudioEncoder;
import com.wingtech.cloudserver.consts.ActivityServiceMessage;
import com.wingtech.cloudserver.consts.ExtraIntent;
import com.wingtech.cloudserver.listener.ClearUserDataObserver;
import com.wingtech.cloudserver.netty.ClientHandler;
import com.wingtech.cloudserver.netty.CloudHandler;
import com.wingtech.cloudserver.netty.NettyServer;
import com.wingtech.cloudserver.netty.UploadFile;
import com.wingtech.cloudserver.protocols.NetworkMessages;
import com.wingtech.cloudserver.service.CheckIsHomeService;
import com.wingtech.cloudserver.service.ScreenCastService;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import com.google.protobuf.ByteString;
import com.wingtech.cloudserver.util.PackageUtils;
import com.wingtech.cloudserver.util.ZipUtil;
import com.wingtech.gameserver.network.netty.NettyClient;
import com.wingtech.gameserver.network.netty.NettClientListener;
import com.wingtech.gameserver.network.stream.H264TcpSession;
import com.wingtech.gameserver.protocols.Messages;
import com.wingtech.gameserver.network.stream.H264TcpSender;
import com.wingtech.gameserver.network.stream.H264TcpSenderListener;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    public static MainActivity mainActivity;

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION = 300;

    private int stateResultCode;
    private Intent stateResultData;

    private Messenger messenger;

    private MediaProjectionManager mediaProjectionManager;
    private ServiceConnection serviceConnection;
    private Messenger serviceMessenger;
    public H264TcpSender videoServer;
    public H264TcpSender audioServer;
    public NettyServer messageServer;
    public static DisplayMetrics metrics;

    AudioRecord audioRecord;
    public boolean isRecording = false;
    int audioSource = MediaRecorder.AudioSource.REMOTE_SUBMIX;
    int sampleRateInHz = 44100;
    int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    LinkedList<byte[]> mRecordLinkList;

    public PowerManager powerManager;
    public PowerManager.WakeLock wakeLock;
    public KeyguardManager keyguardManager;
    public KeyguardManager.KeyguardLock keyguardLock;

    public NettyClient nettyClient;

    ConnectivityManager connectivityManager;
    NetworkInfo networkInfo;

    AudioEncoder audioEncoder;
    boolean settingChange = false;

    Timer timer;
    private LocalSocket bakSocket;
    private OutputStream bakOutputStream;
    private PrintWriter print;
    public ClearUserDataObserver mClearDataObserver;
    public ActivityManager am;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate: ");

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: wakelock and keguardlock +++");
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.SCREEN_DIM_WAKE_LOCK, "clientWakeUp");
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        keyguardLock = keyguardManager.newKeyguardLock("clientKeyguardLock");
        Log.d(TAG, "onCreate: wakelock and keguardlock ---");

        connectCloud();

        am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        if (savedInstanceState != null) {
            this.stateResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            this.stateResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }

        this.mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        this.messenger = new Messenger(new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.i(TAG, "Handler got message : " + msg.what);
                return false;
            }
        }));

        this.serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, name + " service is connected.");

                serviceMessenger = new Messenger(service);
                Message msg = Message.obtain(null, ActivityServiceMessage.CONNECTED);
                msg.replyTo = messenger;
                try {
                    serviceMessenger.send(msg);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send message due to:" + e.toString());
                    e.printStackTrace();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, name + " service is disconnected.");
                serviceMessenger = null;
            }
        };

        final Button startButton = (Button) findViewById(R.id.button_start);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Start button clicked.");
                startCaptureScreen();
            }
        });

        final Button stopButton = (Button) findViewById(R.id.button_stop);
        stopButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                stopScreenCapture();
                isRecording = false;
                audioRecord.stop();
                audioRecord.release();
            }
        });

        startService();

        videoServer = new H264TcpSender(6000);
        videoServer.setMaxBufferPackage(15);
        videoServer.setMaxBufferPackageForResend(5);
        videoServer.setListener(new ConnectListener());
        videoServer.start();

        /*TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (totalSkip > 50 && !settingChange) {
                    Log.d(TAG, "run: 降低分辨率");
                    totalSkip = 0;
                    settingChange = true;
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setDpi(640,360,3072000);
                    }else {
                        setDpi(360,640,3072000);
                    }
                } else if (totalSkip < 50 && settingChange){
                    totalSkip = 0;
                    Log.d(TAG, "run: 提高分辨率");
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setDpi(1280,720,4096000);
                    }else {
                        setDpi(720,1280,4096000);
                    }
                    settingChange = false;
                }
            }
        };

        Timer timer = new Timer();
        timer.schedule(timerTask,0,6000);*/

        audioServer = new H264TcpSender(6001);
        audioServer.setMaxBufferPackage(3);
        audioServer.setMaxBufferPackageForResend(0);
        audioServer.setIAudio(true);
        audioServer.start();

        messageServer = new NettyServer(6002);
        messageServer.start();

        MainActivity.mainActivity = this;

        Button audioRecord = (Button) findViewById(R.id.button_audio_record);
        audioRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startAudioRecord();
            }
        });

        /*Log.d(TAG, "onCreate: property=" + SystemProperties.get("persist.sys.copy_service"));
        SystemProperties.set("persist.sys.copy_service", 1 + "");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                    openAndWriteBakSocket("com.sg.raiden.qihu", 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();*/
        /*try {
            ZipUtil.unzip("/sdcard/gamedata/com.sg.raiden.qihu.zip","/sdcard/gamedata/com.sg.raiden.qihu");
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        /*ActivityManager am = (ActivityManager)
                getSystemService(Context.ACTIVITY_SERVICE);
        if (mClearDataObserver == null) {
            mClearDataObserver = new ClearUserDataObserver();
        }
        boolean res = am.clearApplicationUserData("com.sg.raiden.qihu", mClearDataObserver);
        Log.d(TAG, "onCreate: res=" + res);*/
    }

    public void startAudioRecord() {

        audioEncoder = AudioEncoder.newInstance();
        audioEncoder.initAudioEncoder();

        isRecording = true;
        mRecordLinkList = new LinkedList<>();

        final int recBufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        Log.d(TAG, "startAudioRecord: recBufferSizeInBytes=" + recBufferSizeInBytes);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig,
                            audioFormat, recBufferSizeInBytes);

                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "run: audioRecord is initialized");

                        audioRecord.startRecording();

                        byte[] buffer = new byte[recBufferSizeInBytes];

                        while (isRecording) {
                            int readSize = audioRecord.read(buffer, 0, recBufferSizeInBytes);
                            audioEncoder.encodeAudio(buffer);
                        }
                        Log.d(TAG, "isRecording = " + isRecording);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (stateResultData != null) {
            outState.putInt(STATE_RESULT_CODE, stateResultCode);
            outState.putParcelable(STATE_RESULT_DATA, stateResultData);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User didn't allow.");
            } else {
                Log.d(TAG, "Starting screen capture");
                stateResultCode = resultCode;
                stateResultData = data;
                startCaptureScreen();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    private void unbindService() {
        if (serviceMessenger != null) {
            try {
                Message msg = Message.obtain(null, ActivityServiceMessage.DISCONNECTED);
                msg.replyTo = messenger;
                serviceMessenger.send(msg);
            } catch (RemoteException e) {
                Log.d(TAG, "Failed to send unregister message to service, e: " + e.toString());
                e.printStackTrace();
            }
            unbindService(serviceConnection);
        }
    }

    private void startService() {

        Log.i(TAG, "Starting cast service");

        final Intent intent = new Intent(this, ScreenCastService.class);
        if (stateResultCode != 0 && stateResultData != null) {

            metrics = new DisplayMetrics();
            //getWindowManager().getDefaultDisplay().getMetrics(metrics);
            try {
                Class c = Class.forName("android.view.Display");
                Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
                method.invoke(getWindowManager().getDefaultDisplay(), metrics);
            } catch (Exception e) {
                e.printStackTrace();
            }

            intent.putExtra(ExtraIntent.RESULT_CODE.toString(), stateResultCode);
            intent.putExtra(ExtraIntent.RESULT_DATA.toString(), stateResultData);
            intent.putExtra(ExtraIntent.VIDEO_FORMAT.toString(), "video/avc");

            //++modify lijiwei
            /*intent.putExtra(ExtraIntent.SCREEN_WIDTH.toString(), metrics.widthPixels);
            intent.putExtra(ExtraIntent.SCREEN_HEIGHT.toString(), metrics.heightPixels);*/
            intent.putExtra(ExtraIntent.SCREEN_WIDTH.toString(), 1280);
            intent.putExtra(ExtraIntent.SCREEN_HEIGHT.toString(), 720);
            Log.d(TAG, "widthPixels = " + metrics.widthPixels);
            Log.d(TAG, "heightPixels = " + metrics.heightPixels);
            intent.putExtra(ExtraIntent.SCREEN_DPI.toString(), metrics.densityDpi);
            //--

            intent.putExtra(ExtraIntent.VIDEO_BITRATE.toString(), 3072000);
        }

        ComponentName name = startService(intent);
        Log.i(TAG, "服务名称： " + name);
        if (bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.i(TAG, "启动成功！");
        } else {
            Log.i(TAG, "启动失败！");
        }
    }

    public void startCaptureScreen() {
        if (stateResultCode != 0 && stateResultData != null) {
            startService();
        } else {
            Log.d(TAG, "Requesting confirmation");
            startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(), ACTIVITY_RESULT_REQUEST_MEDIA_PROJECTION);
        }
    }

    public void stopScreenCapture() {
        if (serviceMessenger == null) {
            return;
        }

        Message msg = Message.obtain(null, ActivityServiceMessage.STOP);
        msg.replyTo = messenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:" + e.toString());
            e.printStackTrace();
        }
    }

    public void setDpi(int widthDpi, int heightDpi, int bitRate, int profile, int level) {
        if (serviceMessenger == null) {
            return;
        }

        Message msg = Message.obtain(null, ActivityServiceMessage.DPISETTING);
        Bundle bundle = new Bundle();
        bundle.putInt("widthDpi", widthDpi);
        bundle.putInt("heightDpi", heightDpi);
        bundle.putInt("bitRate", bitRate);
        bundle.putInt("profile", profile);
        bundle.putInt("level", level);
        msg.setData(bundle);
        msg.replyTo = messenger;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException:" + e.toString());
            e.printStackTrace();
        }
    }

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:

                    int orientation = MainActivity.mainActivity.getResources()
                            .getConfiguration().orientation;
                    if (orientation == 1) {
                        NetworkMessages.serverLandscapeOrPortrait.Builder builder =
                                NetworkMessages.serverLandscapeOrPortrait.newBuilder();
                        builder.setIsLandscape(false);
                        try {
                            messageServer.sendMessage(builder.build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "sendMessage: serverLandscapeOrPortrait=" + 1);
                    } else {
                        NetworkMessages.serverLandscapeOrPortrait.Builder builder =
                                NetworkMessages.serverLandscapeOrPortrait.newBuilder();
                        builder.setIsLandscape(true);
                        try {
                            messageServer.sendMessage(builder.build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "sendMessage: serverLandscapeOrPortrait=" + 2);
                    }
                    NetworkMessages.ClientDisplayMetrics.Builder metricsBd =
                            NetworkMessages.ClientDisplayMetrics.newBuilder();
                    metricsBd.setWidthPixels(MainActivity.mainActivity.metrics.widthPixels);
                    metricsBd.setHeightPixels(MainActivity.mainActivity.metrics.heightPixels);
                    try {
                        messageServer.sendMessage(metricsBd.build());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (!wakeLock.isHeld()) {
                        wakeLock.acquire();
                    }
                    keyguardLock.disableKeyguard();

                    break;
                case 1:
                    Bundle bundle = msg.getData();
                    final String apkPath = bundle.getString("apkPath");
                    final String fileName = bundle.getString("fileName");
                    Log.d(TAG, "handleMessage: install filename=" + fileName);
                    int installResult = PackageUtils.installSilent(mainActivity, apkPath, "-r -i");
                    if (installResult == 1) {
                        Log.d(TAG, "handleMessage: install " + apkPath + " success");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                String ip = null;
                                try {
                                    InetAddress address = getLocalHostLANAddress();
                                    ip = address.getHostAddress();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                Messages.Game_UpdateInfoReq.Builder builder = Messages.Game_UpdateInfoReq.newBuilder();
                                builder.setIp(ip);
                                List<Map<String, Object>> appList = getAPKInfo();
                                for (Map<String, Object> map : appList) {
                                    Messages.Game.Builder game = Messages.Game.newBuilder();
                                    game.setName((String) map.get("appName"));
                                    game.setPackageName((String) map.get("pkgName"));
                                    game.setMainActivity((String) map.get("className"));
                                    builder.addGames(game.build());
                                    Log.d(TAG, "appList: " + (String) map.get("pkgName"));
                                }
                                nettyClient.sendMessage(builder.build());

                                Messages.Game_InstallAPKResp.Builder bd = Messages.Game_InstallAPKResp.newBuilder();
                                bd.setFilename(fileName);
                                bd.setMessage("success");
                                nettyClient.sendMessage(bd.build());

                            }
                        }).start();
                        File file = new File(apkPath);
                        file.delete();
                    } else {
                        Log.d(TAG, "handleMessage: install" + apkPath + "fail");
                        Messages.Game_InstallAPKResp.Builder bd = Messages.Game_InstallAPKResp.newBuilder();
                        bd.setFilename(apkPath);
                        bd.setMessage("fail");
                        nettyClient.sendMessage(bd.build());
                    }
                    break;
                case 2:
                    startAll();
                    break;
                case 3:
                    Log.d(TAG, "startCheckIsHomeService: ");
                    String pkgName = (String) msg.obj;
                    Intent checkIsHomeIntent = new Intent(MainActivity.mainActivity, CheckIsHomeService.class);
                    checkIsHomeIntent.putExtra("pkgName", pkgName);
                    MainActivity.mainActivity.startService(checkIsHomeIntent);
                    CheckIsHomeService.isNotHome = true;
                    break;
                case 4:
                    Bundle bundl = msg.getData();
                    int dpiSetting = bundl.getInt("dpi");
                    int bitRateSetting = bundl.getInt("bitRate");
                    int profile = bundl.getInt("profile");
                    int level = bundl.getInt("level");
                    gameSet(dpiSetting, bitRateSetting, profile, level);
                    break;
                case 5:
                    String packageName = (String) msg.obj;
                    Log.d(TAG, "handleMessage: pkgName=" + packageName);
                    uninstallApp(packageName);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String ip = null;
                            try {
                                InetAddress address = getLocalHostLANAddress();
                                ip = address.getHostAddress();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Messages.Game_UpdateInfoReq.Builder builder = Messages.Game_UpdateInfoReq.newBuilder();
                            builder.setIp(ip);
                            for (Map<String, Object> map : getAPKInfo()) {
                                Messages.Game.Builder game = Messages.Game.newBuilder();
                                game.setName((String) map.get("appName"));
                                game.setPackageName((String) map.get("pkgName"));
                                game.setMainActivity((String) map.get("className"));
                                builder.addGames(game.build());
                            }
                            nettyClient.sendMessage(builder.build());
                        }
                    }).start();
                    break;
                case 6:
                    String pkg = (String) msg.obj;
                    getIcon(pkg);
                    break;
                case 7:
                    Messages.Game_Log.Builder log = Messages.Game_Log.newBuilder();
                    log.setTime(System.currentTimeMillis());
                    log.setTag("MainActivity");
                    log.setLogText("testMode");
                    nettyClient.sendMessage(log.build());
                    break;
                case 8:
                    Log.d(TAG, "upload data complete");
                    String name = (String) msg.obj;
                    if (mClearDataObserver == null) {
                        mClearDataObserver = new ClearUserDataObserver();
                    }
                    Log.d(TAG, "clear user data");
                    boolean res = am.clearApplicationUserData(name, mClearDataObserver);
                    Log.d(TAG, "clearResult: res=" + res);
                    mClearDataObserver = null;
                    Log.d(TAG, "delete data.zip");
                    File f = new File(Environment.getExternalStorageDirectory().getPath() + File.separator
                            + "gamedata" + File.separator + name + ".zip");
                    f.delete();
                    //lijiwei.add 20180421
                    Messages.Game_AppDisconnected.Builder builder = Messages.Game_AppDisconnected.newBuilder();
                    MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
                    Log.d(TAG, "handleClientExitGame Game_AppDisconnected: ");
                    break;
                default:
                    break;
            }
        }
    };

    public void getIcon(String pkgName) {
        try {
            Drawable icon = getPackageManager().getApplicationInfo(pkgName, 0)
                    .loadIcon(getPackageManager());
            Messages.GetIconResp.Builder builder = Messages.GetIconResp.newBuilder();
            builder.setPackageName(pkgName);
            builder.setIcon(ByteString.copyFrom(Bitmap2Bytes(drawableToBitmap(icon))));
            nettyClient.sendMessage(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void gameSet(int dpiSetting, int bitRateSetting, int profile, int level) {
        int widthDpi, heightDpi, bitRate = 3072000;
        if (dpiSetting == 1) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthDpi = 640;
                heightDpi = 360;
            } else {
                widthDpi = 360;
                heightDpi = 640;
            }
        } else if (dpiSetting == 2) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthDpi = 1280;
                heightDpi = 720;
            } else {
                widthDpi = 720;
                heightDpi = 1280;
            }
        } else {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                widthDpi = 1920;
                heightDpi = 1080;
            } else {
                widthDpi = 1080;
                heightDpi = 1920;
            }
        }
        if (bitRateSetting == 1) {
            bitRate = 2048000;
        } else if (bitRateSetting == 2) {
            bitRate = 3072000;
        } else if (bitRateSetting == 3) {
            bitRate = 4096000;
        } else if (bitRateSetting == 4) {
            bitRate = 6144000;
        }
        setDpi(widthDpi, heightDpi, bitRate, profile, level);
    }

    public void startAll() {
        settingChange = false;
        startCaptureScreen();
        startAudioRecord();
    }

    public void stopAll() {
        try {
            stopScreenCapture();
            stateResultCode = 0;
            stateResultData = null;
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioEncoder.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopAudioRecord() {
        isRecording = false;
        audioRecord.stop();
        audioRecord.release();
    }

    public void connectCloud() {

        String toPath = Environment.getExternalStorageDirectory().getPath() + File.separator
                + "gamedata";
        File file = new File(toPath);
        if (!file.exists()) {
            file.mkdir();
        }

        Log.d(TAG, "connectCloud: ++++++");
        com.wingtech.gameserver.network.handler.HandlerManager.regist(CloudHandler.class);
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                    networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo != null) {
                        if (networkInfo.isAvailable()) {
                            break;
                        }
                    }
                }
                try {
                    nettyClient = new NettyClient("122.225.54.17", 6003);
                    nettyClient.setListener(new NettyClientConListener());
                    nettyClient.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "connectCloud: -----");
            }
        }).start();
    }

    public InetAddress getLocalHostLANAddress() throws Exception {
        try {
            InetAddress candidateAddress = null;
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {
                        if (inetAddr.isSiteLocalAddress()) {
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            return jdkSuppliedAddress;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        // 取 drawable 的长宽
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();

        // 取 drawable 的颜色格式
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                : Bitmap.Config.RGB_565;
        // 建立对应 bitmap
        Bitmap bitmap = Bitmap.createBitmap(w, h, config);
        // 建立对应 bitmap 的画布
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        // 把 drawable 内容画到画布中
        drawable.draw(canvas);
        return bitmap;
    }

    public byte[] Bitmap2Bytes(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }

    public List<Map<String, Object>> getAPKInfo() {
        List<Map<String, Object>> list = new ArrayList<>();
        Intent intent = new Intent("android.intent.action.MAIN", null);
        intent.addCategory("android.intent.category.LAUNCHER");
        List<ResolveInfo> apklList = getPackageManager().queryIntentActivities(intent, 0);

        List<PackageInfo> appList = getPackageManager().getInstalledPackages(0);

        for (PackageInfo packageInfo : appList) {

            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                Map<String, Object> map = new HashMap<>();
                String pkg = packageInfo.packageName;
                String cls = null;

                for (ResolveInfo resolveInfo : apklList) {
                    String packageStr = resolveInfo.activityInfo.packageName;
                    if (packageStr.equals(pkg)) {
                        cls = resolveInfo.activityInfo.name;
                    }
                }


                //Drawable icon = packageInfo.applicationInfo.loadIcon(getPackageManager());
                String appName = packageInfo.applicationInfo.loadLabel(getPackageManager()).toString();

                map.put("appIcon", null);
                map.put("appName", appName);
                map.put("pkgName", pkg);
                map.put("className", cls);
                list.add(map);
            }
        }
        return list;
    }

    private void uninstallApp(String packageName) {
        try {
            PackageManager pm = mainActivity.getPackageManager();
            Method[] methods = pm.getClass().getDeclaredMethods();
            Method delPacMethod = null;
            for (Method method : methods) {
                if (method.getName().equals("deletePackage")) {
                    delPacMethod = method;
                    break;
                }
            }
            delPacMethod.setAccessible(true);
            delPacMethod.invoke(pm, packageName, null, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openAndWriteBakSocket(final String pkgName, final int flag) {
        SystemProperties.set("persist.sys.copy_service", 1 + "");
        File file = new File(Environment.getExternalStorageDirectory().getPath() + File.separator
                + "gamedata" + File.separator + pkgName);
        if (!file.exists()) {
            file.mkdirs();
        }
        String sourcePath = "/data/data/" + pkgName + File.separator;
        String desPath = Environment.getExternalStorageDirectory().getPath() + File.separator
                + "gamedata" + File.separator + pkgName + File.separator;
        final String s;
        if (flag == 0) {
            s = sourcePath + "," + desPath;
        } else {
            s = desPath + "," + sourcePath;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                    bakSocket = new LocalSocket();
                    bakSocket.connect(
                            new LocalSocketAddress("gamedatabackup",
                                    LocalSocketAddress.Namespace.RESERVED));
                    Log.d(TAG, "run: socket connect establish");

                    bakOutputStream = bakSocket.getOutputStream();
                    print = new PrintWriter(bakOutputStream);
                } catch (Exception e) {
                    Log.d(TAG, "openBakSocket: failed");
                    bakSocket = null;
                    e.printStackTrace();
                }
                try {
                    Log.d(TAG, "bakOutputStream.write " + s);
                    print.println(s);
                    print.flush();

                    BufferedReader br = new BufferedReader(new InputStreamReader(bakSocket.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    Log.d(TAG, "openAndWriteBakSocket: rev=" + sb.toString());
                    Log.d(TAG, "openAndWriteBakSocket: close copy_service");
                    br.close();
                    if (flag == 0) {
                        Log.d(TAG, "openAndWriteBakSocket: copy data/data to sdcard complete");
                        ZipUtil.zip(Environment.getExternalStorageDirectory().getPath() + File.separator
                                        + "gamedata" + File.separator + pkgName,
                                Environment.getExternalStorageDirectory().getPath() + File.separator
                                        + "gamedata" + File.separator + pkgName + ".zip");
                        File f = new File(Environment.getExternalStorageDirectory().getPath() + File.separator
                                + "gamedata" + File.separator + pkgName);
                        ZipUtil.delete(f.getPath());
                        //上传data
                        Log.d(TAG, "openAndWriteBakSocket: start upload data");
                        UploadFile.uploadData(pkgName);
                    } else {
                        Log.d(TAG, "openAndWriteBakSocket: copy sdcard to data/data complete");
                        ClientHandler.isReady = true;
                    }

                } catch (Exception e) {
                    Log.d(TAG, "Error writing to socket");
                    e.printStackTrace();
                } finally {
                    try {
                        bakOutputStream.close();
                        bakSocket.close();
                        print.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                SystemProperties.set("persist.sys.copy_service", 0 + "");
            }
        }).start();
    }

}

class ConnectListener implements H264TcpSenderListener {
    @Override
    public void onSkip(H264TcpSender var1, int var2) {
        //Log.d(TAG, "video onSkip: " + skipRate + "/s");
        //totalSkip += skipRate;
    }

    @Override
    public void onConnected(H264TcpSender var1, H264TcpSession var2) {

    }

    @Override
    public void onPause(H264TcpSender var1, H264TcpSession var2) {

    }

    @Override
    public void onResume(H264TcpSender var1, H264TcpSession var2) {
        MainActivity.mainActivity.stopScreenCapture();
        MainActivity.mainActivity.stopAudioRecord();
        MainActivity.mainActivity.startAudioRecord();
        MainActivity.mainActivity.startCaptureScreen();
    }

    @Override
    public void onDisconnected(H264TcpSender var1, H264TcpSession var2) {
    }

}

class NettyClientConListener implements NettClientListener {
    @Override
    public void connected(NettyClient nettyClient) {
        try {
            String ip = null;
            InetAddress address = MainActivity.mainActivity.getLocalHostLANAddress();
            ip = address.getHostAddress();

            Messages.Game_UpdateInfoReq.Builder builder = Messages.Game_UpdateInfoReq.newBuilder();
            builder.setIp(ip);
            Log.d("NettyClientConListener", "run: ip=" + ip);

            for (Map<String, Object> map : MainActivity.mainActivity.getAPKInfo()) {
                Messages.Game.Builder game = Messages.Game.newBuilder();
                game.setName((String) map.get("appName"));
                game.setPackageName((String) map.get("pkgName"));
                game.setMainActivity((String) map.get("className"));
                builder.addGames(game.build());
                Log.d("sendGameList", "sendGameList: " + game.getName());
            }

            Log.d("NettyClientConListener", "run: 连接至云");
            nettyClient.sendMessage(builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void closed(NettyClient nettyClient) {
    }
}