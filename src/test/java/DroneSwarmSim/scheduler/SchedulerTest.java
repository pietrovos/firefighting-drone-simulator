package DroneSwarmSim.scheduler;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneRegister;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.ui.StubGUI;
import java.io.File;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    private static final int TEST_PORT = 15600;

    @Test
    void balancesAssignmentsAcrossMultipleDrones() throws Exception {
        File zoneFile = File.createTempFile("zones", ".csv");
        zoneFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(zoneFile)) {
            pw.println("Zone ID,Zone Start,Zone End");
            pw.println("1,(0;0),(300;300)");
            pw.println("2,(1300;700),(1600;1000)");
        }

        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(TEST_PORT, zoneFile.getAbsolutePath(), gui);
        Thread schedulerThread = new Thread(scheduler, "scheduler-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        try (DatagramSocket droneOneSocket = new DatagramSocket(0);
             DatagramSocket droneTwoSocket = new DatagramSocket(0);
             DatagramSocket sender = new DatagramSocket()) {
            droneOneSocket.setSoTimeout(4000);
            droneTwoSocket.setSoTimeout(4000);

            AssignTask firstTask = null;
            int maxAttempts = 5;
            for (int attempt = 0; attempt < maxAttempts && firstTask == null; attempt++) {
                sender.send(packet(PacketBuilder.build(
                        new DroneRegister(1, "localhost", droneOneSocket.getLocalPort()))));
                sender.send(packet(PacketBuilder.build(
                        new DroneRegister(2, "localhost", droneTwoSocket.getLocalPort()))));
                sender.send(packet(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.IDLE, 50, 50, 15.0))));
                sender.send(packet(PacketBuilder.build(
                        new DroneUpdate(2, DroneState.IDLE, 1350, 750, 15.0))));

                sender.send(packet(PacketBuilder.build(new IncidentReport("14:00:00.000", 1,
                        EventType.FIRE_DETECTED, DroneSwarmSim.model.Severity.LOW))));

                try {
                    firstTask = receiveAssignTask(droneOneSocket);
                } catch (SocketTimeoutException e) {
                    if (attempt == maxAttempts - 1) {
                        throw e;
                    }
                    // Retry on timeout to allow scheduler more time to start listening.
                }
            }

            assertNotNull(firstTask);
            assertEquals(1, firstTask.droneId());
            assertEquals(1, firstTask.zoneId());

            sender.send(packet(PacketBuilder.build(new DroneUpdate(1, DroneState.COMPLETED, 100, 100, 0.0))));
            sender.send(packet(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 0, 0, 15.0))));

            sender.send(packet(PacketBuilder.build(new IncidentReport("14:00:00.000", 2, EventType.FIRE_DETECTED,
                    DroneSwarmSim.model.Severity.LOW))));

            AssignTask secondTask = receiveAssignTask(droneTwoSocket);
            assertEquals(2, secondTask.droneId());
            assertEquals(2, secondTask.zoneId());
            assertTrue(gui.allLogs.toString().contains("Drone 2 dispatched to zone 2"));
        }
    }

    private DatagramPacket packet(byte[] payload) {
        return new DatagramPacket(payload, payload.length,
                java.net.InetAddress.getLoopbackAddress(), TEST_PORT);
    }

    private AssignTask receiveAssignTask(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
        return PacketParser.parseAssignTask(data);
    }
}
