package com.moppy.cli.commands;

import com.moppy.cli.network.CLINetworkManager;
import com.moppy.core.device.DeviceDescriptor;
import com.moppy.core.status.StatusBus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command to list and manage Moppy devices on the network
 */
@Command(name = "devices", description = "List and manage Moppy devices on the network", mixinStandardHelpOptions = true)
public class DevicesCommand implements Callable<Integer> {

    @Option(names = { "-n", "--network" }, description = "Network type (udp, serial, all)", defaultValue = "all")
    private String networkType;

    @Option(names = { "-p", "--port" }, description = "Serial port (for serial network)")
    private String serialPort;

    @Option(names = { "-t", "--timeout" }, description = "Discovery timeout in seconds", defaultValue = "10")
    private int timeout;

    @Option(names = { "-v", "--verbose" }, description = "Verbose output")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        System.out.println("Discovering Moppy devices...");

        StatusBus statusBus = new StatusBus();
        CLINetworkManager networkManager = new CLINetworkManager(statusBus, networkType, serialPort);

        try {
            if (verbose) {
                System.out.println("Starting network manager...");
            }
            networkManager.start();

            // Wait for device discovery
            System.out.printf("Waiting %d seconds for device responses...%n", timeout);
            Thread.sleep(timeout * 1000);

            // Display discovered devices
            List<DeviceDescriptor> devices = networkManager.getDiscoveredDevices();

            if (devices.isEmpty()) {
                System.out.println("No devices discovered.");
                System.out.println();
                System.out.println("Troubleshooting tips:");
                System.out.println("- Ensure Moppy devices are powered on and connected");
                System.out.println("- Check network connectivity (UDP multicast or serial connections)");
                System.out.println("- Try increasing the timeout with --timeout option");
                System.out.println("- Use --verbose for more detailed output");
                return 1;
            }

            System.out.println();
            System.out.printf("Found %d device(s):%n", devices.size());
            System.out.println("┌─────────┬──────────────────────┬─────────────────┐");
            System.out.println("│ Device  │ Network Address      │ Sub-Addresses   │");
            System.out.println("├─────────┼──────────────────────┼─────────────────┤");

            for (DeviceDescriptor device : devices) {
                System.out.printf("│ %-7d │ %-20s │ %-15s │%n",
                        device.getDeviceAddress() & 0xFF,
                        truncate(device.getNetworkAddress(), 20),
                        String.format("%d-%d",
                                device.getMinSubAddress() & 0xFF,
                                device.getMaxSubAddress() & 0xFF));
            }

            System.out.println("└─────────┴──────────────────────┴─────────────────┘");

            if (verbose) {
                System.out.println();
                System.out.println("Detailed device information:");
                for (DeviceDescriptor device : devices) {
                    System.out.printf("Device %d:%n", device.getDeviceAddress() & 0xFF);
                    System.out.printf("  Network: %s%n", device.getNetworkAddress());
                    System.out.printf("  Sub-addresses: %d to %d (%d total)%n",
                            device.getMinSubAddress() & 0xFF,
                            device.getMaxSubAddress() & 0xFF,
                            (device.getMaxSubAddress() & 0xFF) - (device.getMinSubAddress() & 0xFF) + 1);
                    System.out.println();
                }
            }

        } finally {
            networkManager.close();
        }

        return 0;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
}
