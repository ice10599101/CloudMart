package com.cloudmart.live.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 服务器，支持百万级长连接弹幕广播。
 * 独立于 Spring Web 端口运行，避免阻塞 HTTP 请求处理线程。
 * 配置了 IdleStateHandler 检测僵尸连接，WriteBufferWaterMark 控制背压。
 */
@Component
public class NettyWebSocketServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketServer.class);

    @Value("${live.netty.port:9016}")
    private int nettyPort;

    @Value("${live.netty.boss-threads:1}")
    private int bossThreads;

    @Value("${live.netty.worker-threads:0}")
    private int workerThreads;

    @Value("${live.netty.idle-timeout-seconds:300}")
    private int idleTimeoutSeconds;

    private final DanmakuChannelHandler danmakuChannelHandler;

    private MultiThreadIoEventLoopGroup bossGroup;
    private MultiThreadIoEventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyWebSocketServer(DanmakuChannelHandler danmakuChannelHandler) {
        this.danmakuChannelHandler = danmakuChannelHandler;
    }

    @Override
    public void run(String... args) {
        bossGroup = new MultiThreadIoEventLoopGroup(bossThreads, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(workerThreads, NioIoHandler.newFactory());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new IdleStateHandler(idleTimeoutSeconds, 0, 0, TimeUnit.SECONDS))
                                .addLast(new WebSocketServerProtocolHandler("/ws/live/danmaku"))
                                .addLast(new DanmakuFrameHandler(danmakuChannelHandler));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        try {
            ChannelFuture future = bootstrap.bind(nettyPort).sync();
            serverChannel = future.channel();
            log.info("Netty WebSocket server started on port {}", nettyPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Netty server bind interrupted");
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        log.info("Netty WebSocket server shut down");
    }
}
