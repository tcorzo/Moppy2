"""Abstract interfaces for the Moppy Button Controller."""

from abc import ABC, abstractmethod
from typing import Callable, Dict, Any


class ApiClient(ABC):
    """Abstract interface for API client implementations."""

    @abstractmethod
    async def load_song(self, file_path: str) -> bool:
        """Load a MIDI song file."""

    @abstractmethod
    async def play(self) -> bool:
        """Start or resume playback."""

    @abstractmethod
    async def pause(self) -> bool:
        """Pause playback."""

    @abstractmethod
    async def stop(self) -> bool:
        """Stop playback."""

    @abstractmethod
    async def get_status(self) -> Dict[str, Any]:
        """Get current playback status."""


class GpioController(ABC):
    """Abstract interface for GPIO controller implementations."""

    @abstractmethod
    def setup(self) -> None:
        """Initialize GPIO settings."""

    @abstractmethod
    def add_button(self, pin: int, callback: Callable[[], None]) -> None:
        """Add a button with callback."""

    @abstractmethod
    def cleanup(self) -> None:
        """Clean up GPIO resources."""


class ButtonHandler(ABC):
    """Abstract interface for button event handling."""

    @abstractmethod
    async def handle_button_press(self, pin: int) -> None:
        """Handle a button press event."""


class Logger(ABC):
    """Abstract interface for logging implementations."""

    @abstractmethod
    def debug(self, message: str) -> None:
        """Log debug message."""

    @abstractmethod
    def info(self, message: str) -> None:
        """Log info message."""

    @abstractmethod
    def warning(self, message: str) -> None:
        """Log warning message."""

    @abstractmethod
    def error(self, message: str) -> None:
        """Log error message."""
