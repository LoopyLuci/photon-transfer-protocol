//! prism_core — the PRISM optical transport codec core.
//!
//! This crate implements the full PRISM optical data transport pipeline:
//! pre-processing, fountain coding, CGG symbol alphabet, HAP-MLC partition
//! tree, NB-LDPC inner code, JNI bridge, and CLI utilities.

pub mod codec;
pub mod jni;
pub mod render;
pub mod cli;

/// Returns true if the codec core can encode and decode a tiny payload.
pub fn self_test() -> bool {
    let payload = b"prism-self-test";
    let config = codec::SessionConfig {
        segment_bytes: 64,
        symbol_bytes: 32,
        ..Default::default()
    };
    let Ok(mut enc) = codec::Encoder::new(config.clone()) else { return false };
    match enc.encode(payload) {
        Ok(tiles) => {
            let Ok(mut dec) = codec::Decoder::new(config) else { return false };
            for tile in tiles {
                if dec.ingest(&tile).unwrap_or(false) {
                    return dec.take_file().ok().flatten().as_deref() == Some(payload.as_slice());
                }
            }
            false
        }
        Err(_) => false,
    }
}
