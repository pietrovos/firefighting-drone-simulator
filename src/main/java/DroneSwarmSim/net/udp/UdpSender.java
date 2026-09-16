package DroneSwarmSim.net.udp;

import java.io.IOException;
import java.net.DatagramSocket;

/**
 * A utility class for sending UDP packets to a specified destination.
 * This class wraps a {@link UdpEndpoint} object to manage outbound UDP communications.
 * <p>
 * Instances of this class are associated with a specific destination
 * host and port, to which all UDP packets are sent.
 * <p>
 * This class implements {@link AutoCloseable}, ensuring that resources
 * related to the underlying {@link UdpEndpoint} are properly released
 * when the {@code close()} method is called.
 */
public class UdpSender implements AutoCloseable {
    private final UdpEndpoint endpoint;
    private final String destHost;
    private final int destPort;

    /**
     * Constructs a new {@code UdpSender} instance for sending UDP packets
     * to a specified destination host and port.
     *
     * @param destHost the hostname or IP address of the target destination.
     *                 This is the address to which UDP packets will be sent.
     * @param destPort the port on the target machine to which UDP packets
     *                 will be sent.
     * @throws IOException if an error occurs during the creation of the
     *                     underlying {@link UdpEndpoint}, such as failure
     *                     to create the DatagramSocket.
     */
    public UdpSender(String destHost, int destPort) throws IOException {
        this.endpoint = new UdpEndpoint();
        this.destHost = destHost;
        this.destPort = destPort;
    }

    /**
     * Sends a UDP packet with the specified data to the configured destination host and port.
     * The data is passed to an underlying {@link UdpEndpoint} instance for transmission.
     *
     * @param data the byte array containing the data to be sent.
     *             Cannot be null; must contain the payload intended for the recipient.
     * @throws IOException if an I/O error occurs while sending the packet.
     */
    public void send(byte[] data) throws IOException { endpoint.send(data, destHost, destPort); }

    /**
     * Closes this {@code UdpSender} instance and releases the resources associated
     * with the underlying {@link UdpEndpoint}. The {@code close} method ensures that
     * the {@link DatagramSocket} used for UDP communications is properly closed to
     * prevent resource leaks.
     * <p>
     * This method is typically called when the {@code UdpSender} is no longer needed,
     * such as at the end of its lifecycle or within a try-with-resources block.
     */
    @Override
    public void close() {
        endpoint.close();
    }
}
