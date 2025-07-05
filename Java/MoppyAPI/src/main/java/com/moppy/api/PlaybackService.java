package com.moppy.api;

import com.moppy.core.events.mapper.MapperCollection;
import com.moppy.core.events.mapper.MIDIEventMapper;
import com.moppy.core.events.postprocessor.MessagePostProcessor;
import com.moppy.core.midi.MoppyMIDIReceiverSender;
import com.moppy.core.midi.MoppyMIDISequencer;
import com.moppy.core.status.StatusBus;
import com.moppy.core.status.StatusConsumer;
import com.moppy.core.status.StatusUpdate;
import com.moppy.core.comms.bridge.NetworkBridge;
import lombok.Getter;

import javax.sound.midi.MidiMessage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PlaybackService implements StatusConsumer {

    @Getter
    private final PlaybackState state = new PlaybackState();

    private final StatusBus statusBus;
    private final NetworkBridge networkBridge;
    private MoppyMIDISequencer sequencer;
    private MoppyMIDIReceiverSender receiverSender;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> currentPlayback = new AtomicReference<>();

    public PlaybackService(StatusBus statusBus, NetworkBridge networkBridge) {
        this.statusBus = statusBus;
        this.networkBridge = networkBridge;
        this.statusBus.registerConsumer(this);
    }

    public synchronized void loadSong(String filePath) throws Exception {
        File midiFile = new File(filePath);
        if (!midiFile.exists()) {
            throw new IllegalArgumentException("MIDI file not found: " + filePath);
        }

        if (!midiFile.getName().toLowerCase().endsWith(".mid") &&
                !midiFile.getName().toLowerCase().endsWith(".midi")) {
            throw new IllegalArgumentException("File must be a MIDI file (.mid or .midi)");
        }

        initializeSequencer();

        sequencer.loadSequence(midiFile);

        state.setFileName(midiFile.getName());
        state.setFilePath(filePath);
        state.setDuration((int) sequencer.getSecondsLength());
        state.setPosition(0);
        state.setPlaybackState(PlaybackState.State.LOADED);

        // Reset tempo to default when loading new song
        state.setTempo(120.0f); // Default tempo, will be updated by status events
    }

    public synchronized void play() throws Exception {
        if (state.getPlaybackState() == PlaybackState.State.UNLOADED) {
            throw new IllegalStateException("No song loaded");
        }

        initializeSequencer();

        if (state.getPlaybackState() == PlaybackState.State.PAUSED) {
            sequencer.play();
        } else {
            CompletableFuture<Void> playbackFuture = CompletableFuture.runAsync(() -> {
                try {
                    sequencer.play();
                } catch (Exception e) {
                    state.setPlaybackState(PlaybackState.State.ERROR);
                    state.setError(e.getMessage());
                }
            });
            currentPlayback.set(playbackFuture);
        }

        state.setPlaybackState(PlaybackState.State.PLAYING);
    }

    public synchronized void pause() {
        if (sequencer != null && state.getPlaybackState() == PlaybackState.State.PLAYING) {
            sequencer.pause();
            state.setPlaybackState(PlaybackState.State.PAUSED);
        }
    }

    public synchronized void stop() {
        if (sequencer != null) {
            sequencer.stop();
            state.setPosition(0);
            state.setPlaybackState(PlaybackState.State.LOADED);

            CompletableFuture<Void> playback = currentPlayback.get();
            if (playback != null) {
                playback.cancel(true);
            }
        }
    }

    public synchronized void setPosition(int seconds) {
        if (sequencer != null && seconds >= 0 && seconds <= state.getDuration()) {
            sequencer.setSecondsPosition(seconds);
            state.setPosition(seconds);
        }
    }

    public synchronized void setTempo(float tempo) {
        if (sequencer != null && tempo > 0) {
            sequencer.setTempo(tempo);
            state.setTempo(tempo);
        }
    }

    public synchronized void setVolume(double volume) {
        if (volume >= 0.0 && volume <= 2.0) {
            state.setVolume(volume);
            // Update the post-processor for volume control
            try {
                initializeSequencer();
            } catch (Exception e) {
                state.setPlaybackState(PlaybackState.State.ERROR);
                state.setError("Failed to reinitialize sequencer: " + e.getMessage());
            }
        }
    }

    public synchronized void setLoop(boolean loop) {
        state.setLoop(loop);
    }

    private void initializeSequencer() throws Exception {
        if (isInitialized.get()) {
            return;
        }

        // Set up event mapping
        MapperCollection<MidiMessage> mappers = new MapperCollection<>();
        MIDIEventMapper defaultMapper = MIDIEventMapper.defaultMapper((byte) 1);
        mappers.addMapper(defaultMapper);

        // Set up post-processor for volume control
        MessagePostProcessor postProcessor = message -> {
            if (message.getMessageCommandByte() == com.moppy.core.comms.MoppyMessage.CommandByte.DEV_PLAYNOTE) {
                byte[] bytes = message.getMessageBytes();
                bytes[6] = (byte) Math.max(0, Math.min(255, bytes[6] * state.getVolume()));
            }
            return message;
        };

        try {
            // Create MIDI system components
            receiverSender = new MoppyMIDIReceiverSender(mappers, postProcessor, networkBridge);
            sequencer = new MoppyMIDISequencer(statusBus, receiverSender);

            isInitialized.set(true);
        } catch (Exception e) {
            state.setPlaybackState(PlaybackState.State.ERROR);
            state.setError("Failed to initialize sequencer: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void receiveUpdate(StatusUpdate update) {
        switch (update.getType()) {
            case SEQUENCE_START:
                state.setPlaybackState(PlaybackState.State.PLAYING);
                break;
            case SEQUENCE_PAUSE:
                state.setPlaybackState(PlaybackState.State.PAUSED);
                break;
            case SEQUENCE_STOPPED:
                state.setPlaybackState(PlaybackState.State.LOADED);
                state.setPosition(0);
                break;
            case SEQUENCE_END:
                if (state.isLoop()) {
                    try {
                        setPosition(0);
                        play();
                    } catch (Exception e) {
                        state.setPlaybackState(PlaybackState.State.ERROR);
                        state.setError(e.getMessage());
                    }
                } else {
                    state.setPlaybackState(PlaybackState.State.LOADED);
                    state.setPosition(0);
                }
                break;
            case SEQUENCE_TEMPO_CHANGE:
                if (update.getData().isPresent()) {
                    float tempo = (Float) update.getData().get();
                    state.setTempo(tempo);
                }
                break;
            default:
                // Ignore other status types
                break;
        }

        // Update position if we have a sequencer
        if (sequencer != null && (state.getPlaybackState() == PlaybackState.State.PLAYING ||
                state.getPlaybackState() == PlaybackState.State.PAUSED)) {
            state.setPosition((int) sequencer.getSecondsPosition());
        }
    }

    public void updateState() {
        if (sequencer == null)
            return;

        state.setPosition((int) sequencer.getSecondsPosition());
        state.setDuration((int) sequencer.getSecondsLength());
    }

    public synchronized void shutdown() throws IOException {
        if (sequencer != null)
            sequencer.close();

        CompletableFuture<Void> playback = currentPlayback.get();
        if (playback != null) {
            playback.cancel(true);
        }
    }
}
