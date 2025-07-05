# Moppy API Documentation

This directory contains comprehensive API documentation for the Moppy API v2.2.0.

## 📄 Documentation Files

- **`openapi.yaml`** - OpenAPI 3.0.0 specification for API integration and tooling

## 🚀 Quick Start

### 1. Build the API Server
```bash
./gradlew build
```

### 2. Start the API Server
```bash
# Using Gradle
./gradlew run

# Or using the built JAR (default port 8080)
java -jar build/libs/MoppyAPI-2.2.0.jar

# Custom port
java -jar build/libs/MoppyAPI-2.2.0.jar --port 9090
```

### 3. View Documentation
- Use `openapi.yaml` with tools like Swagger UI, Postman, or API clients
- Import the OpenAPI spec into your preferred API testing tool

### 4. Test the API
```bash
# Health check
curl http://localhost:8080/api/health

# Check network status
curl http://localhost:8080/api/network/status

# View discovered devices
curl http://localhost:8080/api/network/devices

# Load a MIDI file
curl -X POST http://localhost:8080/api/load \
     -H "Content-Type: application/json" \
     -d '{"filePath": "/path/to/your/song.mid"}'

# Start playback
curl -X POST http://localhost:8080/api/play

# Get playback status
curl http://localhost:8080/api/status
```

## 🎯 API Overview

The Moppy API provides REST endpoints for controlling musical hardware devices (floppy drives, hard drives, etc.) through MIDI playback:

### Core Features
- **🎼 MIDI File Control** - Load and manage .mid/.midi files
- **⏯️ Playback Control** - Play, pause, stop operations with real-time control
- **🎵 Real-time Parameters** - Adjust tempo, volume, and playback position
- **🔄 Loop Mode** - Continuous playback control and configuration
- **📊 Status Monitoring** - Get comprehensive playback state and progress
- **🌐 Network Management** - Discover and manage Moppy devices on the network
- **🔧 System Health** - Monitor API server health and status

### Device Control
Moppy transforms computer hardware into musical instruments by controlling stepper motors to produce specific frequencies. The API provides a programmatic interface to control multiple devices simultaneously for musical performances.

## 📚 API Endpoints

### System Operations
| Endpoint      | Method | Description                  |
|---------------|--------|------------------------------|
| `/api/health` | GET    | Health check and server info |

### Playback Control
| Endpoint     | Method | Description                 |
|--------------|--------|-----------------------------|
| `/api/load`  | POST   | Load MIDI file for playback |
| `/api/play`  | POST   | Start/resume playback       |
| `/api/pause` | POST   | Pause playback              |
| `/api/stop`  | POST   | Stop playback               |

### Status & State
| Endpoint      | Method | Description                 |
|---------------|--------|-----------------------------|
| `/api/state`  | GET    | Get complete system state   |
| `/api/status` | GET    | Get current playback status |

### Playback Parameters
| Endpoint        | Method  | Description                   |
|-----------------|---------|-------------------------------|
| `/api/position` | GET/PUT | Current position control (ms) |
| `/api/tempo`    | GET/PUT | Tempo control (BPM)           |
| `/api/volume`   | GET/PUT | Volume control (0.0-2.0)      |
| `/api/loop`     | GET/PUT | Loop mode toggle              |

### Network Management
| Endpoint               | Method | Description                   |
|------------------------|--------|-------------------------------|
| `/api/network/status`  | GET    | Get network connection status |
| `/api/network/devices` | GET    | List discovered Moppy devices |

## 🛠️ Development & Integration

### OpenAPI Specification
The `openapi.yaml` file provides a complete API specification that can be used with:

- **🔍 Swagger UI** - Interactive API explorer and documentation
- **📮 Postman** - Import for comprehensive API testing
- **⚙️ Code Generation** - Generate client libraries in multiple languages
- **🚪 API Gateways** - Integration with Kong, Zuul, AWS API Gateway, etc.
- **🧪 Testing Tools** - Automated API testing and validation

### Building from Source
```bash
# Clone the repository
git clone https://github.com/SammyIAm/Moppy2.git
cd Moppy2/Java/MoppyAPI

# Build the project
./gradlew build

# Run tests
./gradlew test

# Create distribution
./gradlew distTar distZip
```

### Configuration
The API server accepts command-line arguments:
- `--port <number>` - Set the server port (default: 8080)
- `--help` - Display usage information

### Response Formats
All API responses are in JSON format with consistent structure:
- **Success responses** include `success: true` and relevant data
- **Error responses** include `error` message and HTTP status code
- **Status responses** provide detailed state information

## 🔧 Requirements

- **Java 11+** - Required for running the API server
- **Gradle** - For building from source (wrapper included)
- **Moppy Hardware** - Floppy drives, hard drives, or compatible stepper motor devices
- **MIDI Files** - Standard .mid or .midi format files for playback

