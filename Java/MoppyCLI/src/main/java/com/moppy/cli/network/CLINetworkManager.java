package com.moppy.cli.network;

import com.moppy.core.comms.MoppyMessage;
import com.moppy.core.comms.NetworkMessageConsumer;
import com.moppy.core.comms.NetworkReceivedMessage;
import com.moppy.core.comms.bridge.BridgeSerial;
import com.moppy.core.comms.bridge.BridgeUDP;
import com.moppy.core.comms.bridge.MultiBridge;
import com.moppy.core.comms.bridge.NetworkBridge;
import com.moppy.core.device.DeviceDescriptor;
import com.moppy.core.status.StatusBus;
import com.moppy.core.status.StatusUpdate;

import java.io.Closeable;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI-specific network manager for managing Moppy device connections
 */
public class CLINetworkManager implements NetworkMessageConsumer, Closeable {

    private static final Logger logger = Logger.getLogger(CLINetworkManager.class.getName());

    private final StatusBus statusBus;
    private final MultiBridge multiBridge;
    private final List<NetworkBridge> bridges;
    private final ConcurrentHashMap<String, DeviceDescriptor> discoveredDevices;
    private final ScheduledExecutorService scheduler;

    private boolean started = false;

    public CLINetworkManager(StatusBus statusBus, String networkType, String serialPort) {
        this.statusBus = statusBus;
        this.multiBridge = new MultiBridge();
        this.bridges = new ArrayList<>();
        this.discoveredDevices = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);

        multiBridge.registerMessageReceiver(this);

        initializeBridges(networkType, serialPort);
    }

    private void initializeBridges(String networkType, String serialPort) {
        switch (networkType.toLowerCase()) {
            case "udp":
                initializeUDPBridge();
                break;
            case "serial":
                initializeSerialBridge(serialPort);
                break;
            case "all":
            default:
                initializeUDPBridge();
                initializeSerialBridges();
                break;
        }

        // Add all bridges to the MultiBridge
        for (NetworkBridge bridge : bridges) {
            multiBridge.addBridge(bridge);
        }
    }

    private void initializeUDPBridge() {
        try {
            BridgeUDP udpBridge = new BridgeUDP();
            bridges.add(udpBridge);
            logger.info("UDP bridge initialized: " + udpBridge.getNetworkIdentifier());
        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "Failed to initialize UDP bridge", e);
        }
    }

    private void initializeSerialBridge(String specificPort) {
        if (specificPort != null && !specificPort.isEmpty()) {
            try {
                BridgeSerial serialBridge = new BridgeSerial(specificPort);
                bridges.add(serialBridge);
                logger.info("Serial bridge initialized: " + specificPort);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to initialize serial bridge for port: " + specificPort, e);
            }
        } else {
            initializeSerialBridges();
        }
    }

    private void initializeSerialBridges() {
        List<String> availableSerials = BridgeSerial.getAvailableSerials();
        for (String serial : availableSerials) {
            try {
                BridgeSerial serialBridge = new BridgeSerial(serial);
                bridges.add(serialBridge);
                logger.info("Serial bridge initialized: " + serial);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to initialize serial bridge for: " + serial, e);
            }
        }
    }

    public void start() throws IOException {
        if (started) {
            return;
        }

        // Connect all bridges
        for (NetworkBridge bridge : bridges) {
            try {
                bridge.connect();
                logger.info("Connected bridge: " + bridge.getNetworkIdentifier());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to connect bridge: " + bridge.getNetworkIdentifier(), e);
            }
        }

        // Start device discovery
        startDeviceDiscovery();

        started = true;
    }

    private void startDeviceDiscovery() {
        // Send ping every 5 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                multiBridge.sendMessage(MoppyMessage.SYS_PING);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to send ping", e);
            }
        }, 1, 5, TimeUnit.SECONDS);

        // Clean up stale devices every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            long cutoffTime = System.currentTimeMillis() - 30000; // 30 seconds
            discoveredDevices.entrySet().removeIf(entry -> {
                // This is a simplified check - in a real implementation you'd track last seen
                // time
                return false; // For now, don't remove devices
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    public NetworkBridge getPrimaryBridge() {
        return multiBridge;
    }

    public List<DeviceDescriptor> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }

    @Override
    public void acceptNetworkMessage(NetworkReceivedMessage networkMessage) {
        if (networkMessage.getMessageCommandByte() == MoppyMessage.CommandByte.SYS_PONG) {
            handleDevicePong(networkMessage);
        }
    }

    private void handleDevicePong(NetworkReceivedMessage message) {
        try {
            byte[] payload = message.getMessageCommandPayload();
            if (payload.length >= 3) {
                byte deviceAddress = payload[0];
                byte minSubAddress = payload[1];
                byte maxSubAddress = payload[2];

                DeviceDescriptor device = DeviceDescriptor.builder()
                        .networkAddress(message.getRemoteIdentifier())
                        .deviceAddress(deviceAddress)
                        .minSubAddress(minSubAddress)
                        .maxSubAddress(maxSubAddress)
                        .build();

                String deviceKey = message.getRemoteIdentifier() + ":" + (deviceAddress & 0xFF);
                DeviceDescriptor existing = discoveredDevices.put(deviceKey, device);

                if (existing == null) {
                    logger.info(String.format("Device discovered: %d on %s (sub-addresses: %d-%d)",
                            deviceAddress & 0xFF, message.getRemoteIdentifier(),
                            minSubAddress & 0xFF, maxSubAddress & 0xFF));

                    // Notify status bus
                    statusBus.receiveUpdate(StatusUpdate.NET_DEVICES_CHANGED);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing device pong", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        for (NetworkBridge bridge : bridges) {
            try {
                bridge.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing bridge: " + bridge.getNetworkIdentifier(), e);
            }
        }
    }
}
