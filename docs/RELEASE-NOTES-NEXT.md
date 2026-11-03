# DiPlay next release notes

Changes through DiPlay 0.2.22 are documented in [0.2.22 release notes](RELEASE-NOTES-0.2.22.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

## Unreleased — ECARX 0.2 (versionCode 114)

The unreleased ECARX changes are maintained in the “0.2 — 未发布” section of the [changelog](../CHANGELOG.md). In short:

- **No ADB.** The in-app ADB client, key handling, port-5555 connections and shell helpers are gone. Boot start, hotspot control, log access, the wheel key service and USB/map permissions use platform permissions, system settings entry points, or fail explicitly; the five new platform permissions are read back in the in-app permission check. A v114 release APK has been built and checked off-vehicle.
- **Navigation output lifecycle.** Route ownership and published output are separate states, so hiding the cluster map no longer ends guidance; cluster exit waits for the phone to report the route finished; scheduled recovery bypasses the SDK de-duplication cache.
- **ETA units.** `updateEta` receives remaining seconds instead of an epoch value, and an unknown ETA is no longer sent as 0.
- **Cluster map.** The independent map survives layout changes and OEM visibility callbacks; the initial map visibility switch now only decides each new route's starting state, and on-cluster show/hide wins.
- **Bluetooth and microphone.** Media yield is tracked per phone across reconnects, and the GKUI microphone policy lifecycle is completed with a direct-recording fallback.

Pending on-vehicle acceptance: full reboot after removing ADB, USB/map permission paths, wheel key service binding, Wi-Fi Direct recovery, hotspot control with debugging off, and route start/end across the supported maps.
