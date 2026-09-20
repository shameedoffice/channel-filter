# Channel Filter

Android 13+ (API 33+) visual channel-isolation aid for display calibration. Target SDK 36.

Modes: RGB, Red, Green, Blue. Captures are saved to Pictures/ChannelFilter on modern Android.

The processed mode is the primary calibration path. Blue mode is intended for AVS HD 709-style flashing color bars. RAW capability is detected through Camera2; actual RAW_SENSOR capture/processing should be treated as device-dependent rather than guaranteed live preview.
