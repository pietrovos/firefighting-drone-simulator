package DroneSwarmSim.net.udp;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * A class that listens for incoming UDP packets on a specified port and processes them using
 * a provided callback handler. The class operates in a background thread to continuously receive
 * packets until explicitly stopped or closed.
 * <p>
 * The class uses an {@link UdpEndpoint} for socket operations and relies on a user-defined
 * {@link PacketHandler} to handle the content of received UDP packets.
 * <p>
 * It is important to call {@link #stop()} or {@link #close()} to release resources and shut down the
 * background receive loop when the receiver is no longer needed.
 */
public class UdpReceiver implements AutoCloseable {
    /**
     * Functional interface for handling incoming UDP packets.
     * This interface is intended to be implemented by classes or lambdas
     * that process raw byte data received by a UDP receiver class.
     */
    @FunctionalInterface
    public interface PacketHandler { void onPacket(byte[] data); }

    private static final Logger LOGGER = Logger.getLogger(UdpReceiver.class.getName());
    private final UdpEndpoint endpoint;
    private final PacketHandler handler;
    private volatile boolean running;

    /**
     * Constructs a new {@code UdpReceiver} instance that listens for incoming UDP packets
     * on a specified local port and processes them using the provided {@link PacketHandler}.
     *
     * @param localPort the local port on which the receiver will listen for incoming UDP packets.
     *                  Must be a valid port number (0-65535).
     * @param handler   the {@link PacketHandler} implementation used to process received packets.
     *                  This must not be {@code null}.
     * @throws IOException if the underlying {@link UdpEndpoint} could not be created or if the port
     *                     could not be bound.
     */
    public UdpReceiver(int localPort, PacketHandler handler) throws IOException {
        this.endpoint = new UdpEndpoint(localPort);
        this.handler  = handler;
    }

    /**
     * Starts the UDP packet receiver by initializing and launching a separate daemon thread.
     * The thread executes the {@code receiveLoop} method, which listens for incoming packets
     * and processes them using the provided {@link PacketHandler}.
     * <p>
     * This method is non-blocking and enables the continuous reception of UDP packets in the background.
     * The receiver will continue running until {@link #stop()} or {@link #close()} is called.
     * <p>
     * It is safe to call {@code start()} multiple times, but later calls will have no effect
     * if the receiver thread is already running.
     *
     * @throws IllegalStateException if the receiver has been closed and cannot be restarted.
     */
    public void start() {
        running = true;
        Thread receiverThread = new Thread(this::receiveLoop, "udp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    /**
     * Stops the UDP packet receiver by signalling the background receiver thread to terminate
     * and releasing resources associated with the underlying {@link UdpEndpoint}.
     * <p>
     * This method sets the internal {@code running} flag to {@code false}, which causes the
     * {@code receiveLoop} to exit, and then proceeds to close the {@link UdpEndpoint} instance.
     * <p>
     * Once this method is called, the receiver cannot continue processing incoming packets.
     * To restart the receiver, a new instance should be created or the {@link #start()} method
     * should be invoked again (if supported).
     * <p>
     * This method is safe to call multiple times; however, later calls after the receiver
     * has already stopped or been closed will have no additional effect.
     */
    public void stop() {
        running = false;
        endpoint.close();
    }

    /**
     * Listens for incoming UDP packets in a continuous loop and processes them using the associated
     * {@link PacketHandler}. This method is executed within a separate thread to ensure non-blocking
     * behaviour and enable asynchronous packet processing.
     * <p>
     * The {@code receiveLoop} method operates as follows:
     * - Continuously invokes the {@code endpoint.receive()} method to retrieve incoming UDP packets.
     * - Delegates the processing of each packet's payload to the {@code handler.onPacket(byte[] data)} method.
     * - Handles {@link IOException} instances that may occur during packet reception, logging errors
     *   to the logger as long as the receiver is still running.
     * <p>
     * This loop continues to execute until the {@code running} flag is set to {@code false}, typically
     * controlled by methods like {@code stop()} or {@code close()}. If an exception occurs while the
     * receiver is still active, the error message is logged, but the loop remains operational.
     * <p>
     * Note: This method is intended to be invoked only by the {@link UdpReceiver} class and should
     * not be directly accessed from external code.
     */
    private void receiveLoop() {
        while (running) {
            try {
                byte[] data = endpoint.receive();
                handler.onPacket(data);
            } catch (IOException e) { if (running) { LOGGER.log(Level.SEVERE, "[UdpReceiver] Receive error: {0}", e.getMessage()); } }
        }
    }

    /**
     * Releases resources associated with the {@code UdpReceiver} instance and stops the background
     * receiver thread if it is running. This method ensures that the receiver is properly shut down.
     *
     * <ul>
     *   <li>Calls the {@link #stop()} method to terminate the background thread and signal the
     *   receiver to cease operation.</li>
     *   <li>Additional calls to this method after the instance has been closed will have no effect.</li>
     * </ul>
     *
     * This method is part of the {@link AutoCloseable} interface, allowing the receiver to be used in
     * try-with-resources blocks for proper resource management.
     */
    @Override
    public void close() { stop(); }
}
