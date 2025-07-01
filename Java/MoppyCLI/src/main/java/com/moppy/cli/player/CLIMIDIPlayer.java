package com.moppy.cli.player;

import com.moppy.cli.util.ConsoleProgressBar;
import com.moppy.core.midi.MoppyMIDISequencer;
import com.moppy.core.status.StatusConsumer;
import com.moppy.core.status.StatusUpdate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CLI MIDI player that handles playback status and progress updates
 */
public class CLIMIDIPlayer implements StatusConsumer {

    private final MoppyMIDISequencer sequencer;
    private final ConsoleProgressBar progressBar;
    private final CountDownLatch playbackLatch;
    private final boolean loop;
    private final boolean verbose;
    private final ScheduledExecutorService progressUpdater;

    private boolean isPlaying = false;

    public CLIMIDIPlayer(MoppyMIDISequencer sequencer, ConsoleProgressBar progressBar,
            CountDownLatch playbackLatch, boolean loop, boolean verbose) {
        this.sequencer = sequencer;
        this.progressBar = progressBar;
        this.playbackLatch = playbackLatch;
        this.loop = loop;
        this.verbose = verbose;
        this.progressUpdater = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void receiveUpdate(StatusUpdate update) {
        switch (update.getType()) {
            case SEQUENCE_START:
                handleSequenceStart();
                break;
            case SEQUENCE_PAUSE:
                handleSequencePause();
                break;
            case SEQUENCE_STOPPED:
                handleSequenceStop();
                break;
            case SEQUENCE_END:
                handleSequenceEnd();
                break;
            default:
                if (verbose) {
                    System.err.println("Status: " + update.getType());
                }
                break;
        }
    }

    private void handleSequenceStart() {
        isPlaying = true;
        if (verbose) {
            System.out.println("Playback started");
        }

        // Start progress updates
        if (progressBar != null) {
            progressUpdater.scheduleAtFixedRate(this::updateProgress, 0, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void handleSequencePause() {
        isPlaying = false;
        if (verbose) {
            System.out.println("Playback paused");
        }
        stopProgressUpdates();
    }

    private void handleSequenceStop() {
        isPlaying = false;
        if (verbose) {
            System.out.println("Playback stopped");
        }
        stopProgressUpdates();

        if (progressBar != null) {
            progressBar.update(Duration.ZERO);
            progressBar.finish();
        }

        playbackLatch.countDown();
    }

    private void handleSequenceEnd() {
        if (loop && isPlaying) {
            if (verbose) {
                System.out.println("Looping...");
            }
            // Reset position and continue playing
            sequencer.setSecondsPosition(0);
            sequencer.play();
        } else {
            handleSequenceStop();
        }
    }

    private void updateProgress() {
        if (isPlaying && progressBar != null) {
            Duration currentTime = Duration.ofSeconds(sequencer.getSecondsPosition());
            progressBar.update(currentTime);
        }
    }

    private void stopProgressUpdates() {
        if (progressUpdater != null && !progressUpdater.isShutdown()) {
            // Don't shutdown the executor, just stop scheduling new tasks
            // The existing scheduled task will check isPlaying
        }
    }

    public void shutdown() {
        if (progressUpdater != null && !progressUpdater.isShutdown()) {
            progressUpdater.shutdown();
            try {
                if (!progressUpdater.awaitTermination(1, TimeUnit.SECONDS)) {
                    progressUpdater.shutdownNow();
                }
            } catch (InterruptedException e) {
                progressUpdater.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
