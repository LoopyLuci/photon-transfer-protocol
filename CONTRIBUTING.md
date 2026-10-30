# Contributing to Photon Transfer Protocol

Thanks for your interest in Photon Transfer Protocol! This document explains how to set up your
development environment, the architecture, and how to contribute.

## Architecture overview

Photon Transfer Protocol is a **Rust + Kotlin** project:

- **`core/`** — Rust codec core (fountain codes, encryption, JNI bridge)
- **`android/`** — Kotlin Android app (CameraX, UI, orchestration)
- **`tools/`** — Build and deployment scripts

### Why Rust for the codec?

The codec core needs:
- **Low-latency** symbol encoding/decoding (60+ FPS)
- **Memory safety** for JNI bridges
- **Cross-platform** compilation (Android NDK + desktop)

Rust provides all three with zero-cost abstractions.

### Why Kotlin for the app?

The Android app needs:
- **CameraX** integration (Android native)
- **Jetpack Compose** UI
- **TFLite** model management

Kotlin provides all three with idiomatic Android APIs.

## Setting up your environment

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Rust | 1.75+ | `rustup` recommended |
| Android NDK | 27.x | `ANDROID_NDK` env var |
| JDK | 17 | `JAVA_HOME` env var |
| Android Studio | Ladybug (2024.2.1)+ | For app builds |
| Gradle | 9.x | Bundled with Android Studio |

### Rust setup

```bash
# Install Rust via rustup
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Add Android targets
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
```

### Android setup

```bash
# Set environment variables
export ANDROID_NDK=/path/to/android-ndk/27.3.13750724
export JAVA_HOME=/path/to/jdk-17

# Verify
$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --version
```

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

### Build the Android app

```bash
cd android/app
./gradlew assembleDebug
```

## Coding standards

### Rust (`core/`)

- **Format**: `cargo fmt` (default Rust style)
- **Lint**: `cargo clippy -- -D warnings`
- **Tests**: All public functions must have unit tests
- **Docs**: All public APIs must have `///` doc comments
- **Errors**: Use `Result<T, String>` for user-facing errors, `anyhow::Result` for internal

### Kotlin (`android/`)

- **Format**: `ktlint` (default Android style)
- **Lint**: `./gradlew lint`
- **Tests**: All ViewModels must have unit tests
- **Docs**: All public classes/methods must have KDoc
- **Errors**: Use sealed classes for error types

## Project structure

### Core modules

```
core/src/
├── lib.rs          # Library entry point
├── cli.rs          # Clap CLI definitions
├── codec/
│   ├── mod.rs      # Module re-exports
│   ├── session_config.rs  # SessionConfig struct
│   ├── alphabet_mode.rs   # AlphabetMode enum
│   ├── encoder.rs  # Encoder implementation
│   └── decoder.rs  # Decoder implementation
├── jni/
│   ├── mod.rs      # JNI module entry
│   └── codec.rs    # JNI bridge functions
└── render.rs       # Symbol rendering helpers
```

### Android packages

```
android/app/src/main/java/com/prism/ot/
├── core/           # Constants, errors, logger
├── codec/          # Codec bridge, fountain codecs
├── capture/        # CameraX integration
├── session/        # Transfer orchestration
├── ui/             # ViewModels + screens
├── ml/             # TFLite model management
├── decoder/        # Native decoder pool
├── transfer/       # Data models
├── settings/       # User preferences
└── widgets/        # Custom UI components
```

## How to contribute

### 1. Fork and clone

```bash
git clone https://github.com/your-username/prism.git
cd prism
```

### 2. Create a branch

```bash
git checkout -b feature/your-feature-name
```

### 3. Make changes

Follow the coding standards above. Write tests for new functionality.

### 4. Run tests

```bash
# Rust tests
cd core
cargo test --all-targets

# Android tests
cd ../android/app
./gradlew testDebugUnitTest
```

### 5. Submit a PR

Push your branch and open a PR against `main`. Include:
- A clear description of the change
- Test results
- Any breaking changes

## Reporting issues

Use the GitHub issue templates:
- **Bug report** — for reproducible bugs
- **Feature request** — for new capabilities

## License

By contributing, you agree that your contributions will be licensed under the
Apache-2.0 license. See [LICENSE](LICENSE) for details.
