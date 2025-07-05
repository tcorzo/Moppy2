"""Configuration models for the Moppy Button Controller."""

from dataclasses import dataclass
from typing import List
import yaml
from pathlib import Path


@dataclass
class MoppyApiConfig:
    """Configuration for the Moppy API connection."""

    host: str
    port: int
    base_url: str


@dataclass
class ButtonConfig:
    """Configuration for a single button."""

    pin: int
    file: str


@dataclass
class GpioConfig:
    """GPIO configuration settings."""

    mode: str
    pull_up_down: str
    bounce_time: int


@dataclass
class LoggingConfig:
    """Logging configuration settings."""

    level: str
    format: str


@dataclass
class Config:
    """Main configuration container."""

    moppy_api: MoppyApiConfig
    buttons: List[ButtonConfig]
    gpio: GpioConfig
    logging: LoggingConfig

    @classmethod
    def from_yaml(cls, config_path: str) -> "Config":
        """Load configuration from a YAML file."""
        config_file = Path(config_path)
        if not config_file.exists():
            raise FileNotFoundError(f"Configuration file not found: {config_path}")

        with open(config_file, "r") as f:
            data = yaml.safe_load(f)

        return cls(
            moppy_api=MoppyApiConfig(**data["moppy_api"]),
            buttons=[ButtonConfig(**btn) for btn in data["buttons"]],
            gpio=GpioConfig(**data["gpio"]),
            logging=LoggingConfig(**data["logging"]),
        )

    def validate(self) -> None:
        """Validate the configuration."""
        if not self.buttons:
            raise ValueError("At least one button must be configured")

        pins = [btn.pin for btn in self.buttons]
        if len(pins) != len(set(pins)):
            raise ValueError("Duplicate pin numbers are not allowed")

        for button in self.buttons:
            if not Path(button.file).exists():
                raise FileNotFoundError(f"MIDI file not found: {button.file}")
