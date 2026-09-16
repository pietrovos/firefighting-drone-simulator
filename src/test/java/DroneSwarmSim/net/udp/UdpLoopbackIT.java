package DroneSwarmSim.net.udp;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;

/**
 * Integration tests for UDP loopback communication.
 * <p>
 * This test class verifies the functionality of UDP communication via
 * loopback using a sender and receiver. It specifically ensures that data
 * integrity is maintained, messages are properly echoed, and multiple packets
 * are delivered correctly.
 * <p>
 * Tests in this class include:
 * <p>
 * 1. Echoing a short payload to verify basic UDP communication.
 * 2. Echoing an empty payload to confirm correct handling of zero-yEnd data.
 * 3. Echoing a large payload to check for data integrity with larger data sizes.
 * 4. Sending and receiving multiple packets to validate delivery order and
 *    completeness across multiple transmissions.
 * <p>
 * Each test runs within a defined timeout to avoid infinite hangs in case of
 * failure. The receiver and sender are initialized using the specified base
 * port, ensuring tests are isolated by using unique port numbers for each.
 */
@DisplayName("UDP Loopback Integration Tests")
class UdpLoopbackIT {

    private static final int PORT_BASE = 15700;

    /**
     * Tests the ability to send and receive a short payload over UDP using the {@code UdpSender} and {@code UdpReceiver}.
     * The test verifies that the receiver correctly receives the payload sent by the sender within the expected
     * timeout period.
     * <p>
     * The method performs the following operations:
     * 1. Creates a short payload to send.
     * 2. Sets up a {@link CountDownLatch} to synchronize and ensure timely receipt of the payload.
     * 3. Initializes a {@code UdpReceiver} that listens for incoming UDP packets on a specific port and
     *    adds received packets to a thread-safe list.
     * 4. Starts the {@code UdpReceiver} and sends the short payload using a {@code UdpSender}.
     * 5. Waits for the payload to be received within 3 seconds and asserts that it has been received.
     * 6. Verifies that the received payload matches the payload sent byte-for-byte.
     * <p>
     * An assertion is triggered in the following cases:
     * - If the payload is not received within 3 seconds.
     * - If the received payload does not match the payload sent.
     * <p>
     * @throws Exception if any unexpected error occurs during transmission or reception.
     */
    @Test
    @Timeout(5)
    void testEchoShortPayload() throws Exception {
        byte[] payload = "HELLO UDP".getBytes();
        CountDownLatch latch = new CountDownLatch(1);
        List<byte[]> received = new CopyOnWriteArrayList<>();

        // Verifies short UDP payload echoes correctly within timeout
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE, data -> {
            received.add(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE)) {
                tx.send(payload);
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS), "Payload should arrive within 3 s");
        }

        assertArrayEquals(payload, received.get(0), "Received payload must be identical to sent payload");
    }

    /**
     * Tests the transmission and reception of an empty payload over UDP using the {@code UdpSender} and {@code UdpReceiver}.
     * <p>
     * This method verifies that an empty payload (a zero-yEnd byte array) can be sent and correctly received by the
     * receiver. The following steps are performed:
     * <p>
     * 1. Creates an empty payload with a yEnd of zero bytes.
     * 2. Sets up a {@link CountDownLatch} to synchronize and ensure the payload is received within the timeout period.
     * 3. Initializes a {@code UdpReceiver} to listen for incoming packets on a specific port. The payloads it receives are
     *    added to a thread-safe list.
     * 4. Starts the {@code UdpReceiver}, then sends the empty payload using a {@code UdpSender}.
     * 5. Waits for the receiver to confirm payload reception within the specified timeout duration.
     * 6. Asserts that the received payload has a yEnd of zero, confirming that an empty payload is properly transmitted
     *    and received.
     * <p>
     * Assertions are triggered in the following cases:
     * - If the payload is not received within 3 seconds.
     * - If the received payload is not empty (i.e., its yEnd is not zero).
     *
     * @throws Exception if any unexpected error occurs during transmission or reception.
     */
    @Test
    @Timeout(5)
    void testEchoEmptyPayload() throws Exception {
        byte[] payload = new byte[0];
        CountDownLatch latch = new CountDownLatch(1);
        List<byte[]> received = new CopyOnWriteArrayList<>();

        // Sends empty payload to receiver; verifies zero-byte arrival
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 1, data -> {
            received.add(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 1)) {
                tx.send(payload);
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        assertEquals(0, received.get(0).length, "Empty payload should arrive with zero bytes");
    }

    /**
     * Tests the transmission and reception of a large payload over UDP using the {@code UdpSender} and {@code UdpReceiver}.
     * <p>
     * This method verifies the ability of the system to send and receive a payload of 2 KB in size without corruption.
     * The following operations are performed:
     * <p>
     * 1. Creates a large payload of 2048 bytes with sequentially assigned values.
     * 2. Configures a {@link CountDownLatch} to synchronize and validate the timely receipt of the payload.
     * 3. Initializes a {@code UdpReceiver} to listen for incoming packets on a specific port and add received payloads
     *    to a thread-safe list.
     * 4. Starts the {@code UdpReceiver} and sends the large payload using a {@code UdpSender}.
     * 5. Verifies that the payload is received within the timeout duration of 3 seconds.
     * 6. Compares the received payload against the payload sent to ensure data integrity.
     * <p>
     * Assertions are triggered in the following cases:
     * - If the payload is not received within 3 seconds.
     * - If the received payload does not match the payload sent byte-for-byte.
     * <p>
     * @throws Exception if any unexpected error occurs during the transmission or reception process.
     */
    @Test
    @Timeout(5)
    void testEchoLargePayload() throws Exception {
        byte[] payload = new byte[2048];
        for (int i = 0; i < payload.length; i++) { payload[i] = (byte) (i & 0xFF); }

        CountDownLatch latch = new CountDownLatch(1);
        List<byte[]> received = new CopyOnWriteArrayList<>();

        // Receives large payload via UDP; timely verifies uncorrupted arrival
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 2, data -> {
            received.add(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 2)) {
                tx.send(payload);
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS), "2 KB payload should arrive within 3 s");
        }

        assertArrayEquals(payload, received.get(0), "Large payload must arrive without corruption");
    }

    /**
     * Tests the delivery of multiple UDP packets using the {@code UdpSender} and {@code UdpReceiver}.
     * <p>
     * This method verifies that the receiver can successfully receive multiple packets sent in quick succession from
     * the sender. The test ensures all packets are received within the designated timeout period and without omissions.
     * <p>
     * The following operations are performed:
     * 1. Sets up a {@link CountDownLatch} initialized with the number of packets to be sent.
     * 2. Configures a {@code UdpReceiver} to listen for incoming packets and record received messages in a thread-safe list.
     * 3. Starts the {@code UdpReceiver}.
     * 4. Sends the specified number of packets sequentially using a {@code UdpSender}.
     * 5. Waits for confirmation that all packets have been received within 3 seconds.
     * 6. Asserts the total number of received packets matches the number of sent packets.
     * <p>
     * Assertions are triggered in the following cases:
     * - If fewer than the expected number of packets are received within the specified timeout duration.
     * - If the total count of received packets does not match the transmitted count.
     *
     * @throws Exception if an unexpected error occurs during transmission or reception.
     */
    @Test
    @Timeout(5)
    void testMultiplePacketsDelivered() throws Exception {
        int count = 5;
        CountDownLatch latch = new CountDownLatch(count);
        List<String> msgs = new CopyOnWriteArrayList<>();

        // Verifies all sent packets arrive within timeout
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 3, data -> {
            msgs.add(new String(data));
            latch.countDown();
        })) {
            rx.start();
            // Sends multiple test packets to the receiver
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 3)) {
                for (int i = 0; i < count; i++) {
                    tx.send(("msg-" + i).getBytes());
                }
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS),
                    "All " + count + " packets should arrive. Got: " + msgs.size());
        }

        assertEquals(count, msgs.size(), "Exactly " + count + " messages expected");
    }
}
