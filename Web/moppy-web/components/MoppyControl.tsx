'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
	Play,
	Pause,
	Square,
	FolderOpen,
	Volume2,
	Repeat,
	Wifi,
	WifiOff,
	Music,
	Gauge,
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Slider } from '@/components/ui/slider';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import { moppyApi, type PlaybackState, type NetworkStatus } from '@/lib/api';

export default function MoppyControl() {
	const [state, setState] = useState<PlaybackState | null>(null);
	const [networkStatus, setNetworkStatus] = useState<NetworkStatus | null>(
		null
	);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [filePath, setFilePath] = useState('');
	const [connected, setConnected] = useState(false);

	// Fetch current state
	const fetchState = useCallback(async () => {
		try {
			const currentState = await moppyApi.getState();
			setState(currentState);
			setConnected(true);
			setError(null);
		} catch (err) {
			setConnected(false);
			setError(
				err instanceof Error ? err.message : 'Failed to fetch state'
			);
		}
	}, []);

	// Fetch network status
	const fetchNetworkStatus = useCallback(async () => {
		try {
			const status = await moppyApi.getNetworkStatus();
			setNetworkStatus(status);
		} catch (err) {
			console.error('Failed to fetch network status:', err);
		}
	}, []);

	// Auto-refresh state every second when playing
	useEffect(() => {
		fetchState();
		fetchNetworkStatus();

		const interval = setInterval(() => {
			if (state?.playbackState === 'PLAYING') {
				fetchState();
			}
		}, 1000);

		return () => clearInterval(interval);
	}, [fetchState, fetchNetworkStatus, state?.playbackState]);

	const handleAction = async (action: () => Promise<unknown>) => {
		setLoading(true);
		try {
			await action();
			await fetchState();
		} catch (err) {
			setError(err instanceof Error ? err.message : 'Operation failed');
		} finally {
			setLoading(false);
		}
	};

	const loadSong = () => handleAction(() => moppyApi.loadSong(filePath));
	const play = () => handleAction(() => moppyApi.play());
	const pause = () => handleAction(() => moppyApi.pause());
	const stop = () => handleAction(() => moppyApi.stop());

	const handleVolumeChange = (values: number[]) => {
		handleAction(() => moppyApi.setVolume(values[0] / 100));
	};

	const handleTempoChange = (values: number[]) => {
		handleAction(() => moppyApi.setTempo(values[0]));
	};

	const handlePositionChange = (values: number[]) => {
		if (state) {
			const newPosition = Math.floor((values[0] / 100) * state.duration);
			handleAction(() => moppyApi.setPosition(newPosition));
		}
	};

	const handleLoopToggle = (checked: boolean) => {
		handleAction(() => moppyApi.setLoop(checked));
	};

	const getStateColor = () => {
		if (!connected) return 'text-red-500';
		switch (state?.playbackState) {
			case 'PLAYING':
				return 'text-green-500';
			case 'PAUSED':
				return 'text-yellow-500';
			case 'ERROR':
				return 'text-red-500';
			case 'LOADED':
				return 'text-blue-500';
			default:
				return 'text-gray-500';
		}
	};

	const getStateIcon = () => {
		if (!connected) return <WifiOff className="w-4 h-4" />;
		if (networkStatus?.isStarted) return <Wifi className="w-4 h-4" />;
		return <WifiOff className="w-4 h-4" />;
	};

	return (
		<div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-4">
			<div className="max-w-4xl mx-auto space-y-6">
				{/* Header */}
				<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
					<CardHeader>
						<div className="flex items-center justify-between">
							<div className="flex items-center space-x-3">
								<Music className="w-8 h-8 text-purple-400" />
								<div>
									<CardTitle className="text-white text-2xl">
										Moppy Controller
									</CardTitle>
									<CardDescription className="text-gray-300">
										Musical Floppy Drive Control Interface
									</CardDescription>
								</div>
							</div>
							<div className="flex items-center space-x-2">
								{getStateIcon()}
								<span
									className={`text-sm font-medium ${getStateColor()}`}
								>
									{connected
										? state?.playbackState || 'UNKNOWN'
										: 'DISCONNECTED'}
								</span>
							</div>
						</div>
					</CardHeader>
				</Card>

				{/* Connection Status */}
				{error && (
					<Card className="bg-red-500/10 border-red-500/20">
						<CardContent className="p-4">
							<p className="text-red-400 text-sm">{error}</p>
						</CardContent>
					</Card>
				)}

				<div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
					{/* File Loading */}
					<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
						<CardHeader>
							<CardTitle className="text-white flex items-center">
								<FolderOpen className="w-5 h-5 mr-2" />
								Load MIDI File
							</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<Input
								placeholder="/path/to/song.mid"
								value={filePath}
								onChange={(e) => setFilePath(e.target.value)}
								className="bg-black/30 border-gray-600 text-white placeholder-gray-400"
							/>
							<Button
								onClick={loadSong}
								disabled={loading || !filePath}
								className="w-full bg-purple-600 hover:bg-purple-700"
							>
								Load Song
							</Button>
							{state?.fileName && (
								<p className="text-sm text-gray-300 truncate">
									Loaded: {state.fileName}
								</p>
							)}
						</CardContent>
					</Card>

					{/* Network Status */}
					<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
						<CardHeader>
							<CardTitle className="text-white flex items-center">
								<Wifi className="w-5 h-5 mr-2" />
								Network Status
							</CardTitle>
						</CardHeader>
						<CardContent className="space-y-2">
							<div className="flex justify-between text-sm">
								<span className="text-gray-300">Status:</span>
								<span
									className={
										networkStatus?.isStarted
											? 'text-green-400'
											: 'text-red-400'
									}
								>
									{networkStatus?.isStarted
										? 'Active'
										: 'Inactive'}
								</span>
							</div>
							<div className="flex justify-between text-sm">
								<span className="text-gray-300">
									Connected Bridges:
								</span>
								<span className="text-white">
									{networkStatus?.connectedBridges || 0}
								</span>
							</div>
							<div className="flex justify-between text-sm">
								<span className="text-gray-300">
									Discovered Devices:
								</span>
								<span className="text-white">
									{networkStatus?.discoveredDevices || 0}
								</span>
							</div>
							<div className="text-xs text-gray-400 mt-2">
								Available:{' '}
								{networkStatus?.availableBridges?.join(', ') ||
									'None'}
							</div>
						</CardContent>
					</Card>
				</div>

				{/* Playback Controls */}
				<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
					<CardHeader>
						<CardTitle className="text-white">
							Playback Controls
						</CardTitle>
					</CardHeader>
					<CardContent className="space-y-6">
						{/* Progress Bar */}
						<div className="space-y-2">
							<div className="flex justify-between text-sm text-gray-300">
								<span>
									{state?.formattedPosition || '0:00'}
								</span>
								<span>
									{state?.formattedDuration || '0:00'}
								</span>
							</div>
							<div className="space-y-2">
								<Progress
									value={(state?.progress || 0) * 100}
									className="h-2 bg-gray-700"
								/>
								<Slider
									value={[(state?.progress || 0) * 100]}
									onValueChange={handlePositionChange}
									max={100}
									step={0.1}
									className="w-full"
									disabled={
										!state ||
										state.playbackState === 'UNLOADED'
									}
								/>
							</div>
						</div>

						{/* Control Buttons */}
						<div className="flex justify-center space-x-4">
							<Button
								onClick={play}
								disabled={
									loading ||
									!state ||
									state.playbackState === 'UNLOADED'
								}
								size="lg"
								className="bg-green-600 hover:bg-green-700"
							>
								<Play className="w-5 h-5" />
							</Button>
							<Button
								onClick={pause}
								disabled={
									loading ||
									!state ||
									state.playbackState !== 'PLAYING'
								}
								size="lg"
								className="bg-yellow-600 hover:bg-yellow-700"
							>
								<Pause className="w-5 h-5" />
							</Button>
							<Button
								onClick={stop}
								disabled={
									loading ||
									!state ||
									state.playbackState === 'UNLOADED'
								}
								size="lg"
								className="bg-red-600 hover:bg-red-700"
							>
								<Square className="w-5 h-5" />
							</Button>
						</div>
					</CardContent>
				</Card>

				{/* Parameter Controls */}
				<div className="grid grid-cols-1 md:grid-cols-3 gap-6">
					{/* Volume */}
					<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
						<CardHeader>
							<CardTitle className="text-white text-lg flex items-center">
								<Volume2 className="w-5 h-5 mr-2" />
								Volume
							</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="flex items-center justify-between">
								<span className="text-sm text-gray-300">
									Level:
								</span>
								<span className="text-white font-mono">
									{Math.round((state?.volume || 1) * 100)}%
								</span>
							</div>
							<Slider
								value={[(state?.volume || 1) * 100]}
								onValueChange={handleVolumeChange}
								max={200}
								min={0}
								step={1}
								className="w-full"
							/>
						</CardContent>
					</Card>

					{/* Tempo */}
					<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
						<CardHeader>
							<CardTitle className="text-white text-lg flex items-center">
								<Gauge className="w-5 h-5 mr-2" />
								Tempo
							</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="flex items-center justify-between">
								<span className="text-sm text-gray-300">
									BPM:
								</span>
								<span className="text-white font-mono">
									{Math.round(state?.tempo || 120)}
								</span>
							</div>
							<Slider
								value={[state?.tempo || 120]}
								onValueChange={handleTempoChange}
								max={300}
								min={20}
								step={1}
								className="w-full"
							/>
						</CardContent>
					</Card>

					{/* Loop */}
					<Card className="bg-black/20 border-purple-500/20 backdrop-blur-sm">
						<CardHeader>
							<CardTitle className="text-white text-lg flex items-center">
								<Repeat className="w-5 h-5 mr-2" />
								Loop Mode
							</CardTitle>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="flex items-center justify-between">
								<span className="text-sm text-gray-300">
									Repeat:
								</span>
								<Switch
									checked={state?.loop || false}
									onCheckedChange={handleLoopToggle}
								/>
							</div>
							<p className="text-xs text-gray-400">
								{state?.loop
									? 'Song will repeat continuously'
									: 'Song will play once'}
							</p>
						</CardContent>
					</Card>
				</div>
			</div>
		</div>
	);
}
