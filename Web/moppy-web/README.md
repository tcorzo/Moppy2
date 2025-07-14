# Moppy Web Controller

A modern web interface for controlling the Moppy musical floppy drive hardware system. This Next.js application provides an intuitive dashboard for loading MIDI files, controlling playback, adjusting parameters, and monitoring network status.

## Features

- **MIDI File Loading**: Load and play MIDI files (.mid, .midi)
- **Playback Controls**: Play, pause, stop with real-time progress tracking
- **Parameter Adjustment**:
  - Volume control (0-200%)
  - Tempo adjustment (20-300 BPM)
  - Loop mode toggle
  - Position seeking
- **Network Monitoring**: View connected devices and bridge status
- **Real-time Updates**: Live status updates during playback
- **Modern UI**: Beautiful, responsive interface with dark theme

## Prerequisites

- Node.js 18+
- MoppyAPI server running (default: http://localhost:8080)
- MIDI files for testing

## Setup

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Start the development server:**
   ```bash
   npm run dev
   ```

3. **Open your browser to:**
   ```
   http://localhost:3000
   ```

## Usage

### Starting the MoppyAPI Server

First, ensure the MoppyAPI server is running. From the Java/MoppyAPI directory:

```bash
java -jar build/libs/MoppyAPI-2.2.0.jar --port 8080
```

### Using the Web Interface

1. **Load a MIDI File**: Enter the full path to a MIDI file in the file input field and click "Load Song"

2. **Control Playback**: Use the play, pause, and stop buttons to control playback

3. **Adjust Parameters**:
   - **Volume**: Use the volume slider (0-200%)
   - **Tempo**: Adjust playback speed (20-300 BPM)
   - **Position**: Seek to any position using the progress bar
   - **Loop**: Toggle repeat mode

4. **Monitor Status**: View network status, connected devices, and real-time playback information

## API Configuration

By default, the app connects to `http://localhost:8080`. The MoppyAPI client is configured in `lib/api.ts`.

To change the server URL, modify the base URL in the MoppyApiClient constructor:

```typescript
const moppyApi = new MoppyApiClient('http://your-server:port')
```

## Project Structure

```
├── components/
│   ├── ui/                 # Reusable UI components (shadcn/ui)
│   └── MoppyControl.tsx    # Main controller component
├── lib/
│   ├── api.ts             # MoppyAPI client
│   └── utils.ts           # Utility functions
└── src/
    └── app/
        ├── globals.css    # Global styles
        ├── layout.tsx     # App layout
        └── page.tsx       # Main page
```

## Technologies Used

- **Next.js 15**: React framework with App Router
- **TypeScript**: Type-safe development
- **Tailwind CSS**: Utility-first CSS framework
- **shadcn/ui**: Modern component library
- **Lucide React**: Beautiful icons
- **Radix UI**: Accessible primitives

## Development

### Building for Production

```bash
npm run build
npm start
```

### Linting

```bash
npm run lint
```

## API Reference

This application interfaces with the MoppyAPI REST endpoints:

- `GET /api/health` - Health check
- `POST /api/load` - Load MIDI file
- `POST /api/play` - Start playback
- `POST /api/pause` - Pause playback
- `POST /api/stop` - Stop playback
- `GET /api/state` - Get full playback state
- `GET /api/status` - Get simplified status
- `PUT /api/position` - Seek to position
- `PUT /api/tempo` - Set tempo
- `PUT /api/volume` - Set volume
- `PUT /api/loop` - Set loop mode
- `GET /api/network/status` - Network status
- `GET /api/network/devices` - Discovered devices

## License

This project is part of the Moppy2 system and follows the same MIT license.
