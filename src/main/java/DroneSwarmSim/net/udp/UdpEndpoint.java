package DroneSwarmSim.net.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
/**
 * A simple abstraction for sending and receiving UDP packets.
 * This class provides a wrapper around the Java {@link DatagramSocket}
 * to simplify UDP communications, including sending data to a specific destination
 * and receiving packets.
 * <p>
 * This class can operate in two modes:
 * - Outbound-only, where it is used solely for sending packets.
 * - Bidirectional (sending and receiving) when bound to a local port.
 * <p>
 * Instances of this class must be explicitly closed to release the underlying socket.
 */
public class UdpEndpoint implements AutoCloseable {
    private static final int MAX_PACKET_SIZE = 4096;
    private final DatagramSocket socket;

    /**
     * Constructs a new {@code UdpEndpoint} for sending and receiving UDP packets.
     * This constructor creates an unbound {@link DatagramSocket}, which can be used
     * for outbound communication or explicitly bound to a local port and address
     * if needed.
     *
     * @throws IOException if the underlying {@link DatagramSocket} could not be created.
     */
    public UdpEndpoint() throws IOException { this.socket = new DatagramSocket(); }

    /**
     * Constructs a new {@code UdpEndpoint} bound to the specified local port for
     * sending and receiving UDP packets.
     *
     * @param localPort the local port to bind the {@link DatagramSocket} to.
     * @throws IOException if the underlying {@link DatagramSocket} could not be created
     *         or if the port could not be bound.
     */
    public UdpEndpoint(int localPort) throws IOException { this.socket = new DatagramSocket(localPort); }

    /**
     * Sends a UDP packet containing the given data to the specified host and port.
     *
     * @param data the byte array containing the data to be sent.
     * @param host the hostname or IP address of the destination.
     * @param port the port number on the destination machine.
     * @throws IOException if an I/O error occurs while sending the packet.
     */
    public void send(byte[] data, String host, int port) throws IOException {
        InetAddress address = InetAddress.getByName(host);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    /**
     * Receives a UDP packet and returns its payload as a byte array.
     * This method blocks until a packet is received or an I/O error occurs.
     * The size of the buffer used to store the payload is determined by {@code MAX_PACKET_SIZE}.
     *
     * @return A byte array containing the payload of the received UDP packet.
     * @throws IOException if an I/O error occurs while receiving the packet.
     */
    public byte[] receive() throws IOException {
        byte[] buf = new byte[MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
        return data;
    }

    /**
     * Closes the underlying {@link DatagramSocket} if it is not already closed.
     * This method ensures the proper release of system resources associated with the socket.
     */
    @Override
    public void close() { if (!socket.isClosed()) { socket.close(); } }
}
