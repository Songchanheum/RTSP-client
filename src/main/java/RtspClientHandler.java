
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.apache.log4j.Logger;

import java.util.List;

import static io.netty.handler.codec.rtsp.RtspHeaderNames.CSEQ;
import static io.netty.handler.codec.rtsp.RtspHeaderNames.SESSION;
import static io.netty.handler.codec.rtsp.RtspResponseStatuses.CONTINUE;
import static io.netty.handler.codec.rtsp.RtspVersions.RTSP_1_0;

public class RtspClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(RtspClientHandler.class);

    private List<HttpRequest> requestDeque;
    private List<HttpResponse> responseDeque;
    private RtspClient client;

    public RtspClientHandler(List<HttpRequest> requestDeque, List<HttpResponse> responseDeque, RtspClient client) {
        this.requestDeque = requestDeque;
        this.responseDeque = responseDeque;
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpResponse) {
            //get response,send
            this.responseDeque.add((HttpResponse) msg);
        } else if (msg instanceof HttpRequest) {
            this.requestDeque.add((HttpRequest) msg);

            HttpRequest req = (HttpRequest) msg;
            int sequence = req.headers().getInt(CSEQ);
            String sessionId = req.headers().getAsString(SESSION);

            if (HttpUtil.is100ContinueExpected(req)) {
                ctx.write(new DefaultFullHttpResponse(RTSP_1_0, CONTINUE));
            }

            FullHttpResponse response = new DefaultFullHttpResponse(RTSP_1_0, HttpResponseStatus.OK);
            response.headers().setInt(CSEQ, sequence);
            if (sessionId != null && !sessionId.isEmpty()) {
                response.headers().set(SESSION, sessionId);
            }
            client.send(response);  //send response

            //    ctx.write(response);

        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        ctx.channel().close();
        cause.printStackTrace();
    }

}
