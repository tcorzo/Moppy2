package com.moppy.api;

import lombok.Data;

@Data
public class PlaybackState {

    public enum State {
        UNLOADED,
        LOADED,
        PLAYING,
        PAUSED,
        ERROR
    }

    private State playbackState = State.UNLOADED;
    private String fileName = "";
    private String filePath = "";
    private int duration = 0; // in seconds
    private int position = 0; // in seconds
    private float tempo = 120.0f; // BPM
    private double volume = 1.0; // 0.0 to 2.0
    private boolean loop = false;
    private String error = "";

    public double getProgress() {
        if (duration == 0) {
            return 0.0;
        }
        return (double) position / duration;
    }

    public String getFormattedDuration() {
        return formatTime(duration);
    }

    public String getFormattedPosition() {
        return formatTime(position);
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}
