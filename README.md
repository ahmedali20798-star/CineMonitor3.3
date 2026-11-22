# CineMonitor Pro v2.9 (CrashProof + Zero-Alloc Bitmap Pool)

Direct USB PTP Live View & Cinema Field Monitor for **Canon EOS** cameras on Android, powered by **OpenGL ES 3.0** and **Jetpack Compose**.

## Key Features
- **Direct USB Host PTP Streaming (`0x9153`):** Real-time Live View without HDMI capture cards.
- **Zero-Allocation Triple-Buffer `inBitmap` Pool:** Eliminates Garbage Collector (GC) stutters and keeps Native Heap steady during high-FPS monitoring.
- **OpenGL ES 3.0 Cinema Pipeline:**
  - Real-time 3D `.cube` LUT loader & intensity mixer (preserved across screen lock/rotation).
  - False Color exposure map, Focus Peaking (Sobel edge detection), Animated Zebra stripes, Monochrome mode, H/V Flip (180° rig mode), and Anamorphic De-squeeze (`1.0x` – `2.0x`).
- **Camera Telemetry & Remote Control:** Live Shutter, Iris, ISO, Kelvin WB, Battery/AC status, Video REC trigger, and Aspect-Synced Touch Autofocus.

## Project Structure
- `app/src/main/java/com/ahmedmahmoud/cinemonitor/MainActivity.kt` — USB Host lifecycle, permission handling, and async connection manager.
- `app/src/main/java/com/ahmedmahmoud/cinemonitor/camera/` — Canon PTP protocol engine, event parser, exposure lookup tables, and Touch AF mapper.
- `app/src/main/java/com/ahmedmahmoud/cinemonitor/gl/` — OpenGL ES 3.0 renderer, `.cube` parser, and 3D texture uploader.
- `app/src/main/java/com/ahmedmahmoud/cinemonitor/ui/` — Jetpack Compose cinema HUD, frame guides, and touch AF reticle.

## Build Requirements
- Android Studio Iguana / Koala or newer
- JDK 17
- Android SDK 34 (`minSdk = 26`, `targetSdk = 33`)
