"""Button handler implementation for managing button press events."""

from typing import Dict, Set
from .interfaces import ButtonHandler, ApiClient, Logger
from .config import ButtonConfig


class MoppyButtonHandler(ButtonHandler):
    """Handles button press events and controls music playback."""

    def __init__(self, api_client: ApiClient, logger: Logger):
        """Initialize button handler.

        Args:
            api_client: API client for controlling Moppy
            logger: Logger instance
        """
        self.api_client = api_client
        self.logger = logger
        self.button_configs: Dict[int, ButtonConfig] = {}
        self.playing_songs: Set[int] = (
            set()
        )  # Track which buttons are currently playing
        self._processing_buttons: Set[int] = set()  # Prevent concurrent processing

    def add_button_config(self, button_config: ButtonConfig) -> None:
        """Add a button configuration.

        Args:
            button_config: Button configuration to add
        """
        self.button_configs[button_config.pin] = button_config
        self.logger.info(
            f"Added button config: pin {button_config.pin} -> {button_config.file}"
        )

    async def handle_button_press(self, pin: int) -> None:
        """Handle a button press event.

        Args:
            pin: GPIO pin number that was pressed
        """
        if pin in self._processing_buttons:
            self.logger.debug(f"Button {pin} is already being processed, ignoring")
            return

        self._processing_buttons.add(pin)

        try:
            if pin not in self.button_configs:
                self.logger.warning(f"No configuration found for pin {pin}")
                return

            button_config = self.button_configs[pin]
            self.logger.info(f"Button pressed: pin {pin}")

            # Check current playback status
            status = await self.api_client.get_status()
            current_file = status.get("fileName", "")
            is_playing = status.get("isPlaying", False)

            # If this button's song is currently playing, pause it
            if (
                pin in self.playing_songs
                and is_playing
                and current_file in button_config.file
            ):
                await self._pause_song(pin)
            else:
                # Load and play the new song
                await self._play_song(pin, button_config)

        except Exception as e:
            self.logger.error(f"Error handling button press for pin {pin}: {str(e)}")
        finally:
            self._processing_buttons.discard(pin)

    async def _play_song(self, pin: int, button_config: ButtonConfig) -> None:
        """Load and play a song.

        Args:
            pin: GPIO pin number
            button_config: Button configuration
        """
        # Stop any currently playing song
        if self.playing_songs:
            await self.api_client.stop()
            self.playing_songs.clear()

        # Load the new song
        if await self.api_client.load_song(button_config.file):
            # Start playback
            if await self.api_client.play():
                self.playing_songs.add(pin)
                self.logger.info(f"Started playing: {button_config.file}")
            else:
                self.logger.error(f"Failed to start playback for: {button_config.file}")
        else:
            self.logger.error(f"Failed to load song: {button_config.file}")

    async def _pause_song(self, pin: int) -> None:
        """Pause the currently playing song.

        Args:
            pin: GPIO pin number
        """
        if await self.api_client.pause():
            self.playing_songs.discard(pin)
            self.logger.info(f"Paused playback for pin {pin}")
        else:
            self.logger.error(f"Failed to pause playback for pin {pin}")

    async def stop_all(self) -> None:
        """Stop all playback and clear playing state."""
        if self.playing_songs:
            await self.api_client.stop()
            self.playing_songs.clear()
            self.logger.info("Stopped all playback")

    def get_playing_pins(self) -> Set[int]:
        """Get the set of pins that are currently playing.

        Returns:
            Set of pin numbers currently playing songs
        """
        return self.playing_songs.copy()
