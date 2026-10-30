# Photon Transfer Protocol — Optical Data Transfer via Fountain Coding

Photon Transfer Protocol is an
optical data transfer system that moves files between a source display and a
destination camera with **no back-channel**. Every frame must be useful — so
Photon Transfer Protocol uses **fountain codes (RaptorQ/LT)** as the primary transport, with QR as a
fallback for small payloads.

## How it works

Photon Transfer Protocol splits data into three transport systems:

| System | Transport | Use case |
|--------|-----------|----------|
| **SPC** | Static Printable Code | Progressive static mosaic (A4 @ 600-1200dpi, 200-500KB/page) |
| **DRM** | Dynamic Rotating Mass | Face-rate-matched kinetic fountain (8fps → 30fps, 46KB/s → 720KB/s) |
| **QR**  | QR Code fallback | Small payloads when custom symbol decode fails |

### Architecture (7 layers)

1. **Pre-processing** — zstd/XZ compression → SHA-256 hashing → AES-256-GCM encryption
2. **Fountain coding** — LT codes (first priority) + RaptorQ for redundancy
3. **CGG modulation** — 16 geometric shapes × 4-8 colors = 65-128 distinct symbols
4. **Display streaming** — 60-120 FPS output synchronized to display refresh
5. **Capture/decode** — CameraX + JNI native decoder pool (saturates 60 FPS)
6. **Reconstruction** — Peeling decoder → SHA-256 verification → reassembly
7. **AI/ML plane** — Shared cognitive layer (13 models M1-M13) across Photon Transfer Protocol and Agentic Workbench

### Repository layout

```
prism/
├── core/                 # Rust codec core (fountain, encryption, JNI bridge)
│   ├── Cargo.toml        # photon-transfer-protocol_core crate (cdylib for JNI)
│   ├── src/
│   │   ├── lib.rs        # Library entry point + self_test()
│   │   ├── cli.rs        # Clap-based CLI (encode/inspect/selftest)
│   │   ├── bin/prism/    # CLI binary (main.rs + encode commands)
│   │   ├── codec/        # SessionConfig, AlphabetMode, Encoder, Decoder
│   │   ├── jni/          # JNI bridge (CodecBridgeImpl native methods)
│   │   └── render.rs     # Symbol rendering helpers
│   └── tests/            # Integration tests (round-trip, JNI smoke, CLI)
├── android/              # Android app (Kotlin + XML)
│   ├── app/src/main/
│   │   ├── java/com/prism/ot/
│   │   │   ├── core/      # PrismConstants, PrismError, PrismLogger, PrismResult
│   │   │   ├── codec/     # CodecBridge, PrismCodec, Alphabet, Fountain codecs
│   │   │   ├── capture/   # CameraX integration, frame analysis
│   │   │   ├── session/   # Transfer orchestration + state machine
│   │   │   ├── ui/        # 4 ViewModels + 5 screens (Jetpack Compose)
│   │   │   ├── ml/        # TFLite model management (M1-M13)
│   │   │   ├── decoder/   # Native decoder pool
│   │   │   ├── transfer/  # Data model (FrameData, FountainPacket, ChannelState)
│   │   │   ├── settings/  # User preferences
│   │   │   └── widgets/   # Custom UI components
│   │   └── res/           # Drawables, layouts, values, XML configs
│   └── build.gradle.kts   # Gradle build scripts + version catalog
├── tools/                # Build and deployment scripts
├── .github/              # CI workflows + issue/PR templates
└── IDEA.txt              # Full 702KB design specification
```

## Quick start

### Prerequisites

- **Rust** 1.75+ (with `aarch64-linux-android` target)
- **Android NDK** 27.x
- **JDK** 17
- **Android Studio** Ladybug (2024.2.1)+ (for app builds)

### Build the Rust core

```bash
cd core
cargo build --release --target aarch64-linux-android
```

### Run tests

```bash
cd core
cargo test --all-targets
cargo test --release
```

### CLI usage

```bash
# Encode a static printable code
prism encode static --input file.bin --output page.png --mode spc

# Encode a kinetic fountain stream
prism encode kinetic --input file.bin --fps 30 --duration 10

# Inspect a Photon Transfer Protocol symbol
photon inspect --symbol ./captured_frame.png

# Self-test
photon selftest
```

### Android app

```bash
cd android/app
./gradlew assembleDebug
```

## Key features

- **No back-channel required** — fountain codes ensure forward error correction
- **SHA-256 verification** — every file is verified before delivery
- **Native decoder pool** — saturates 60 FPS capture with parallel JNI decoders
- **QR fallback** — graceful degradation when custom symbols fail to decode
- **AES-256-GCM encryption** — end-to-end security for sensitive transfers
- **Cross-app profiles** — v1.1.0 JSON profile format shared with KotMoE and Agentic Workbench

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture details, coding
standards, and how to set up your development environment.

## License

Apache-2.0 — see [LICENSE](LICENSE).
  