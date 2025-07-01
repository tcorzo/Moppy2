# MoppyCLI

A command-line interface for playing MIDI files through Moppy musical floppy drive networks.

## Features

- **MIDI File Playback**: Play standard MIDI files (.mid/.midi) through Moppy devices
- **Network Support**: UDP multicast and Serial communication
- **Device Discovery**: Automatic detection of Moppy devices on the network
- **Playback Control**: Tempo adjustment, velocity control, looping
- **Progress Tracking**: Real-time progress bar with time display
- **Testing Tools**: Network connectivity and device communication testing

## Building

```bash
cd Java/MoppyCLI
./gradlew build
```

This creates a fat JAR with all dependencies at `build/libs/MoppyCLI-all.jar`.

## Usage

### Basic MIDI Playback

```bash
# Play a MIDI file (auto-discovers devices)
java -jar MoppyCLI-all.jar play song.mid

# Play with specific network type
java -jar MoppyCLI-all.jar play song.mid --network udp

# Play through specific serial port
java -jar MoppyCLI-all.jar play song.mid --network serial --port /dev/ttyUSB0
```

### Playback Options

```bash
# Adjust velocity (volume)
java -jar MoppyCLI-all.jar play song.mid --velocity 0.8

# Adjust tempo (speed)
java -jar MoppyCLI-all.jar play song.mid --tempo 1.5

# Loop the song
java -jar MoppyCLI-all.jar play song.mid --loop

# Target specific device and sub-devices
java -jar MoppyCLI-all.jar play song.mid --device 2 --sub-devices 16

# Disable progress bar
java -jar MoppyCLI-all.jar play song.mid --no-progress

# Verbose output
java -jar MoppyCLI-all.jar play song.mid --verbose
```

### Device Discovery

```bash
# Discover devices on the network
java -jar MoppyCLI-all.jar devices

# Increase discovery timeout
java -jar MoppyCLI-all.jar devices --timeout 15

# Verbose device information
java -jar MoppyCLI-all.jar devices --verbose
```

### Network Testing

```bash
# Test network connectivity
java -jar MoppyCLI-all.jar test

# Test specific device
java -jar MoppyCLI-all.jar test --device 2 --sub-device 3

# Test with custom note and duration
java -jar MoppyCLI-all.jar test --note 72 --duration 3
```

## Command Reference

### Global Options

- `--help, -h`: Show help information
- `--version`: Show version information

### Play Command

```
java -jar MoppyCLI-all.jar play <midi-file> [options]
```

**Options:**
- `--network, -n <type>`: Network type (udp, serial, all) [default: all]
- `--port, -p <port>`: Serial port for serial network
- `--device, -d <address>`: Target device address [default: 1]
- `--sub-devices, -s <count>`: Number of sub-devices [default: 8]
- `--velocity, -v <multiplier>`: Velocity multiplier 0.1-2.0 [default: 1.0]
- `--tempo, -t <multiplier>`: Tempo multiplier 0.1-5.0 [default: 1.0]
- `--loop`: Loop the MIDI file
- `--no-progress`: Disable progress bar
- `--verbose`: Verbose output

### Devices Command

```
java -jar MoppyCLI-all.jar devices [options]
```

**Options:**
- `--network, -n <type>`: Network type (udp, serial, all) [default: all]
- `--port, -p <port>`: Serial port for serial network
- `--timeout, -t <seconds>`: Discovery timeout [default: 10]
- `--verbose`: Verbose output

### Test Command

```
java -jar MoppyCLI-all.jar test [options]
```

**Options:**
- `--network, -n <type>`: Network type (udp, serial, all) [default: all]
- `--port, -p <port>`: Serial port for serial network
- `--device, -d <address>`: Target device address [default: 1]
- `--sub-device, -s <address>`: Target sub-device address [default: 1]
- `--note <number>`: Test note number (MIDI) [default: 60]
- `--duration <seconds>`: Test note duration [default: 2]
- `--verbose`: Verbose output

## Examples

### Playing Different File Types

```bash
# Standard MIDI file
java -jar MoppyCLI-all.jar play "Fur Elise.mid"

# With custom settings
java -jar MoppyCLI-all.jar play "Symphony.mid" --velocity 0.7 --tempo 0.9 --device 1 --sub-devices 16
```

### Network-Specific Usage

```bash
# UDP only (for networked devices)
java -jar MoppyCLI-all.jar play song.mid --network udp

# Serial only (for direct-connected Arduino)
java -jar MoppyCLI-all.jar play song.mid --network serial --port COM3

# Windows serial port
java -jar MoppyCLI-all.jar play song.mid --network serial --port COM3

# Linux/Mac serial port
java -jar MoppyCLI-all.jar play song.mid --network serial --port /dev/ttyUSB0
```

### Device Management

```bash
# Quick device scan
java -jar MoppyCLI-all.jar devices

# Detailed device information
java -jar MoppyCLI-all.jar devices --verbose --timeout 15

# Test communication with discovered devices
java -jar MoppyCLI-all.jar test --verbose
```

## Troubleshooting

### No Devices Found

1. Ensure Moppy devices are powered on and connected
2. Check network connectivity (firewall, network interface)
3. Try increasing discovery timeout: `--timeout 30`
4. Use verbose mode for detailed output: `--verbose`
5. Test specific network types: `--network udp` or `--network serial`

### MIDI File Issues

1. Verify file is a valid MIDI file (.mid or .midi extension)
2. Try with a different MIDI file
3. Use verbose mode to see detailed error information

### Serial Connection Issues

1. Check serial port name (COM1, /dev/ttyUSB0, etc.)
2. Ensure no other applications are using the serial port
3. Verify baud rate and connection settings match your device
4. Try different USB ports or cables

### Performance Issues

1. Reduce velocity multiplier if notes are too fast/loud
2. Adjust tempo multiplier for appropriate playback speed
3. Use `--no-progress` to disable progress bar for better performance
4. Ensure adequate system resources for real-time MIDI processing

## Integration

The CLI can be integrated into scripts and automation:

```bash
#!/bin/bash
# Play a playlist of MIDI files
for file in *.mid; do
    echo "Playing: $file"
    java -jar MoppyCLI-all.jar play "$file" --velocity 0.8
    sleep 2
done
```

## Dependencies

- Java 8 or higher
- MoppyLib (included in fat JAR)
- PicoCLI for command-line parsing
- Access to MIDI system for file parsing
- Network access for UDP communication or serial port access
