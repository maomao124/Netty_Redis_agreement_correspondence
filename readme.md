



# 协议设计与解析

## 为什么需要协议？

TCP/IP 中消息传输基于流的方式，没有边界。

协议的目的就是划定消息的边界，制定通信双方要共同遵守的通信规则





## redis协议

### RESP协议

Redis是一个CS架构的软件，通信一般分两步（不包括pipeline和PubSub）

* 客户端（client）向服务端（server）发送一条命令
* 服务端解析并执行命令，返回响应结果给客户端

因此客户端发送命令的格式、服务端响应结果的格式必须有一个规范，这个规范就是通信协议。



在Redis中采用的是**RESP**（Redis Serialization Protocol）协议



* lRedis 1.2版本引入了RESP协议
* lRedis 2.0版本中成为与Redis服务端通信的标准，称为RESP2
* lRedis 6.0版本中，从RESP2升级到了RESP3协议，增加了更多数据类型并且支持6.0的新特性--客户端缓存



lRedis 2.0版本：

在RESP中，通过首字节的字符来区分不同数据类型，常用的数据类型包括5种

* 单行字符串：首字节是 ‘**+**’ ，后面跟上单行字符串，以CRLF（ "**\r\n**" ）结尾。例如返回"OK"： "+OK\r\n"

* 错误（Errors）：首字节是 ‘**-**’ ，与单行字符串格式一样，只是字符串是异常信息，例如："-Error message\r\n"

* 数值：首字节是 ‘**:**’ ，后面跟上数字格式的字符串，以CRLF结尾。例如：":10\r\n"

* 多行字符串：首字节是 ‘**$**’ ，表示二进制安全的字符串，最大支持512MB。例如：$5\r\nhello***\r\n

    * $5的数字5：字符串占用字节大小
    * hello：真正的字符串数据
    * 如果大小为0，则代表空字符串："$0\r\n\r\n"
    * u如果大小为-1，则代表不存在："$-1\r\n"

* 数组：首字节是 ‘*’，后面跟上数组元素个数，再跟上元素，元素数据类型不限

  ```sh
  *3\r\n
  :10\r\n
  $5\r\nhello\r\n
  ```







### 通信客户端

```java
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
```



运行结果：

```sh
2023-03-25  20:48:26.582  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618] REGISTERED
2023-03-25  20:48:26.582  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618] CONNECT: /127.0.0.1:6379
2023-03-25  20:48:26.585  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  客户端启动完成
2023-03-25  20:48:26.585  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] ACTIVE
get name
2023-03-25  20:48:39.203  [input] DEBUG mao.t1.RedisClient:  命令：get name
2023-03-25  20:48:39.204  [input] DEBUG io.netty.util.Recycler:  -Dio.netty.recycler.maxCapacityPerThread: 4096
2023-03-25  20:48:39.205  [input] DEBUG io.netty.util.Recycler:  -Dio.netty.recycler.maxSharedCapacityFactor: 2
2023-03-25  20:48:39.205  [input] DEBUG io.netty.util.Recycler:  -Dio.netty.recycler.linkCapacity: 16
2023-03-25  20:48:39.205  [input] DEBUG io.netty.util.Recycler:  -Dio.netty.recycler.ratio: 8
2023-03-25  20:48:39.207  [input] DEBUG io.netty.buffer.AbstractByteBuf:  -Dio.netty.buffer.checkAccessible: true
2023-03-25  20:48:39.207  [input] DEBUG io.netty.buffer.AbstractByteBuf:  -Dio.netty.buffer.checkBounds: true
2023-03-25  20:48:39.208  [input] DEBUG io.netty.util.ResourceLeakDetectorFactory:  Loaded default ResourceLeakDetector: io.netty.util.ResourceLeakDetector@794284ee
2023-03-25  20:48:39.214  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 33 0d 0a 67 65 74 0d 0a 24 34 0d |*2..$3..get..$4.|
|00000010| 0a 6e 61 6d 65 0d 0a                            |.name..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:39.215  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:48:39.219  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 34B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2d 4e 4f 41 55 54 48 20 41 75 74 68 65 6e 74 69 |-NOAUTH Authenti|
|00000010| 63 61 74 69 6f 6e 20 72 65 71 75 69 72 65 64 2e |cation required.|
|00000020| 0d 0a                                           |..              |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:39.220  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：-NOAUTH Authentication required.

2023-03-25  20:48:39.220  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 34, cap: 1024) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:48:39.220  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:48:39.220  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
auth 123
2023-03-25  20:48:52.341  [input] DEBUG mao.t1.RedisClient:  命令：auth 123
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 34 0d 0a 61 75 74 68 0d 0a 24 33 |*2..$4..auth..$3|
|00000010| 0d 0a 31 32 33 0d 0a                            |..123..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2d 45 52 52 20 69 6e 76 61 6c 69 64 20 70 61 73 |-ERR invalid pas|
|00000010| 73 77 6f 72 64 0d 0a                            |sword..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：-ERR invalid password

2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 23, cap: 1024) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:48:52.342  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
auth 123456
2023-03-25  20:48:58.615  [input] DEBUG mao.t1.RedisClient:  命令：auth 123456
2023-03-25  20:48:58.615  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 26B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 34 0d 0a 61 75 74 68 0d 0a 24 36 |*2..$4..auth..$6|
|00000010| 0d 0a 31 32 33 34 35 36 0d 0a                   |..123456..      |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:58.615  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:48:58.616  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 5B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2b 4f 4b 0d 0a                                  |+OK..           |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:48:58.616  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：+OK

2023-03-25  20:48:58.616  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 5, cap: 512) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:48:58.616  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:48:58.616  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
keys map*
2023-03-25  20:49:08.410  [input] DEBUG mao.t1.RedisClient:  命令：keys map*
2023-03-25  20:49:08.411  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 24B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 34 0d 0a 6b 65 79 73 0d 0a 24 34 |*2..$4..keys..$4|
|00000010| 0d 0a 6d 61 70 2a 0d 0a                         |..map*..        |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:08.411  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:49:08.411  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 36B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 33 0d 0a 24 35 0d 0a 6d 61 70 31 33 0d 0a 24 |*3..$5..map13..$|
|00000010| 34 0d 0a 6d 61 70 31 0d 0a 24 35 0d 0a 6d 61 70 |4..map1..$5..map|
|00000020| 31 34 0d 0a                                     |14..            |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:08.412  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：*3
$5
map13
$4
map1
$5
map14

2023-03-25  20:49:08.412  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 36, cap: 512) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:49:08.412  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:49:08.412  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
get key1
2023-03-25  20:49:32.529  [input] DEBUG mao.t1.RedisClient:  命令：get key1
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 33 0d 0a 67 65 74 0d 0a 24 34 0d |*2..$3..get..$4.|
|00000010| 0a 6b 65 79 31 0d 0a                            |.key1..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 12B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 24 36 0d 0a e4 bd a0 e5 a5 bd 0d 0a             |$6..........    |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：$6
你好

2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 12, cap: 496) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:49:32.530  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
get name
2023-03-25  20:49:55.535  [input] DEBUG mao.t1.RedisClient:  命令：get name
2023-03-25  20:49:55.535  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 33 0d 0a 67 65 74 0d 0a 24 34 0d |*2..$3..get..$4.|
|00000010| 0a 6e 61 6d 65 0d 0a                            |.name..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 5B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 24 2d 31 0d 0a                                  |$-1..           |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：$-1

2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 5, cap: 496) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:49:55.536  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
set name lisi
2023-03-25  20:50:14.100  [input] DEBUG mao.t1.RedisClient:  命令：set name lisi
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 33B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 33 0d 0a 24 33 0d 0a 73 65 74 0d 0a 24 34 0d |*3..$3..set..$4.|
|00000010| 0a 6e 61 6d 65 0d 0a 24 34 0d 0a 6c 69 73 69 0d |.name..$4..lisi.|
|00000020| 0a                                              |.               |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 5B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2b 4f 4b 0d 0a                                  |+OK..           |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：+OK

2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 5, cap: 480) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:50:14.101  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
get name
2023-03-25  20:50:19.587  [input] DEBUG mao.t1.RedisClient:  命令：get name
2023-03-25  20:50:19.588  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] WRITE: 23B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 2a 32 0d 0a 24 33 0d 0a 67 65 74 0d 0a 24 34 0d |*2..$3..get..$4.|
|00000010| 0a 6e 61 6d 65 0d 0a                            |.name..         |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:50:19.588  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] FLUSH
2023-03-25  20:50:19.589  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ: 10B
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 24 34 0d 0a 6c 69 73 69 0d 0a                   |$4..lisi..      |
+--------+-------------------------------------------------+----------------+
2023-03-25  20:50:19.589  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  redis响应结果：$4
lisi

2023-03-25  20:50:19.589  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded inbound message PooledUnsafeDirectByteBuf(ridx: 0, widx: 10, cap: 480) that reached at the tail of the pipeline. Please check your pipeline configuration.
2023-03-25  20:50:19.589  [nioEventLoopGroup-2-1] DEBUG io.netty.channel.DefaultChannelPipeline:  Discarded message pipeline : [LoggingHandler#0, RedisClient$1$1#0, DefaultChannelPipeline$TailContext#0]. Channel : [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379].
2023-03-25  20:50:19.589  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] READ COMPLETE
q
2023-03-25  20:50:27.316  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 - R:/127.0.0.1:6379] CLOSE
2023-03-25  20:50:27.316  [nioEventLoopGroup-2-1] INFO  mao.t1.RedisClient:  关闭客户端
2023-03-25  20:50:27.319  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 ! R:/127.0.0.1:6379] INACTIVE
2023-03-25  20:50:27.319  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 ! R:/127.0.0.1:6379] CLOSE
2023-03-25  20:50:27.319  [nioEventLoopGroup-2-1] DEBUG io.netty.handler.logging.LoggingHandler:  [id: 0xb844e618, L:/127.0.0.1:52290 ! R:/127.0.0.1:6379] UNREGISTERED
2023-03-25  20:50:29.567  [nioEventLoopGroup-2-1] DEBUG io.netty.buffer.PoolThreadCache:  Freed 4 thread-local buffer(s) from thread: nioEventLoopGroup-2-1
```





