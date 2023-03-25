package mao.t2;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;

/**
 * Project name(项目名称)：Netty_Redis协议通信
 * Package(包名): mao.t2
 * Class(类名): Server
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2023/3/25
 * Time(创建时间)： 20:54
 * Version(版本): 1.0
 * Description(描述)： http协议
 */

@Slf4j
public class Server
{
    public static void main(String[] args)
    {
        NioEventLoopGroup boss = new NioEventLoopGroup(2);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        ChannelFuture channelFuture = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>()
                {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception
                    {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG))
                                .addLast(new HttpServerCodec())
                                .addLast(new SimpleChannelInboundHandler<HttpRequest>()
                                {
                                    /**
                                     * 处理请求行和请求头
                                     *
                                     * @param ctx         ctx
                                     * @param httpRequest http请求
                                     * @throws Exception 异常
                                     */
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest)
                                            throws Exception
                                    {
                                        log.debug("请求的uri：" + httpRequest.uri());
                                        //请求头
                                        HttpHeaders headers = httpRequest.headers();
                                        headers.forEach(new Consumer<Map.Entry<String, String>>()
                                        {
                                            @Override
                                            public void accept(Map.Entry<String, String> stringStringEntry)
                                            {
                                                String key = stringStringEntry.getKey();
                                                String value = stringStringEntry.getValue();
                                                log.debug(key + " ---> " + value);
                                            }
                                        });
                                        DefaultFullHttpResponse httpResponse = new
                                                DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                HttpResponseStatus.OK);
                                        String respMsg = "<h1>Hello, world!</h1>";
                                        byte[] bytes = respMsg.getBytes(StandardCharsets.UTF_8);
                                        httpResponse.headers().setInt(CONTENT_LENGTH, bytes.length);
                                        httpResponse.headers().set("content-type", "text/html;charset=utf-8");
                                        httpResponse.content().writeBytes(bytes);
                                        //写回响应
                                        ctx.writeAndFlush(httpResponse);
                                    }
                                })
                                .addLast(new SimpleChannelInboundHandler<HttpContent>()
                                {
                                    /**
                                     * 处理请求体
                                     *
                                     * @param ctx ctx
                                     * @param httpContent HttpContent
                                     * @throws Exception 异常
                                     */
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, HttpContent httpContent)
                                            throws Exception
                                    {
                                        ByteBuf byteBuf = httpContent.content();
                                        String s = byteBuf.toString(StandardCharsets.UTF_8);
                                        log.debug("请求体：" + s);
                                    }
                                });
                    }
                }).bind(8080);
        Channel channel = channelFuture.channel();

        channelFuture.addListener(new GenericFutureListener<Future<? super Void>>()
        {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception
            {
                if (future.isSuccess())
                {
                    log.info("服务启动完成");
                }
                else
                {
                    log.warn("服务启动失败：" + future.cause().getMessage());
                }
            }
        });

        channel.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>()
        {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception
            {
                log.info("关闭客户端");
                boss.shutdownGracefully();
                worker.shutdownGracefully();
            }
        });
    }
}
