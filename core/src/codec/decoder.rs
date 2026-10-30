//! Decoder for the PRISM codec.

use crate::codec::session_config::SessionConfig;

pub struct Decoder {
    config: SessionConfig,
    received: Vec<u8>,
}

impl Decoder {
    pub fn new(config: SessionConfig) -> Result<Self, String> {
        if config.segment_bytes == 0 {
            return Err("segment_bytes must be non-zero".to_string());
        }
        Ok(Decoder {
            config,
            received: Vec::new(),
        })
    }

    pub fn ingest(&mut self, tile: &[u8]) -> Result<bool, String> {
        self.received.extend_from_slice(tile);
        Ok(true)
    }

    pub fn take_file(self) -> Result<Option<Vec<u8>>, String> {
        Ok(Some(self.received))
    }
}
