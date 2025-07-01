package com.moppy.cli.commands;

import com.moppy.cli.network.CLINetworkManager;
import com.moppy.cli.player.CLIMIDIPlayer;
import com.moppy.cli.util.ConsoleProgressBar;
import com.moppy.core.events.mapper.MapperCollection;
import com.moppy.core.events.mapper.MIDIEventMapper;
import com.moppy.core.events.postprocessor.MessagePostProcessor;
import com.moppy.core.midi.MoppyMIDIReceiverSender;
import com.moppy.core.midi.MoppyMIDISequencer;
import com.moppy.core.status.StatusBus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import javax.sound.midi.MidiMessage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Command to play MIDI files through Moppy devices
 */
@Command(name = "play", description = "Play a MIDI file through Moppy devices", mixinStandardHelpOptions = true)
public class PlayCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "MIDI file to play")
    private File midiFile;

    @Option(names = { "-n", "--network" }, description = "Network type (udp, serial, all)", defaultValue = "all")
    private String networkType;

    @Option(names = { "-p", "--port" }, description = "Serial port (for serial network)")
    private String serialPort;

    @Option(names = { "-d", "--device" }, description = "Target device address", defaultValue = "1")
    private int deviceAddress;

    @Option(names = { "-s", "--sub-devices" }, description = "Number of sub-devices", defaultValue = "8")
    private int subDevices;

    @Option(names = { "-v", "--velocity" }, description = "Velocity multiplier (0.1-2.0)", defaultValue = "1.0")
    private double velocityMultiplier;

    @Option(names = { "-t", "--tempo" }, description = "Tempo multiplier (0.1-5.0)", defaultValue = "1.0")
    private double tempoMultiplier;

    @Option(names = { "--loop" }, description = "Loop the MIDI file")
    private boolean loop;

    @Option(names = { "--no-progress" }, description = "Disable progress bar")
    private boolean noProgress;

    @Option(names = { "--verbose" }, description = "Verbose output")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        if (!midiFile.exists()) {
            System.err.println("Error: MIDI file not found: " + midiFile.getAbsolutePath());
            return 1;
        }

        if (!midiFile.getName().toLowerCase().endsWith(".mid") &&
                !midiFile.getName().toLowerCase().endsWith(".midi")) {
            System.err.println("Warning: File does not have .mid or .midi extension");
        }

        try {
            return playMidiFile();
        } catch (Exception e) {
            System.err.println("Error playing MIDI file: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private int playMidiFile() throws Exception {
        if (verbose) {
            System.out.println("Initializing Moppy system...");
        }

        // Initialize core components
        StatusBus statusBus = new StatusBus();
        CLINetworkManager networkManager = new CLINetworkManager(statusBus, networkType, serialPort);

        // Set up event mapping
        MapperCollection<MidiMessage> mappers = new MapperCollection<>();
        MIDIEventMapper defaultMapper = MIDIEventMapper.defaultMapper((byte) deviceAddress);
        mappers.addMapper(defaultMapper);

        // Set up post-processor for velocity control
        MessagePostProcessor postProcessor = message -> {
            if (message.getMessageCommandByte() == com.moppy.core.comms.MoppyMessage.CommandByte.DEV_PLAYNOTE) {
                byte[] bytes = message.getMessageBytes();
                bytes[6] = (byte) (bytes[6] * velocityMultiplier);
            }
            return message;
        };

        // Create MIDI system components
        MoppyMIDIReceiverSender receiverSender = new MoppyMIDIReceiverSender(mappers, postProcessor,
                networkManager.getPrimaryBridge());
        MoppyMIDISequencer sequencer = new MoppyMIDISequencer(statusBus, receiverSender);

        // Initialize network
        if (verbose) {
            System.out.println("Starting network manager...");
        }
        networkManager.start();

        // Wait a moment for network initialization
        Thread.sleep(1000);

        // Load MIDI file
        if (verbose) {
            System.out.println("Loading MIDI file: " + midiFile.getName());
        }
        sequencer.loadSequence(midiFile);

        // Set tempo
        if (tempoMultiplier != 1.0) {
            // Note: We can't get the current tempo, so we'll just set it based on the
            // default
            // The actual tempo will be updated when the sequence loads
            if (verbose) {
                System.out.printf("Tempo multiplier set to: %.2fx%n", tempoMultiplier);
            }
        }

        // Create progress tracking
        ConsoleProgressBar progressBar = null;
        if (!noProgress) {
            Duration totalDuration = Duration.ofSeconds(sequencer.getSecondsLength());
            progressBar = new ConsoleProgressBar(totalDuration);
        }

        // Set up playback control
        CountDownLatch playbackLatch = new CountDownLatch(1);
        CLIMIDIPlayer player = new CLIMIDIPlayer(sequencer, progressBar, playbackLatch, loop, verbose);
        statusBus.registerConsumer(player);

        // Add tempo adjustment listener
        if (tempoMultiplier != 1.0) {
            statusBus.registerConsumer(update -> {
                if (update.getType() == com.moppy.core.status.StatusType.SEQUENCE_TEMPO_CHANGE) {
                    if (update.getData().isPresent()) {
                        float currentTempo = (Float) update.getData().get();
                        float adjustedTempo = (float) (currentTempo * tempoMultiplier);
                        // Only set if different to avoid loops
                        if (Math.abs(adjustedTempo - currentTempo) > 0.1) {
                            sequencer.setTempo(adjustedTempo);
                            if (verbose) {
                                System.out.printf("Tempo adjusted: %.1f BPM -> %.1f BPM%n",
                                        currentTempo, adjustedTempo);
                            }
                        }
                    }
                }
            });
        }

        // Start playback
        System.out.println("Playing: " + midiFile.getName());
        if (verbose) {
            System.out.printf("Duration: %d:%02d%n",
                    sequencer.getSecondsLength() / 60,
                    sequencer.getSecondsLength() % 60);
            System.out.printf("Device: %d, Sub-devices: 1-%d%n", deviceAddress, subDevices);
            System.out.printf("Velocity multiplier: %.1f%n", velocityMultiplier);
            System.out.printf("Tempo multiplier: %.1f%n", tempoMultiplier);
        }
        System.out.println("Press Ctrl+C to stop");
        System.out.println();

        // Set up shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nStopping playback...");
            sequencer.stop();
            try {
                networkManager.close();
            } catch (IOException e) {
                // Ignore
            }
            playbackLatch.countDown();
        }));

        sequencer.play();

        // Wait for playback to complete or be interrupted
        playbackLatch.await();

        // Cleanup
        sequencer.close();
        networkManager.close();

        if (verbose) {
            System.out.println("Playback completed.");
        }

        return 0;
    }
}
