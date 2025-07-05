# Moppy Button Controller

A SOLID Python application for Raspberry Pi that provides physical button control for the Moppy musical floppy drive controller.

## Features

- **SOLID Design Principles**: Clean, maintainable architecture with clear separation of concerns
- **Configurable Button Mapping**: YAML-based configuration for easy setup
- **Play/Pause Control**: Press a button to play a song, press again to pause
- **Multiple Song Support**: Configure multiple buttons for different MIDI files
- **Robust Error Handling**: Comprehensive logging and error recovery
- **Async/Await**: Non-blocking operation using modern Python async patterns

## Requirements

- Raspberry Pi with GPIO support
- Python 3.13+
- Running Moppy API server
- MIDI files (.mid format)

## Installation

### Using Poetry (Recommended)

```bash
# Clone or navigate to the project directory
cd /path/to/moppy_buttons

# Install dependencies (for Raspberry Pi)
poetry install --with rpi

# For development on non-Pi systems
poetry install
```

### Using pip

```bash
# Install dependencies
pip install -r requirements.txt

# On Raspberry Pi, also install:
pip install RPi.GPIO
```

## Configuration

Create a `config.yaml` file with your button mappings:

```yaml
# Moppy API Configuration
moppy_api:
  host: "localhost"
  port: 8080
  base_url: "http://localhost:8080"

# Button Configuration
buttons:
  - pin: 18
    file: "/home/pi/music/tetris.mid"
  - pin: 19
    file: "/home/pi/music/kirby.mid"
  - pin: 20
    file: "/home/pi/music/opera.mid"

# GPIO Configuration
gpio:
  mode: "BCM"  # or "BOARD"
  pull_up_down: "PUD_UP"  # PUD_UP, PUD_DOWN, or PUD_OFF
  bounce_time: 200  # milliseconds

# Logging Configuration
logging:
  level: "INFO"  # DEBUG, INFO, WARNING, ERROR
  format: "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
```

## Usage

1. **Start the Moppy API server** (refer to Moppy documentation)

2. **Connect your buttons** to the configured GPIO pins

3. **Run the button controller**:
   ```bash
   # Using Poetry
   poetry run python src/main.py config.yaml

   # Using Python directly
   python src/main.py config.yaml
   ```

4. **Press buttons** to play/pause songs!

## Wiring

Connect your buttons between the configured GPIO pins and ground (GND). The internal pull-up resistors are used, so no external resistors are needed.

Example wiring for BCM mode:
- Button 1: GPIO 18 → GND
- Button 2: GPIO 19 → GND
- Button 3: GPIO 20 → GND

## Architecture

The application follows SOLID principles with clear separation of concerns:

- **`Config`**: Configuration management and validation
- **`ApiClient`**: HTTP client for Moppy API communication
- **`GpioController`**: GPIO hardware abstraction
- **`ButtonHandler`**: Business logic for button press events
- **`Logger`**: Logging abstraction
- **`MoppyButtonController`**: Main application orchestrator

### Key Classes

- **Interfaces**: Abstract base classes defining contracts
- **Implementations**: Concrete implementations of the interfaces
- **Configuration**: Data classes for type-safe configuration
- **Main**: Application entry point and orchestration

## Development

### Running Tests

```bash
poetry run pytest
```

### Code Quality

```bash
# Format code
poetry run black src/

# Lint code
poetry run pylint src/

# Type checking
poetry run mypy src/
```

## Troubleshooting

### Common Issues

1. **"Cannot connect to Moppy API"**
   - Ensure the Moppy API server is running
   - Check the host/port configuration
   - Verify network connectivity

2. **"Button not responding"**
   - Check GPIO pin numbers in configuration
   - Verify button wiring
   - Check logs for GPIO setup errors

3. **"Permission denied accessing GPIO"**
   - Run with sudo: `sudo python src/main.py config.yaml`
   - Add user to gpio group: `sudo usermod -a -G gpio $USER`

### Logging

Increase logging verbosity by setting `level: "DEBUG"` in the configuration file.

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes following SOLID principles
4. Add tests for new functionality
5. Submit a pull request
