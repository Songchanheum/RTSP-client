
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;
import org.testng.Assert;

import java.util.ArrayList;

import static io.netty.handler.codec.rtsp.RtspHeaderNames.CSEQ;
import static io.netty.handler.codec.rtsp.RtspHeaderNames.SESSION;

public class RtspClient {
    private int seq = 1;
    private String host;
    private int port;
    private EventLoopGroup workerGroup;
    private SocketChannel socketChannel;
    private ArrayList<HttpRequest> requestDeque;
    private ArrayList<HttpResponse> responseDeque;


    public HttpResponse getResponse(int cseq) {
        return responseDeque.stream().filter(p -> p.headers().getInt(CSEQ) == cseq).findFirst().orElse(null);
    }

    public HttpResponse getResponse(int cseq, String sessionId) {
        return responseDeque.stream().filter(p -> p.headers().getInt(CSEQ) == cseq && p.headers().get(SESSION) == sessionId).findFirst().orElse(null);
    }

    public ArrayList<HttpRequest> getRequest() {
        return requestDeque;
    }


    private static final Logger LOGGER = Logger.getLogger(RtspClient.class);


    public void connect(String rtspHost, int rtspPort) {
        host = rtspHost;
        port = rtspPort;
        requestDeque = new ArrayList<>();
        responseDeque = new ArrayList<>();

        RtspClient client = this;
        workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();
                pipeline.addLast(new StringEncoder());  //................
                pipeline.addLast(new RtspDecoder());
                pipeline.addLast(new RtspClientHandler(requestDeque, responseDeque, client));
            }
        });
        // Start the client.
        try {
            ChannelFuture f = bootstrap.connect(host, port).sync();
            socketChannel = (SocketChannel) f.channel();
            if (f.isSuccess() && f.channel().isActive()) {
            } else {
                f.cancel(true);
                f.channel().close();
                Assert.fail("channel failed or inactive");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

    }


    public void disConnect() {
        try {
            // Wait until the connection is closed.
            socketChannel.close();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public void send(HttpMessage request) {
        EmbeddedChannel ch = new EmbeddedChannel(new RtspEncoder());
        ch.writeOutbound(request);
        ByteBuf buf = ch.readOutbound();
        String actual = buf.toString(CharsetUtil.UTF_8);
        socketChannel.writeAndFlush(actual);
        System.out.println(actual);
    }

}
