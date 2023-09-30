package fr.loghub.logback.appender;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import org.zeromq.SocketType;

import ch.qos.logback.core.OutputStreamAppender;
import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Method;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import lombok.Getter;
import lombok.Setter;

public class ZMQAppender<E> extends OutputStreamAppender<E> implements Logger {

    private class ZMQOutputStream extends OutputStream {
        @Override
        public void write(byte[] content) {
            if (!publisher.send(content)) {
                addWarn("Log event lost");
            }
        }

        @Override
        public void write(byte[] content, int off, int len) {
            write(Arrays.copyOfRange(content, off, Math.min(content.length, off + len)));
        }

        @Override
        public void flush() {
            // noop
        }

        @Override
        public void close() {
            publisher.close();
        }

        @Override
        public void write(int b) {
            if (! publisher.send(new byte[]{(byte)b})) {
                addError("Log event lost");
            }
        }
    }

    private SocketType type = Publisher.DEFAULT_TYPE;
    private Method method = Publisher.DEFAULT_METHOD;
    @Getter @Setter
    private String endpoint = null;
    @Getter @Setter
    private int hwm = -1;
    @Getter @Setter
    private int sndHwm = Publisher.DEFAULT_SND_HWM;
    @Getter @Setter
    private int rcvHwm = Publisher.DEFAULT_RCV_HWM;
    @Getter @Setter
    private long maxMsgSize = Publisher.DEFAULT_MAX_MSGSIZE;
    @Getter @Setter
    private int linger = Publisher.DEFAULT_LINGER;
    @Getter @Setter
    public String peerPublicKey;
    @Getter @Setter
    public String privateKeyFile;
    @Getter @Setter
    public String publicKey;
    @Getter @Setter
    private boolean autoCreate = true;

    private Publisher publisher;

    /**
     * Define the ØMQ socket type. Current allowed value are PUB or PUSH.
     *
     * @param type
     */
    public void setType(String type) {
        try {
            this.type = SocketType.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [PUSH, PUB]" + ", using default ZeroMQ socket type, PUSH by default.";
            addError(msg, e);
        }
    }

    /**
     * @return the ØMQ socket type.
     */
    public String getType() {
        return type.toString();
    }

    /**
     * The <b>method</b> define the connection method for the ØMQ socket. It can take the value
     * connect or bind, it's case-insensitive.
     * @param method
     */
    public void setMethod(String method) {
        try {
            this.method = Method.valueOf(method.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [connect, bind]" + ", using default ZeroMQ socket type, connect by default.";
            addError(msg, e);
        }
    }

    @Override
    public void start() {
        if (endpoint == null) {
            addError("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }
        ZMQConfiguration.ZMQConfigurationBuilder<ZMQAppender<E>> builder = ZMQConfiguration.builder();
        ZMQConfiguration<ZMQAppender<E>> config = builder.context(this)
                                                         .endpoint(endpoint)
                                                         .type(type)
                                                         .method(method)
                                                         .maxMsgSize(maxMsgSize)
                                                         .sendHwm(hwm != -1 ? hwm : sndHwm)
                                                         .recvHwm(hwm != -1 ? hwm : rcvHwm)
                                                         .linger(linger)
                                                         .peerPublicKey(peerPublicKey)
                                                         .privateKeyFile(privateKeyFile)
                                                         .publicKey(publicKey)
                                                         .autoCreate(autoCreate)
                                                         .build();

        publisher = Publisher.asynchronous("Log4JZMQPublishingThread", this, config);
        setOutputStream(new ZMQOutputStream());
        super.start();
    }

    @Override
    public void warn(Supplier<String> message, Throwable t) {
        addWarn(message.toString(), t);
    }

    @Override
    public void error(Supplier<String> message, Throwable t) {
        addError(message.toString(), t);
    }

}
