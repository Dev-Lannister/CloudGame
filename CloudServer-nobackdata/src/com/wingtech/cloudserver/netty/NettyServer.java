package com.wingtech.cloudserver.netty;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.google.protobuf.Message;
import com.wingtech.cloudserver.MainActivity;
import com.wingtech.cloudserver.service.CheckIsHomeService;
import com.wingtech.cloudserver.service.ScreenCastService;
import com.wingtech.gameserver.network.Network;
import com.wingtech.gameserver.network.Session;
import com.wingtech.gameserver.network.handler.HandlerManager;
import com.wingtech.gameserver.network.netty.ChannelUtils;
import com.wingtech.gameserver.network.netty.MessageHandler;
import com.wingtech.gameserver.network.netty.NettySession;
import com.wingtech.gameserver.network.netty.ProtobufDecoder;
import com.wingtech.gameserver.network.netty.ProtobufEncoder;
import com.wingtech.gameserver.network.netty.TimeoutHandler;
import com.wingtech.gameserver.protocols.Messages;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

public class NettyServer implements Runnable, Network {

    private static final String TAG = "NettyServer";

    private int port;
    private ChannelFuture f;

    public Map<Channel, NettySession> sessions = new java.util.concurrent.ConcurrentHashMap<Channel, NettySession>();
    private boolean isFirst = true;
    public static String currentPkgName;
    public static String currentClsName;

    public NettyServer(int port) {
        this.port = port;
        HandlerManager.regist(ClientHandler.class);
    }

    public void close() {
        if (f != null) {
            f.channel().close();
        }
    }

    @Override
    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap(); // (2)
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // (3)
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            //添加会话
                            NettySession session = new NettySession(ch);
                            ChannelUtils.addChannelSession(ch, session);

                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ChannelInboundHandlerAdapter() {

                                @Override
                                public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                                    super.channelUnregistered(ctx);

                                    Channel channel = ctx.channel();
                                    NettySession session = (NettySession) ChannelUtils.getSessionBy(channel);
                                    onSessionClosed(session);

                                    sessions.remove(channel);
                                    isFirst = true;
                                    final Timer timer = new Timer();
                                    TimerTask task = new TimerTask() {
                                        @Override
                                        public void run() {
                                            Messages.Game_AppDisconnected.Builder builder = Messages.Game_AppDisconnected.newBuilder();
                                            MainActivity.mainActivity.nettyClient.sendMessage(builder.build());
                                            Log.d(TAG, "Game_AppDisconnected: ");
                                            Log.d(TAG, "channelUnregistered: killPkg=" + currentPkgName);
                                            if (currentPkgName != null) {
                                                ActivityManager am = (ActivityManager) MainActivity.mainActivity.getSystemService(Context.ACTIVITY_SERVICE);
                                                am.forceStopPackage(currentPkgName);
                                            }
                                            MainActivity.mainActivity.videoServer.setConfigData(null, 0, 0);
                                            ScreenCastService.isFirstFrame = true;
                                            Log.d(TAG, "stopCheckIsHomeService: ");
                                            Intent checkIsHomeIntent = new Intent(MainActivity.mainActivity, CheckIsHomeService.class);
                                            MainActivity.mainActivity.stopService(checkIsHomeIntent);
                                            CheckIsHomeService.isNotHome = false;
                                            MainActivity.mainActivity.stopAll();
                                            if (MainActivity.mainActivity.wakeLock.isHeld()) {
                                                MainActivity.mainActivity.wakeLock.release();
                                            }
                                            timer.cancel();
                                        }
                                    };
                                    if (!ClientHandler.nomalExit) {
                                        timer.schedule(task, 60000);
                                        // add backup operation


                                        // add backup operation
                                    }
                                    ClientHandler.nomalExit = false;
                                    ClientHandler.isReady = false;
                                }

                            });
                            pipeline.addLast("idleStateHandler", new IdleStateHandler(15, 5, 0));

                            //Decoder
                            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576 * 2, 0, 4, 0, 4));
                            pipeline.addLast("protobufDecoder", new ProtobufDecoder());

                            //Handler
                            pipeline.addLast("messageHandler", new MessageHandler());

                            //Encoder
                            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                            pipeline.addLast("protobufEncoder", new ProtobufEncoder());

                            //timeout和心跳
                            pipeline.addLast("timeoutHandler", new TimeoutHandler(false));

                            sessions.put(ch, session);
                            if (sessions.size() > 0) {
                                Log.d(TAG, "initChannel: 消息链接建立");
                                if (!MainActivity.mainActivity.isRecording) {
                                    Log.d(TAG, "initChannel: 开启录音录屏");
                                    /*while (!ClientHandler.isReady) {
                                        Log.d(TAG, "initChannel: readying");
                                    }*/ //lijiwei.modify 20180502
                                    MainActivity.mainActivity.startAll();
                                }
                                android.os.Message message = android.os.Message.obtain();
                                message.what = 0;
                                MainActivity.mainActivity.mHandler.sendMessageDelayed(message, 2000);
                            }
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)          // (5)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 2)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024 * 2)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024 * 2)
                    .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024 * 2); // (6)


            // Bind and start to accept incoming connections.
            f = b.bind(port).sync(); // (7)

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void sendMessage(Message message) throws IOException {
        for (Channel ch : sessions.keySet()) {
            if (ch.isActive()) {
                ch.writeAndFlush(message);
            }
        }
    }

    @Override
    public long getLastActiveTime() {
        return 0;
    }

    @Override
    public void sendHeartBeat() throws IOException {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return f != null && f.isSuccess();
    }

    @Override
    public void setLastActiveTime(long time) {
    }

    @Override
    public long getLastSendTime() {
        return 0;
    }

    @Override
    public void start() {
        new Thread(this).start();
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setName(String name) {
    }

    public void onSessionClosed(Session session) {

    }
}
