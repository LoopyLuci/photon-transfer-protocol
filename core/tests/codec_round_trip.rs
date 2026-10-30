//! Host-side integration tests for the codec core.
//!
//! These run with plain `cargo test` on the host and exercise the
//! encode→decode round trip without JNI. The JNI layer is a thin
//! translation layer; the correctness lives here.

use prism_core::codec::{Decoder, Encoder, SessionConfig, AlphabetMode};

#[test]
fn round_trip_single_segment() {
    let payload: Vec<u8> = (0..4096u32).map(|i| (i % 251) as u8).collect();
    let config = SessionConfig {
        format_version: 1,
        alphabet_mode: AlphabetMode::Dense64,
        segment_bytes: 4096,
        symbol_bytes: 512,
        target_overhead: 1.30,
        ..Default::default()
    };

    let mut encoder = Encoder::new(config.clone()).unwrap();
    let tiles = encoder.encode(&payload).unwrap();

    let mut decoder = Decoder::new(config).unwrap();
    let mut reconstructed = None;
    for tile in tiles {
        if decoder.ingest(&tile).unwrap() {
            reconstructed = decoder.take_file().unwrap();
            break;
        }
    }

    assert_eq!(reconstructed.as_deref(), Some(payload.as_slice()));
}

#[test]
fn round_trip_with_dropped_tiles() {
    let payload: Vec<u8> = (0..8192u32).map(|i| (i % 253) as u8).collect();
    let config = SessionConfig {
        format_version: 1,
        alphabet_mode: AlphabetMode::Dense64,
        segment_bytes: 8192,
        symbol_bytes: 512,
        target_overhead: 1.30,
        ..Default::default()
    };

    let mut encoder = Encoder::new(config.clone()).unwrap();
    let tiles = encoder.encode(&payload).unwrap();

    // Drop every third tile. Fountain coding must recover.
    let mut decoder = Decoder::new(config).unwrap();
    let mut result = None;
    for (i, tile) in tiles.iter().enumerate() {
        if i % 3 == 2 { continue; }
        if decoder.ingest(tile).unwrap() {
            result = decoder.take_file().unwrap();
            break;
        }
    }

    assert_eq!(result.as_deref(), Some(payload.as_slice()));
}

#[test]
fn rejects_truncated_payload() {
    let payload = vec![0xAB; 1000];
    let config = SessionConfig {
        segment_bytes: 2048,
        symbol_bytes: 256,
        ..Default::default()
    };

    let mut encoder = Encoder::new(config.clone()).unwrap();
    let mut tiles = encoder.encode(&payload).unwrap();
    tiles.pop(); // remove one tile

    let mut decoder = Decoder::new(config).unwrap();
    for tile in &tiles {
        decoder.ingest(tile).unwrap();
    }
    assert!(decoder.take_file().unwrap().is_none());
}
