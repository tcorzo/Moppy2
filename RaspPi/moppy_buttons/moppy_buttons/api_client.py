"""HTTP client implementation for the Moppy API."""

import aiohttp
from typing import Dict, Any
from .interfaces import ApiClient, Logger


class MoppyApiClient(ApiClient):
    """HTTP client for communicating with the Moppy API server."""

    def __init__(self, base_url: str, logger: Logger):
        """Initialize the API client.

        Args:
            base_url: Base URL of the Moppy API server
            logger: Logger instance for logging operations
        """
        self.base_url = base_url.rstrip("/")
        self.logger = logger
        self.session: aiohttp.ClientSession | None = None

    async def _ensure_session(self) -> aiohttp.ClientSession:
        """Ensure an HTTP session exists."""
        if self.session is None or self.session.closed:
            self.session = aiohttp.ClientSession()
        return self.session

    async def _make_request(
        self, method: str, endpoint: str, json_data: Dict[str, Any] | None = None
    ) -> Dict[str, Any]:
        """Make an HTTP request to the API.

        Args:
            method: HTTP method (GET, POST, PUT)
            endpoint: API endpoint
            json_data: JSON data to send in request body

        Returns:
            Response data as dictionary

        Raises:
            aiohttp.ClientError: If request fails
        """
        session = await self._ensure_session()
        url = f"{self.base_url}{endpoint}"

        try:
            async with session.request(method, url, json=json_data) as response:
                if response.content_type == "application/json":
                    return await response.json()
                else:
                    # Handle non-JSON responses
                    text = await response.text()
                    return {"message": text, "status_code": response.status}
        except Exception as e:
            self.logger.error(f"Request failed: {method} {url} - {str(e)}")
            raise

    async def load_song(self, file_path: str) -> bool:
        """Load a MIDI song file.

        Args:
            file_path: Path to the MIDI file

        Returns:
            True if successful, False otherwise
        """
        try:
            self.logger.info(f"Loading song: {file_path}")
            response = await self._make_request(
                "POST", "/api/load", {"filePath": file_path}
            )
            success = response.get("success", False)
            if success:
                self.logger.info(f"Successfully loaded: {file_path}")
            else:
                self.logger.error(f"Failed to load song: {response}")
            return success
        except Exception as e:
            self.logger.error(f"Error loading song {file_path}: {str(e)}")
            return False

    async def play(self) -> bool:
        """Start or resume playback.

        Returns:
            True if successful, False otherwise
        """
        try:
            self.logger.info("Starting playback")
            response = await self._make_request("POST", "/api/play")
            success = response.get("success", False)
            if success:
                self.logger.info("Playback started successfully")
            else:
                self.logger.error(f"Failed to start playback: {response}")
            return success
        except Exception as e:
            self.logger.error(f"Error starting playback: {str(e)}")
            return False

    async def pause(self) -> bool:
        """Pause playback.

        Returns:
            True if successful, False otherwise
        """
        try:
            self.logger.info("Pausing playback")
            response = await self._make_request("POST", "/api/pause")
            success = response.get("success", False)
            if success:
                self.logger.info("Playback paused successfully")
            else:
                self.logger.error(f"Failed to pause playback: {response}")
            return success
        except Exception as e:
            self.logger.error(f"Error pausing playback: {str(e)}")
            return False

    async def stop(self) -> bool:
        """Stop playback.

        Returns:
            True if successful, False otherwise
        """
        try:
            self.logger.info("Stopping playback")
            response = await self._make_request("POST", "/api/stop")
            success = response.get("success", False)
            if success:
                self.logger.info("Playback stopped successfully")
            else:
                self.logger.error(f"Failed to stop playback: {response}")
            return success
        except Exception as e:
            self.logger.error(f"Error stopping playback: {str(e)}")
            return False

    async def get_status(self) -> Dict[str, Any]:
        """Get current playback status.

        Returns:
            Status information as dictionary
        """
        try:
            response = await self._make_request("GET", "/api/status")
            return response
        except Exception as e:
            self.logger.error(f"Error getting status: {str(e)}")
            return {"error": str(e)}

    async def health_check(self) -> bool:
        """Check if the API server is healthy.

        Returns:
            True if server is healthy, False otherwise
        """
        try:
            response = await self._make_request("GET", "/api/health")
            return response.get("status") == "healthy"
        except Exception as e:
            self.logger.error(f"Health check failed: {str(e)}")
            return False

    async def close(self) -> None:
        """Close the HTTP session."""
        if self.session and not self.session.closed:
            await self.session.close()
