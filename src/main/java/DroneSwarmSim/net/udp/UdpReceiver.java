package DroneSwarmSim.net.udp;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Receives UDP packets on a background thread until closed. */
public class UdpReceiver implements AutoCloseable {
    @FunctionalInterface
    public interface PacketHandler { void onPacket(byte[] data); }

    private static final Logger LOGGER = Logger.getLogger(UdpReceiver.class.getName());
    private final UdpEndpoint endpoint;
    private final PacketHandler handler;
    private volatile boolean running;
    private boolean closed;

    public UdpReceiver(int localPort, PacketHandler handler) throws IOException {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.endpoint = new UdpEndpoint(localPort);
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("Receiver is closed");
        if (running) return;
        running = true;
        Thread receiverThread = new Thread(this::receiveLoop, "udp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public synchronized void stop() {
        if (closed) return;
        running = false;
        closed = true;
        endpoint.close();
    }

    private void receiveLoop() {
        while (running) {
            try {
                byte[] data = endpoint.receive();
                handler.onPacket(data);
            } catch (IOException e) { if (running) { LOGGER.log(Level.SEVERE, "[UdpReceiver] Receive error: {0}", e.getMessage()); } }
        }
    }

    @Override
    public void close() { stop(); }
}
