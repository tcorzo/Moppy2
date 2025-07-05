package com.moppy.api;

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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for managing connecting, disconnecting, and status for API server's
 * network connection(s)
 * Based on the NetworkManager from MoppyControlGUI but adapted for the API
 * server
 */
public class NetworkManager implements NetworkMessageConsumer, Closeable {

    private static final Logger logger = Logger.getLogger(NetworkManager.class.getName());

    /**
     * StatusBus for updating local components about network changes.
     */
    private final StatusBus statusBus;

    private final MultiBridge multiBridge = new MultiBridge();
    private final HashMap<String, NetworkBridge<?>> networkBridges = new HashMap<>();
    private final ConcurrentHashMap<DeviceDescriptor, Instant> recentlySeenDevices = new ConcurrentHashMap<>();

    private Thread pingerThread;
    private volatile boolean isStarted = false;

    public NetworkManager(StatusBus statusBus) {
        this.statusBus = statusBus;
        initializeNetworkBridges();
        multiBridge.registerMessageReceiver(this); // Register to receive network messages to look for pongs
    }

    /**
     * Initialize all available network bridges (UDP and Serial)
     */
    private void initializeNetworkBridges() {
        // Initialize UDP bridge
        try {
            BridgeUDP udpBridge = new BridgeUDP();
            networkBridges.put(udpBridge.getNetworkIdentifier(), udpBridge);
            logger.info("UDP bridge initialized: " + udpBridge.getNetworkIdentifier());
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, "Failed to initialize UDP bridge", ex);
        }

        // Initialize Serial bridges for all available ports
        List<String> availableSerials = BridgeSerial.getAvailableSerials();
        for (String serial : availableSerials) {
            try {
                BridgeSerial serialBridge = new BridgeSerial(serial);
                networkBridges.put(serialBridge.getNetworkIdentifier(), serialBridge);
                logger.info("Serial bridge initialized: " + serial);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to initialize serial bridge for: " + serial, ex);
            }
        }
    }

    /**
     * Start the network manager and begin device discovery
     */
    public synchronized void start() {
        if (isStarted) {
            return;
        }

        // Auto-connect all available bridges
        for (Map.Entry<String, NetworkBridge<?>> entry : networkBridges.entrySet()) {
            try {
                connectBridge(entry.getKey(), null);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to auto-connect bridge: " + entry.getKey(), ex);
            }
        }

        // Create and start device discovery thread
        NetworkPinger pinger = new NetworkPinger(multiBridge, recentlySeenDevices, statusBus);
        pingerThread = new Thread(pinger, "NetworkPinger");
        pingerThread.setDaemon(true);
        pingerThread.start();

        isStarted = true;
        logger.info("Network manager started with " + networkBridges.size() + " bridges");
    }

    /**
     * Stop the network manager and cleanup resources
     */
    @Override
    public synchronized void close() throws IOException {
        if (!isStarted) {
            return;
        }

        // Stop the pinger thread
        if (pingerThread != null) {
            pingerThread.interrupt();
            try {
                pingerThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pingerThread = null;
        }

        // Close all bridges
        for (String bridgeId : networkBridges.keySet()) {
            try {
                closeBridge(bridgeId);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error closing bridge: " + bridgeId, ex);
            }
        }

        isStarted = false;
        logger.info("Network manager stopped");
    }

    /**
     * For the purposes of being able to send or register to receive messages,
     * returns
     * the primary NetworkBridge being managed.
     */
    public NetworkBridge<?> getPrimaryBridge() {
        return multiBridge;
    }

    // The NetworkManager is primarily concerned with receiving pong messages from
    // the network
    // so that it can update its current list of devices.
    @Override
    public void acceptNetworkMessage(NetworkReceivedMessage networkMessage) {

        // Whenever we receive a pong from one of the networks, update the device in
        // recentlySeenDevices with the time it was last seen
        if (networkMessage.isSystemMessage()
                && networkMessage.getMessageCommandByte() == MoppyMessage.CommandByte.SYS_PONG) {
            byte[] payload = networkMessage.getMessageCommandPayload();
            if (payload.length >= 3) {
                // Create DeviceDescriptor using builder (IDE may show error but compiles fine)
                DeviceDescriptor dd = DeviceDescriptor.builder()
                        .networkAddress(String.format("%s - %s", networkMessage.getNetworkIdentifier(),
                                networkMessage.getRemoteIdentifier()))
                        .deviceAddress(payload[0])
                        .minSubAddress(payload[1])
                        .maxSubAddress(payload[2])
                        .build();

                Instant lastSeen = recentlySeenDevices.put(dd, Instant.now());
                // If this device had never been seen before, we have a new device! Let everyone
                // know!
                if (lastSeen == null) {
                    logger.info(String.format("New device discovered: %d on %s (sub-addresses: %d-%d)",
                            payload[0] & 0xFF, networkMessage.getRemoteIdentifier(),
                            payload[1] & 0xFF, payload[2] & 0xFF));
                    statusBus.receiveUpdate(StatusUpdate.NET_DEVICES_CHANGED);
                }
            }
        }
    }

    /**
     * Returns a Map of unique network bridge identifiers and the network bridge
     * 
     * @return
     */
    public Map<String, NetworkBridge<?>> getAvailableNetworkBridges() {
        return new HashMap<>(networkBridges);
    }

    /**
     * Returns a Set of DeviceDescriptors for devices for who we recently received a
     * pong.
     * 
     * @return
     */
    public Set<DeviceDescriptor> getRecentlySeenDevices() {
        return recentlySeenDevices.keySet();
    }

    /**
     * Connect a specific bridge
     */
    public void connectBridge(String bridgeIdentifier, Object connectionOption) throws IOException {
        NetworkBridge<?> bridge = networkBridges.get(bridgeIdentifier);
        if (bridge == null) {
            throw new IllegalArgumentException("Unknown bridge identifier: " + bridgeIdentifier);
        }

        try {
            if (connectionOption != null) {
                @SuppressWarnings("unchecked")
                NetworkBridge<Object> objectBridge = (NetworkBridge<Object>) bridge;
                objectBridge.connect(connectionOption);
            } else {
                bridge.connect();
            }
            bridge.registerMessageReceiver(multiBridge);
            multiBridge.addBridge(bridge);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Connected bridge: " + bridgeIdentifier);
            }
        } finally {
            statusBus.receiveUpdate(StatusUpdate.NET_STATUS_CHANGED);
        }
    }

    /**
     * Close a specific bridge
     */
    public void closeBridge(String bridgeIdentifier) throws IOException {
        NetworkBridge<?> bridge = networkBridges.get(bridgeIdentifier);
        if (bridge == null) {
            return; // Bridge doesn't exist, nothing to close
        }

        try {
            multiBridge.removeBridge(bridge);
            bridge.deregisterMessageReceiver(multiBridge);
            bridge.close();
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Disconnected bridge: " + bridgeIdentifier);
            }
        } finally {
            statusBus.receiveUpdate(StatusUpdate.NET_STATUS_CHANGED);
        }
    }

    /**
     * Get the count of connected bridges
     */
    public int getConnectedBridgeCount() {
        return (int) networkBridges.values().stream().filter(NetworkBridge::isConnected).count();
    }

    /**
     * Get the count of discovered devices
     */
    public int getDiscoveredDeviceCount() {
        return recentlySeenDevices.size();
    }

    /**
     * Check if the network manager is started
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Occasionally pings the network looking for new devices. Also culls
     * devices not seen for a while from the list.
     */
    private static class NetworkPinger implements Runnable {

        private final NetworkBridge<?> bridgeToPing;
        private final ConcurrentHashMap<DeviceDescriptor, Instant> recentlySeenDevices;
        private final StatusBus statusBus; // Status bus for alerting to device removals

        public NetworkPinger(NetworkBridge<?> bridgeToPing,
                ConcurrentHashMap<DeviceDescriptor, Instant> recentlySeenDevices, StatusBus statusBus) {
            this.bridgeToPing = bridgeToPing;
            this.recentlySeenDevices = recentlySeenDevices;
            this.statusBus = statusBus;
        }

        @Override
        public void run() {

            while (!Thread.interrupted()) {
                // Send a ping to discover devices
                try {
                    bridgeToPing.sendMessage(MoppyMessage.SYS_PING);
                } catch (IOException ex) {
                    // If for some reason we can't send the message, just log and carry on
                    // (hopefully whatever's wrong
                    // will resolve itself again, but we don't want to kill the pinger)
                    Logger.getLogger(NetworkManager.class.getName()).log(Level.WARNING, "Failed to send ping", ex);
                }

                // Wait a bit for responses and because we don't need to ping constantly
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    break;
                }

                // Cull devices not seen for 10 seconds (a bit longer than GUI to account for
                // API usage patterns)
                boolean devicesCulled = recentlySeenDevices.values().removeIf(lastSeen -> Duration.ofSeconds(10)
                        .minus(Duration.between(lastSeen, Instant.now()))
                        .isNegative());
                if (devicesCulled) {
                    Logger.getLogger(NetworkManager.class.getName()).log(Level.FINE,
                            "Removed stale devices from discovery list");
                    statusBus.receiveUpdate(StatusUpdate.NET_DEVICES_CHANGED);
                }
            }
        }
    }
}
