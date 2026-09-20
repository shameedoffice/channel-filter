# Channel Filter Camera — Web

Browser-based RGB / Red / Green / Blue channel-isolation camera for visual display calibration.

Open `index.html` from an HTTPS web server, then use Chrome on Android and allow camera access. For AVS HD 709 flashing color bars, select **BLUE**.

The app uses the browser camera API and Canvas, saves the filtered image as PNG, and attempts manual exposure/white-balance controls when the browser/device exposes them. Browser APIs do not provide Android Camera2 RAW_SENSOR access, so this is the processed-camera version rather than the native RAW implementation.
