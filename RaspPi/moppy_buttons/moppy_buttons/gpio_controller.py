"""GPIO controller implementation using RPi.GPIO."""

try:
    import RPi.GPIO as GPIO
except ImportError:
    # Mock GPIO for development/testing on non-Pi systems
    class MockGPIO:
        BCM = 11
        BOARD = 10
        IN = 1
        OUT = 0
        PUD_UP = 22
        PUD_DOWN = 21
        PUD_OFF = 20
        FALLING = 32

        def setmode(self, mode):
            pass

        def setup(self, pin, direction, pull_up_down=None):
            pass

        def add_event_detect(self, pin, edge, callback=None, bouncetime=None):
            pass

        def cleanup(self):
            pass

    GPIO = MockGPIO()

from typing import Callable
from .interfaces import GpioController, Logger


class RaspberryPiGpioController(GpioController):
    """GPIO controller implementation for Raspberry Pi."""

    def __init__(self, mode: str, pull_up_down: str, bounce_time: int, logger: Logger):
        """Initialize GPIO controller.

        Args:
            mode: GPIO numbering mode (BCM or BOARD)
            pull_up_down: Pull-up/down resistor setting
            bounce_time: Debounce time in milliseconds
            logger: Logger instance
        """
        self.mode = getattr(GPIO, mode)
        self.pull_up_down = getattr(GPIO, pull_up_down)
        self.bounce_time = bounce_time
        self.logger = logger
        self.is_setup = False

    def setup(self) -> None:
        """Initialize GPIO settings."""
        if not self.is_setup:
            GPIO.setmode(self.mode)
            self.is_setup = True
            self.logger.info(f"GPIO setup complete with mode: {self.mode}")

    def add_button(self, pin: int, callback: Callable[[], None]) -> None:
        """Add a button with callback.

        Args:
            pin: GPIO pin number
            callback: Function to call when button is pressed
        """
        if not self.is_setup:
            raise RuntimeError("GPIO not setup. Call setup() first.")

        GPIO.setup(pin, GPIO.IN, pull_up_down=self.pull_up_down)
        GPIO.add_event_detect(
            pin,
            GPIO.FALLING,
            callback=lambda channel: callback(),
            bouncetime=self.bounce_time,
        )
        self.logger.info(f"Button configured on pin {pin}")

    def cleanup(self) -> None:
        """Clean up GPIO resources."""
        if self.is_setup:
            GPIO.cleanup()
            self.is_setup = False
            self.logger.info("GPIO cleanup complete")
