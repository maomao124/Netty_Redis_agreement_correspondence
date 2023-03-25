package mao.t1;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Project name(项目名称)：Netty_Redis协议通信
 * Package(包名): mao.t1
 * Class(类名): RedisClient
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2023/3/25
 * Time(创建时间)： 20:14
 * Version(版本): 1.0
 * Description(描述)： resp2.0协议通信
 */

@Slf4j
public class RedisClient
{
    public static void main(String[] args)
    {
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup();
        ChannelFuture channelFuture = new Bootstrap()
                .group(nioEventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>()
                {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception
                    {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter()
                        {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
                            {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                String s = byteBuf.toString(StandardCharsets.UTF_8);
                                log.info("redis响应结果：" + s);
                                super.channelRead(ctx, msg);
                            }

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception
                            {
                                super.channelActive(ctx);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception
                            {
                                ctx.close();
                            }
                        });
                    }
                })
                .connect(new InetSocketAddress("127.0.0.1", 6379));
        Channel channel = channelFuture.channel();

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Scanner input = new Scanner(System.in);
                while (true)
                {
                    String cmd = input.nextLine();
                    if ("q".equals(cmd))
                    {
                        channel.close();
                        return;
                    }
                    log.debug("命令：" + cmd);
                    write(channel, cmd);
                }
            }
        }, "input");

        channelFuture.addListener(new GenericFutureListener<Future<? super Void>>()
        {
            /**
             * 操作完成(启动完成)
             *
             * @param future future
             * @throws Exception 异常
             */
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception
            {
                if (future.isSuccess())
                {
                    log.info("客户端启动完成");
                    thread.start();
                }
                else
                {
                    log.warn("客户端启动失败：" + future.cause().getMessage());
                }
            }
        });
        channel.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>()
        {
            /**
             * 操作完成(关闭)
             *
             * @param future future
             * @throws Exception 异常
             */
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception
            {
                log.info("关闭客户端");
                nioEventLoopGroup.shutdownGracefully();
            }
        });
    }

    /**
     * 向redis发送命令
     *
     * @param channel Channel
     * @param cmd     命令字符串，中间用空格分开，比如： "set name 张三"
     */
    private static void write(Channel channel, String cmd)
    {
        //判断是否为空
        if (cmd == null)
        {
            return;
        }
        //按空格分割
        String[] split = cmd.split(" ");
        //得到数组长度
        int length = split.length;
        //开辟ByteBuf
        ByteBuf buffer = channel.alloc().buffer();
        buffer.writeBytes(("*" + length).getBytes(StandardCharsets.UTF_8));
        buffer.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        //循环
        for (String s : split)
        {
            buffer.writeBytes(("$" + s.length()).getBytes(StandardCharsets.UTF_8));
            buffer.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
            buffer.writeBytes(s.getBytes(StandardCharsets.UTF_8));
            buffer.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        //发送
        channel.writeAndFlush(buffer);
    }
}
