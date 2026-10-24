# RF Physiology Research (real-data starter)

This Android app collects **actual Android TelephonyManager cell telemetry** and exports it as CSV. It does not generate simulated measurements. It does not access raw cellular I/Q samples, and it does not measure or estimate actual EEG alpha/beta/theta/gamma bands.

## Build

GitHub Actions builds a debug APK on every push. Open the Actions tab and download the `rf-physiology-debug-apk` artifact.

## Permissions

The app asks for phone-state and fine-location permissions because Android restricts cell identity access. Availability depends on OS version, carrier, modem, permissions, and vendor behavior. Data may be cached or rate-limited.

## Scientific limits

Cell IDs, EARFCN/NRARFCN, dBm, RSRP, RSRQ, and SINR are modem-reported telemetry, not raw RF waveforms. This project currently has no camera pupil tracking, no EEG acquisition, and no physiological inference. Camera correlation is a future research extension that must use real synchronized measurements and independent validation.

## Security

Never commit access tokens. The upload script uses `GITHUB_TOKEN` from the environment or an existing `gh auth token`; it never embeds credentials in source files. Revoke any token accidentally disclosed in chat, logs, or source code.
