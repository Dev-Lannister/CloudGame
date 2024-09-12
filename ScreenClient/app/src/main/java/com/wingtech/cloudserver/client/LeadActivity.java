package com.wingtech.cloudserver.client;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.wingtech.cloudserver.client.adapter.AppListAdapter;
import com.wingtech.cloudserver.client.handler.CloudHandler;
import com.wingtech.cloudserver.client.handler.ServerHandler;
import com.wingtech.gameserver.network.handler.HandlerManager;
import com.wingtech.gameserver.network.netty.NettClientListener;
import com.wingtech.gameserver.network.netty.NettyClient;
import com.wingtech.gameserver.protocols.Messages;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lijiwei on 2018/1/3.
 */

public class LeadActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = "LeadActivity";

    private ProgressBar progressBar;
    ListView listView;
    AppListAdapter adapter;
    List<Map<String, Object>> list = new ArrayList<>();
    List<Map<String, Object>> appList = new ArrayList<>();
    NettyClient nettyClient;

    String pkgName;
    String clsName;
    String username;

    public static LeadActivity instance;

    public int listSize = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_lead);
        instance = this;
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.VISIBLE);
        requestPermission();
        listView = (ListView) findViewById(R.id.list_view);
        adapter = new AppListAdapter(this, appList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        username = getIntent().getStringExtra("username");
    }

    public void getGameList() {

        HandlerManager.regist(CloudHandler.class);
        HandlerManager.regist(ServerHandler.class);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (nettyClient == null) {
                    nettyClient = new NettyClient("192.168.20.226", 6003);
                    nettyClient.setListener(new NettClientListener() {
                        @Override
                        public void connected(NettyClient nettyClient) {
                            Log.d(TAG, "connected: 连接至云");
                            Messages.App_Login.Builder builder = Messages.App_Login.newBuilder();
                            builder.setName(username);
                            builder.setPassword("test");
                            nettyClient.sendMessage(builder.build());
                        }

                        @Override
                        public void closed(NettyClient nettyClient) {

                        }
                    });
                    nettyClient.start();
                    Messages.App_ListGamesReq.Builder builder = Messages.App_ListGamesReq.newBuilder();
                    nettyClient.sendMessage(builder.build());
                }
            }
        }).start();
    }

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    list.clear();
                    List<Messages.Game> gamesList = (List<Messages.Game>) msg.obj;
                    listSize = gamesList.size();
                    for (Messages.Game game : gamesList) {

                        Map<String, Object> map = new HashMap<>();

                        Messages.GetIconReq.Builder builder = Messages.GetIconReq.newBuilder();
                        builder.setPackageName(game.getPackageName());
                        nettyClient.sendMessage(builder.build());

                        map.put("appName", game.getName());
                        map.put("pkgName", game.getPackageName());
                        map.put("className", game.getMainActivity());
                        list.add(map);
                    }
                    break;
                case 1:
                    Bundle bundle = msg.getData();
                    Log.d(TAG, "handleMessage: what = 1");
                    Intent intent = new Intent(LeadActivity.this, MainActivity.class);
                    intent.putExtra("pkgName", pkgName);
                    intent.putExtra("clsName", clsName);
                    intent.putExtra("useProxy",bundle.getInt("useProxy"));
                    intent.putExtra("token",bundle.getString("token"));
                    intent.putExtra("image", bundle.getByteArray("image"));
                    intent.putExtra("ip", bundle.getString("ip"));
                    intent.putExtra("port", bundle.getInt("port"));
                    intent.putExtra("username",username);
                    startActivity(intent);
                    break;
                case 2:
                    Toast.makeText(LeadActivity.this, "服务器忙，没有游戏资源可用", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    Log.d(TAG, "handleMessage: msg.what=3");
                    Bundle data = msg.getData();
                    String packageName = data.getString("pkgName");
                    byte[] iconBytes = data.getByteArray("iconBytes");
                    Bitmap bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.length);
                    BitmapDrawable icon = new BitmapDrawable(getResources(), bitmap);
                    for (Map map : list) {
                        if (map.get("pkgName").equals(packageName)) {
                            map.put("appIcon", icon);
                            appList.add(map);
                        }
                    }
                    if (appList.size() == listSize) {
                        progressBar.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
        Map<String, Object> map = list.get(i);
        pkgName = (String) map.get("pkgName");
        clsName = (String) map.get("className");
        Log.d(TAG, "onItemClick: pkgName=" + pkgName);
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

                Messages.App_playGameReq.Builder builder = Messages.App_playGameReq.newBuilder();
                builder.setPackageName(pkgName);
                builder.setClientIp(ip);
                Log.d(TAG, "run: clientIp=" + ip);
                nettyClient.sendMessage(builder.build());
            }
        }).start();
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(LeadActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 10);
            } else {
                getGameList();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 10) {
            if (Build.VERSION.SDK_INT >= 23) {
                if (!Settings.canDrawOverlays(this)) {
                    // SYSTEM_ALERT_WINDOW permission not granted...
                    Toast.makeText(LeadActivity.this, "not granted", Toast.LENGTH_SHORT);
                } else {
                    getGameList();
                }
            }
        }
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
}
