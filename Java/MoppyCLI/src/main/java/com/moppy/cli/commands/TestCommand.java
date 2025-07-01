package com.moppy.cli.commands;

import com.moppy.cli.network.CLINetworkManager;
import com.moppy.core.comms.MoppyMessage;
import com.moppy.core.comms.MoppyMessageFactory;
import com.moppy.core.status.StatusBus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Command to test network connectivity and device communication
 */
@Command(name = "test", description = "Test network connectivity and device communication", mixinStandardHelpOptions = true)
public class TestCommand implements Callable<Integer> {

    @Option(names = { "-n", "--network" }, description = "Network type (udp, serial, all)", defaultValue = "all")
    private String networkType;

    @Option(names = { "-p", "--port" }, description = "Serial port (for serial network)")
    private String serialPort;

    @Option(names = { "-d", "--device" }, description = "Target device address for test", defaultValue = "1")
    private int deviceAddress;

    @Option(names = { "-s", "--sub-device" }, description = "Target sub-device address", defaultValue = "1")
    private int subAddress;

    @Option(names = { "-v", "--verbose" }, description = "Verbose output")
    private boolean verbose;

    @Option(names = { "--note" }, description = "Test note number (MIDI note)", defaultValue = "60")
    private int noteNumber;

    @Option(names = { "--duration" }, description = "Test note duration in seconds", defaultValue = "2")
    private int duration;

    @Override
    public Integer call() throws Exception {
        System.out.println("Testing Moppy network connectivity...");

        StatusBus statusBus = new StatusBus();
        CLINetworkManager networkManager = new CLINetworkManager(statusBus, networkType, serialPort);

        try {
            if (verbose) {
                System.out.println("Starting network manager...");
            }
            networkManager.start();

            // Test 1: Network ping
            System.out.println("\n1. Testing network ping...");
            testNetworkPing(networkManager);

            // Test 2: Device reset
            System.out.printf("\n2. Testing device reset (device %d)...%n", deviceAddress);
            testDeviceReset(networkManager);

            // Test 3: Play test note
            System.out.printf("\n3. Testing note playback (device %d, sub-device %d, note %d)...%n",
                    deviceAddress, subAddress, noteNumber);
            testNotePlayback(networkManager);

            System.out.println("\nTest completed successfully!");

        } finally {
            networkManager.close();
        }

        return 0;
    }

    private void testNetworkPing(CLINetworkManager networkManager) throws IOException, InterruptedException {
        // Send ping and wait for response
        networkManager.getPrimaryBridge().sendMessage(MoppyMessage.SYS_PING);
        System.out.println("Ping sent, waiting for responses...");

        Thread.sleep(2000); // Wait 2 seconds for pong responses

        int deviceCount = networkManager.getDiscoveredDevices().size();
        if (deviceCount > 0) {
            System.out.printf("✓ Received responses from %d device(s)%n", deviceCount);
        } else {
            System.out.println("⚠ No device responses received");
        }
    }

    private void testDeviceReset(CLINetworkManager networkManager) throws IOException, InterruptedException {
        MoppyMessage resetMessage = MoppyMessageFactory.deviceReset((byte) deviceAddress);
        networkManager.getPrimaryBridge().sendMessage(resetMessage);
        System.out.println("✓ Device reset command sent");

        Thread.sleep(500); // Brief pause
    }

    private void testNotePlayback(CLINetworkManager networkManager) throws IOException, InterruptedException {
        // Play note
        MoppyMessage playNote = MoppyMessageFactory.devicePlayNote(
                (byte) deviceAddress,
                (byte) subAddress,
                (byte) noteNumber,
                (byte) 100 // velocity
        );

        networkManager.getPrimaryBridge().sendMessage(playNote);
        System.out.printf("✓ Note ON sent (note %d)%n", noteNumber);

        // Wait for specified duration
        Thread.sleep(duration * 1000);

        // Stop note
        MoppyMessage stopNote = MoppyMessageFactory.deviceStopNote(
                (byte) deviceAddress,
                (byte) subAddress,
                (byte) noteNumber);

        networkManager.getPrimaryBridge().sendMessage(stopNote);
        System.out.printf("✓ Note OFF sent (note %d)%n", noteNumber);

        if (verbose) {
            System.out.printf("If you have a properly configured device at address %d, sub-address %d,\\n",
                    deviceAddress, subAddress);
            System.out.println("you should have heard a sound for the specified duration.");
        }
    }
}
