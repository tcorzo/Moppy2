// API types based on the OpenAPI specification
export interface PlaybackState {
    playbackState: 'UNLOADED' | 'LOADED' | 'PLAYING' | 'PAUSED' | 'ERROR'
    fileName: string
    filePath: string
    duration: number
    position: number
    tempo: number
    volume: number
    loop: boolean
    error: string
    progress: number
    formattedDuration: string
    formattedPosition: string
}

export interface PlaybackStatus {
    state: 'UNLOADED' | 'LOADED' | 'PLAYING' | 'PAUSED' | 'ERROR'
    isPlaying: boolean
    fileName: string
    progress: number
    position: string
    duration: string
}

export interface NetworkStatus {
    isStarted: boolean
    connectedBridges: number
    discoveredDevices: number
    availableBridges: string[]
}

export interface NetworkDevice {
    networkAddress: string
    deviceAddress: number
    minSubAddress: number
    maxSubAddress: number
}

export interface NetworkDevices {
    count: number
    devices: NetworkDevice[]
}

export interface ApiResponse<T = unknown> {
    success?: boolean
    message?: string
    error?: string
    status?: number
    data?: T
}

class MoppyApiClient {
    private baseUrl: string

    constructor(baseUrl: string = '/api/moppy') {
        this.baseUrl = baseUrl
    }

    private async request<T>(
        endpoint: string,
        options: RequestInit = {}
    ): Promise<T> {
        // Remove /api prefix since our proxy route handles it
        const cleanEndpoint = endpoint.startsWith('/api/') ? endpoint.substring(5) : endpoint
        const url = `${this.baseUrl}/${cleanEndpoint}`

        try {
            const response = await fetch(url, {
                headers: {
                    'Content-Type': 'application/json',
                    ...options.headers,
                },
                ...options,
            })

            if (!response.ok) {
                const errorData = await response.text()
                throw new Error(`HTTP ${response.status}: ${errorData}`)
            }

            return await response.json()
        } catch (error) {
            if (error instanceof Error) {
                throw error
            }
            throw new Error('Network error occurred')
        }
    }

    // Health check
    async getHealth() {
        return this.request<{ status: string; service: string; version: string }>('health')
    }

    // Playback control
    async loadSong(filePath: string) {
        return this.request<ApiResponse>('load', {
            method: 'POST',
            body: JSON.stringify({ filePath }),
        })
    }

    async play() {
        return this.request<ApiResponse>('play', { method: 'POST' })
    }

    async pause() {
        return this.request<ApiResponse>('pause', { method: 'POST' })
    }

    async stop() {
        return this.request<ApiResponse>('stop', { method: 'POST' })
    }

    // State and status
    async getState() {
        return this.request<PlaybackState>('state')
    }

    async getStatus() {
        return this.request<PlaybackStatus>('status')
    }

    // Parameters
    async getPosition() {
        return this.request<{ position: number; formattedPosition: string }>('position')
    }

    async setPosition(position: number) {
        return this.request<{ success: boolean; position: number }>('position', {
            method: 'PUT',
            body: JSON.stringify({ position }),
        })
    }

    async getTempo() {
        return this.request<{ tempo: number }>('tempo')
    }

    async setTempo(tempo: number) {
        return this.request<{ success: boolean; tempo: number }>('tempo', {
            method: 'PUT',
            body: JSON.stringify({ tempo }),
        })
    }

    async getVolume() {
        return this.request<{ volume: number }>('volume')
    }

    async setVolume(volume: number) {
        return this.request<{ success: boolean; volume: number }>('volume', {
            method: 'PUT',
            body: JSON.stringify({ volume }),
        })
    }

    async getLoop() {
        return this.request<{ loop: boolean }>('loop')
    }

    async setLoop(loop: boolean) {
        return this.request<{ success: boolean; loop: boolean }>('loop', {
            method: 'PUT',
            body: JSON.stringify({ loop }),
        })
    }

    // Network
    async getNetworkStatus() {
        return this.request<NetworkStatus>('network/status')
    }

    async getNetworkDevices() {
        return this.request<NetworkDevices>('network/devices')
    }
}

export const moppyApi = new MoppyApiClient()
