package com.moppy.cli;

import com.moppy.cli.commands.PlayCommand;
import com.moppy.cli.commands.DevicesCommand;
import com.moppy.cli.commands.TestCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main CLI application for MoppyLib
 */
@Command(name = "moppy", description = "Command-line interface for Moppy musical floppy drive controller", version = "MoppyCLI 2.0", mixinStandardHelpOptions = true, subcommands = {
        PlayCommand.class,
        DevicesCommand.class,
        TestCommand.class
})
public class MoppyCLI implements Callable<Integer> {

    @Option(names = { "-v", "--verbose" }, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = { "-q", "--quiet" }, description = "Quiet mode - minimal output")
    private boolean quiet;

    public static void main(String[] args) {
        // Set up logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");

        int exitCode = new CommandLine(new MoppyCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // If no subcommand is specified, show help
        System.out.println("MoppyCLI - Moppy Musical Floppy Drive Controller");
        System.out.println();
        System.out.println("Usage: moppy <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  play      Play a MIDI file");
        System.out.println("  devices   List and manage network devices");
        System.out.println("  test      Test network connectivity");
        System.out.println();
        System.out.println("Use 'moppy <command> --help' for more information on a command.");

        return 0;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isQuiet() {
        return quiet;
    }
}
