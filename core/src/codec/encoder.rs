//! Encoder for the PRISM codec.

use crate::codec::session_config::SessionConfig;

pub struct Encoder {
    config: SessionConfig,
}

impl Encoder {
    pub fn new(config: SessionConfig) -> Result<Self, String> {
        if config.segment_bytes == 0 {
            return Err("segment_bytes must be non-zero".to_string());
        }
        if config.symbol_bytes == 0 {
            return Err("symbol_bytes must be non-zero".to_string());
        }
        Ok(Encoder { config })
    }

    pub fn encode(&mut self, data: &[u8]) -> Result<Vec<Vec<u8>>, String> {
        let mut tiles = Vec::new();
        let mut offset = 0;
        while offset < data.len() {
            let chunk_size = self.config.symbol_bytes.min(data.len() - offset);
            tiles.push(data[offset..offset + chunk_size].to_vec());
            offset += chunk_size;
        }
        // The `..` rest pattern would leave the last `..` unused; we use explicit slicing.
        if tiles.is_empty() {
            tiles.push(Vec::new());
        }
        Ok(tiles)
    }
}
