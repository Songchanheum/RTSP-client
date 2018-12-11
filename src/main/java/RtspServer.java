
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.rtsp.*;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class RtspServer {
    public static class RtspServerHandler extends ChannelInboundHandlerAdapter {
        Channel channel;

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        private void sendAnswer(ChannelHandlerContext ctx, DefaultHttpRequest req, FullHttpResponse rep) {
            final String cseq = req.headers().get(RtspHeaderNames.CSEQ);
            if (cseq != null) {
                rep.headers().add(RtspHeaderNames.CSEQ, cseq);
            }
            final String session = req.headers().get(RtspHeaderNames.SESSION);
            if (session != null) {
                rep.headers().add(RtspHeaderNames.SESSION, session);
            }
            if (!HttpUtil.isKeepAlive(req)) {
                ctx.write(rep).addListener(ChannelFutureListener.CLOSE);
            } else {
                rep.headers().set(RtspHeaderNames.CONNECTION, RtspHeaderValues.KEEP_ALIVE);
                System.out.println("---->" + req.toString());
                ctx.write(rep);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof DefaultHttpRequest) {
                DefaultHttpRequest req = (DefaultHttpRequest) msg;
                System.out.println(req.toString());
                FullHttpResponse rep = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.NOT_FOUND);
                if (req.method() == RtspMethods.OPTIONS) {
                    System.out.println("options");
                    rep.setStatus(RtspResponseStatuses.OK);
                    rep.headers().add(RtspHeaderValues.PUBLIC, "DESCRIBE, SETUP, PLAY, TEARDOWN");
                    sendAnswer(ctx, req, rep);
                } else if (req.method() == RtspMethods.SET_PARAMETER) {

                    rep.setStatus(RtspResponseStatuses.OK);
                    sendAnswer(ctx, req, rep);
                    DefaultHttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.ANNOUNCE, "rtsp://123.a");
                    request.headers().add(RtspHeaderNames.CSEQ, 1)
                            .add(RtspHeaderNames.SESSION, 2);
                    RtspServer.send(request, channel);
                    //channel.writeAndFlush(request);
                } else if (req.method() == RtspMethods.DESCRIBE) {

                    ByteBuf buf = Unpooled.copiedBuffer("c=IN IP4 10.5.110.117\r\nm=video 5004 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\n", CharsetUtil.UTF_8);
                    rep.setStatus(RtspResponseStatuses.OK);
                    rep.headers().add(RtspHeaderNames.CONTENT_TYPE, "application/sdp");
                    rep.headers().add(RtspHeaderNames.CONTENT_LENGTH, buf.writerIndex());
                    rep.content().writeBytes(buf);
                    sendAnswer(ctx, req, rep);
                } else if (req.method() == RtspMethods.SETUP) {
                    rep.setStatus(RtspResponseStatuses.OK);
                    String session = String.format("%08x", (int) (Math.random() * 65536));
                    rep.headers().add(RtspHeaderNames.SESSION, session);
                    rep.headers().add(RtspHeaderNames.TRANSPORT, "RTP/AVP;unicast;client_port=5004-5005");
                    sendAnswer(ctx, req, rep);
                } else if (req.method() == RtspMethods.PLAY) {
                    rep.setStatus(RtspResponseStatuses.OK);

                    sendAnswer(ctx, req, rep);
                } else if (req.method() == RtspMethods.TEARDOWN) {
                    rep.setStatus(RtspResponseStatuses.OK);
                    rep.headers().add(RtspHeaderNames.CSEQ, req.headers().get(RtspHeaderNames.CSEQ));
                    rep.headers().add(RtspHeaderNames.SESSION, req.headers().get(RtspHeaderNames.SESSION));
                    ctx.write(rep).addListener(ChannelFutureListener.CLOSE);
                } else {
                    System.err.println("Not managed :" + req.method());
                    ctx.write(rep).addListener(ChannelFutureListener.CLOSE);
                }
            } else if (msg instanceof DefaultHttpResponse) {
                DefaultHttpResponse response = (DefaultHttpResponse) msg;
                System.out.println("..................response");
                System.out.println(response.toString());
                DefaultHttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.ANNOUNCE, "rtsp://10.0.75.2");
                request.headers().add(RtspHeaderNames.CSEQ, response.headers().get(RtspHeaderNames.CSEQ))
                        .add(RtspHeaderNames.SESSION, request.headers().get(RtspHeaderNames.SESSION));
                ctx.writeAndFlush(request);
            } else {

            }
        }
    }

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final RtspServerHandler handler = new RtspServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new RtspDecoder(), new StringEncoder());
                    p.addLast(handler);
                }
            });

            Channel ch = b.bind(8557).sync().channel();
            handler.channel = ch;
            System.err.println("Connect to rtsp://127.0.0.1:8557");
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void send(DefaultHttpRequest request, Channel channel) {
        EmbeddedChannel ch1 = new EmbeddedChannel(new RtspEncoder());
        ch1.writeOutbound(request);
        ByteBuf buf = ch1.readOutbound();
        String actual = buf.toString(CharsetUtil.UTF_8);
        channel.writeAndFlush(actual);
    }
}