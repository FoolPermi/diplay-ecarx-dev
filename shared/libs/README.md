# ECARX AdaptAPI

`ecarx.adapterapi.jar` is the community-provided compilation library for 820A / 20-05 / 753,
downloaded on 2026-11-01 from:

https://jimu.uestcit.com/docs/jars/20-05/820A/753/ecarx.adapterapi.jar

Documentation: https://jimu.uestcit.com/#/docs/20-05/820A/753/integration

SHA-256: `927c8b11194977c1fbf433e606bd228524964983b1355946e5bd4809de6d0b4f`

The file contains JVM `.class` files, unlike the firmware JAR containing `classes.dex`.
Use `compileOnly` in production: the head unit supplies the implementation. Never package
these classes in an APK. Shared-module tests use the JAR and target-firmware fixtures;
common-module tests deliberately omit it to exercise ordinary Android without vendor APIs.

This JAR covers AdaptAPI. The GKUI Policy contract is recovered separately in
[`sdk/ecarx-policy`](../../sdk/ecarx-policy/README.zh-CN.md), retained for reference and not
used by the application. SDK microphone policy, MediaCenter, Android hidden APIs and firmware-specific extensions without a matching
compilation library still use reflection.

The target framework dump reports AdaptAPI 1.1.18.9, rather than the 1.1.17 declarations
bundled in older apps. See [the SDK and microphone context](../../docs/ECARX_SDK_CONTEXT.zh-CN.md)
for the ABI check, GKUI initialization lifecycle and the target server's unsupported mic methods.
