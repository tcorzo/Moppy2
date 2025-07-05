"""Main application for the Moppy Button Controller."""

import asyncio
import signal
import sys
from pathlib import Path

from .config import Config
from .logger import StandardLogger
from .api_client import MoppyApiClient
from .gpio_controller import RaspberryPiGpioController
from .button_handler import MoppyButtonHandler


class MoppyButtonController:
    """Main application class for the Moppy Button Controller."""

    def __init__(self, config_path: str):
        """Initialize the controller.

        Args:
            config_path: Path to the YAML configuration file
        """
        self.config = Config.from_yaml(config_path)
        self.config.validate()

        # Initialize logger
        self.logger = StandardLogger(
            "MoppyButtonController",
            self.config.logging.level,
            self.config.logging.format,
        )

        # Initialize components
        self.api_client = MoppyApiClient(self.config.moppy_api.base_url, self.logger)
        self.gpio_controller = RaspberryPiGpioController(
            self.config.gpio.mode,
            self.config.gpio.pull_up_down,
            self.config.gpio.bounce_time,
            self.logger,
        )
        self.button_handler = MoppyButtonHandler(self.api_client, self.logger)

        # Runtime state
        self.running = False
        self.setup_complete = False

    async def setup(self) -> None:
        """Set up the controller components."""
        try:
            self.logger.info("Setting up Moppy Button Controller...")

            # Check API connectivity
            if not await self.api_client.health_check():
                raise RuntimeError(
                    f"Cannot connect to Moppy API at {self.config.moppy_api.base_url}"
                )

            self.logger.info("Successfully connected to Moppy API")

            # Setup GPIO
            self.gpio_controller.setup()

            # Configure buttons
            for button_config in self.config.buttons:
                # Add button configuration to handler
                self.button_handler.add_button_config(button_config)

                # Create a synchronous callback that schedules the async handler
                def create_callback(pin):
                    def callback():
                        # Schedule the async handler to run in the event loop
                        loop = asyncio.get_event_loop()
                        if loop.is_running():
                            loop.create_task(
                                self.button_handler.handle_button_press(pin)
                            )

                    return callback

                # Setup GPIO pin with callback
                self.gpio_controller.add_button(
                    button_config.pin, create_callback(button_config.pin)
                )

            self.setup_complete = True
            self.logger.info("Setup complete. Ready to accept button presses.")

        except Exception as e:
            self.logger.error(f"Setup failed: {str(e)}")
            raise

    async def run(self) -> None:
        """Run the main application loop."""
        if not self.setup_complete:
            await self.setup()

        self.running = True
        self.logger.info("Moppy Button Controller is running...")

        try:
            # Set up signal handlers for graceful shutdown
            def signal_handler(signum, _frame):
                self.logger.info(f"Received signal {signum}, shutting down...")
                self.running = False

            signal.signal(signal.SIGINT, signal_handler)
            signal.signal(signal.SIGTERM, signal_handler)

            # Main loop - just keep running until interrupted
            while self.running:
                await asyncio.sleep(0.1)

        except KeyboardInterrupt:
            self.logger.info("Received keyboard interrupt, shutting down...")
        finally:
            await self.shutdown()

    async def shutdown(self) -> None:
        """Clean shutdown of the controller."""
        self.logger.info("Shutting down Moppy Button Controller...")

        try:
            # Stop any playing music
            await self.button_handler.stop_all()

            # Cleanup GPIO
            self.gpio_controller.cleanup()

            # Close API client
            await self.api_client.close()

            self.logger.info("Shutdown complete")

        except Exception as e:
            self.logger.error(f"Error during shutdown: {str(e)}")


async def main():
    """Main entry point."""
    if len(sys.argv) != 2:
        print("Usage: python main.py <config_file>")
        sys.exit(1)

    config_path = sys.argv[1]

    if not Path(config_path).exists():
        print(f"Configuration file not found: {config_path}")
        sys.exit(1)

    try:
        controller = MoppyButtonController(config_path)
        await controller.run()
    except Exception as e:
        print(f"Fatal error: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
