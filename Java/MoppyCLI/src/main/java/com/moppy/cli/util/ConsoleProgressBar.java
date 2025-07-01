package com.moppy.cli.util;

import java.time.Duration;

/**
 * Console progress bar for displaying playback progress
 */
public class ConsoleProgressBar {

    private final Duration totalDuration;
    private final int barWidth;
    private Duration currentDuration = Duration.ZERO;
    private boolean finished = false;

    public ConsoleProgressBar(Duration totalDuration) {
        this(totalDuration, 50);
    }

    public ConsoleProgressBar(Duration totalDuration, int barWidth) {
        this.totalDuration = totalDuration;
        this.barWidth = barWidth;
    }

    public void update(Duration currentDuration) {
        if (finished) {
            return;
        }

        this.currentDuration = currentDuration;
        display();
    }

    public void finish() {
        finished = true;
        System.out.println(); // Move to next line
    }

    private void display() {
        double progress = totalDuration.toMillis() > 0 ? (double) currentDuration.toMillis() / totalDuration.toMillis()
                : 0.0;
        progress = Math.min(1.0, Math.max(0.0, progress));

        int filledWidth = (int) (barWidth * progress);

        StringBuilder bar = new StringBuilder();
        bar.append('\r'); // Return to beginning of line
        bar.append("[");

        // Filled portion
        for (int i = 0; i < filledWidth; i++) {
            bar.append("=");
        }

        // Progress indicator
        if (filledWidth < barWidth) {
            bar.append(">");
        }

        // Empty portion
        for (int i = filledWidth + 1; i < barWidth; i++) {
            bar.append(" ");
        }

        bar.append("] ");
        bar.append(String.format("%3.0f%%", progress * 100));
        bar.append(" ");
        bar.append(formatDuration(currentDuration));
        bar.append("/");
        bar.append(formatDuration(totalDuration));

        System.out.print(bar.toString());
        System.out.flush();
    }

    private String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).getSeconds();
        return String.format("%d:%02d", minutes, seconds);
    }
}
