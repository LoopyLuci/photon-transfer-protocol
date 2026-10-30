# Photon Transfer Protocol Architecture

This document explains the architecture of Photon Transfer Protocol in detail. It is intended for
contributors who want to understand how the system fits together.

## System overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Photon Transfer Protocol System Architecture                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐              │
│  │   Source     │     │   Display    │     │   Camera     │              │
│  │   (File)     │────▶│   (Screen)   │────▶│   (Capture)  │              │
│  └──────────────┘     └──────────────┘     └──────────────┘              │
│         │                    │                    │                        │
│         ▼                    ▼                    ▼                        │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │                    Photon Transfer Protocol Core (Rust)                          │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │        │
│  │  │ Pre-proc │ │ Fountain │ │  CGG     │ │  JNI     │         │        │
│  │  │ (zstd)   │ │ (RaptorQ)│ │  Mod     │ │  Bridge  │         │        │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘         │        │
│  └──────────────────────────────────────────────────────────────┘        │
│         │                    │                    │                        │
│         ▼                    ▼                    ▼                        │
│  ┌──────────────────────────────────────────────────────────────┐        │
│  │                   Android App (Kotlin)                        │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │        │
│  │  │ CameraX  │ │  Codec   │ │   UI     │ │   ML     │         │        │
│  │  │ Capture  │ │  Bridge  │ │  (Jetpack)│ │  (TFLite)│        │        │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘         │        │
│  └──────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘
```

## Transport systems

### SPC — Static Printable Code

- **Use case**: Progressive static mosaic printed on A4 paper
- **Capacity**: 200-500 KB per page at 600-1200 dpi
- **Encoding**: Zstd compression → Fountain coding → CGG modulation → PNG output
- **Decoding**: PNG scan → CGG detection → Fountain peeling → Decompression

### DRM — Dynamic Rotating Mass

- **Use case**: Kinetic fountain stream displayed at 8-30 FPS
- **Capacity**: 46 KB/s at 8fps → 720 KB/s at 30fps
- **Encoding**: Fountain coding → CGG modulation → Frame buffer → Display
- **Decoding**: Camera capture → Frame analysis → CGG detection → Fountain peeling

### QR — QR Code fallback

- **Use case**: Small payloads when custom symbol decode fails
- **Capacity**: Up to 2953 bytes per QR code (Version 40, error correction L)
- **Encoding**: Standard QR encoding
- **Decoding**: Camera capture → QR detection (ZBar/ML Kit)

## 7-layer pipeline

### Layer 1: Pre-processing

```
Input file
    │
    ▼
┌─────────────────┐
│  zstd/XZ        │  Compression (optional)
│  compression    │
└─────────────────┘
    │
    ▼
┌─────────────────┐
│  SHA-256 hash   │  Integrity verification
└─────────────────┘
    │
    ▼
┌─────────────────┐
│  AES-256-GCM    │  Encryption (optional)
└─────────────────┘
    │
    ▼
Compressed/encrypted data
```

### Layer 2: Fountain coding

```
Data
    │
    ▼
┌─────────────────┐
│  LT codes       │  First priority (no back-channel)
│  (RaptorQ)      │  Each symbol is useful
└─────────────────┘
    │
    ▼
Fountain symbols (independent, redundant)
```

**Why fountain codes?**

Photon Transfer Protocol has **no back-channel** — the source cannot know which symbols the
destination received. Fountain codes ensure that **any sufficient subset** of
symbols can reconstruct the original data, making every frame useful.

### Layer 3: CGG modulation

```
Fountain symbols
    │
    ▼
┌─────────────────┐
│  CGG (Color-    │  16 geometric shapes × 4-8 colors = 65-128 symbols
│  Geometric-     │
│  Glyph) encoder │
└─────────────────┘
    │
    ▼
CGG symbols (renderable as images)
```

**CGG alphabet**:

| Shapes | Count |
|--------|-------|
| Circle | 1 |
| Square | 1 |
| Triangle | 1 |
| Diamond | 1 |
| Hexagon | 1 |
| ... | ... |
| Total | 16 |

| Colors | Count |
|--------|-------|
| Black | 1 |
| Red | 1 |
| Green | 1 |
| Blue | 1 |
| ... | ... |
| Total | 4-8 |

**Total alphabet**: 16 × 4 = 64 symbols (minimum) to 16 × 8 = 128 symbols (maximum)

### Layer 4: Display streaming

```
CGG symbols
    │
    ▼
┌─────────────────┐
│  Frame buffer   │  60-120 FPS output
│  (sync to       │  synchronized to display refresh
│   display)      │
└─────────────────┘
    │
    ▼
Display (screen)
```

### Layer 5: Capture/decode

```
Display (screen)
    │
    ▼
┌─────────────────┐
│  CameraX        │  Native capture at 60 FPS
│  capture        │
└─────────────────┘
    │
    ▼
┌─────────────────┐
│  Frame          │  Parallel JNI decoder pool
│  analysis       │  saturates capture rate
└─────────────────┘
    │
    ▼
Detected CGG symbols
```

### Layer 6: Reconstruction

```
CGG symbols
    │
    ▼
┌─────────────────┐
│  Peeling        │  Fountain decoder
│  decoder        │
└─────────────────┘
    │
    ▼
┌─────────────────┐
│  SHA-256        │  Verify before delivery
│  verification   │
└─────────────────┘
    │
    ▼
Reconstructed data
```

### Layer 7: AI/ML plane

```
┌─────────────────────────────────────────────────────────────┐
│                    Shared AI/ML plane                         │
│                                                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ M1-M4    │ │ M5-M8    │ │ M9-M11   │ │ M12-M13  │        │
│  │ Detection│ │ Tracking │ │ ML Kit   │ │ Custom   │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└─────────────────────────────────────────────────────────────┘
```

**Models M1-M13**:

| Models | Purpose |
|--------|---------|
| M1-M4 | Symbol detection (CGG, QR, pilot markers) |
| M5-M8 | Frame tracking and alignment |
| M9-M11 | ML Kit integration (barcode, text) |
| M12-M13 | Custom neural networks |

## JNI bridge

The JNI bridge connects Kotlin (Android) to Rust (codec core):

```kotlin
// Kotlin side
class CodecBridgeImpl : CodecBridge {
    override fun createEncoder(config: SessionConfig): Long {
        return nativeCreateEncoder(config)
    }

    external fun nativeCreateEncoder(config: SessionConfig): Long
}
```

```rust
// Rust side
#[no_mangle]
pub extern "system" fn Java_com_prism_ot_codec_CodecBridgeImpl_nativeCreateEncoder(
    env: JNIEnv, _class: JClass, config: JObject,
) -> jlong {
    // Deserialize SessionConfig, build Encoder, Box::into_raw as jlong
}
```

## Data flow

### Encoding (SPC)

```
File → zstd → SHA-256 → AES-GCM → RaptorQ → CGG → PNG → Display
```

### Decoding (SPC)

```
PNG → CGG detect → RaptorQ peel → AES-GCM decrypt → SHA-256 verify → zstd decompress → File
```

### Encoding (DRM)

```
File → zstd → SHA-256 → AES-GCM → RaptorQ → CGG → Frame buffer → Display
```

### Decoding (DRM)

```
Camera → Frame analysis → CGG detect → RaptorQ peel → AES-GCM decrypt → SHA-256 verify → zstd decompress → File
```

## Testing strategy

### Rust tests

```rust
// core/tests/codec_round_trip.rs
#[test]
fn test_encode_decode_round_trip() {
    let payload = b"test data";
    let config = SessionConfig::default();
    let mut encoder = Encoder::new(config.clone()).unwrap();
    let tiles = encoder.encode(payload).unwrap();

    let mut decoder = Decoder::new(config).unwrap();
    for tile in tiles {
        decoder.ingest(&tile).unwrap();
    }
    let result = decoder.take_file().unwrap().unwrap();
    assert_eq!(result, payload);
}
```

### Kotlin tests

```kotlin
// android/app/src/test/java/com/prism/ot/codec/PhotonCodecTest.kt
@Test
fun testEncodeDecodeRoundTrip() {
    val payload = "test data".toByteArray()
    val config = SessionConfig()
    val encoder = PhotonCodec.createEncoder(config)
    val tiles = encoder.encode(payload)

    val decoder = PhotonCodec.createDecoder(config)
    tiles.forEach { decoder.ingest(it) }
    val result = decoder.takeFile()
    assertEquals(payload.toList(), result.toList())
}
```

## Performance targets

| Metric | Target |
|--------|--------|
| Encoding speed | 100+ MB/s |
| Decoding speed | 60+ FPS |
| Fountain overhead | < 10% |
| Memory usage | < 100 MB |
| JNI call latency | < 1 ms |

## Future directions

- **WebAssembly** target for browser-based encoding
- **iOS** port via Swift + JNI bridge
- **Hardware acceleration** (GPU) for CGG modulation
- **Adaptive modulation** based on camera quality
- **Multi-stream** parallel decoding
