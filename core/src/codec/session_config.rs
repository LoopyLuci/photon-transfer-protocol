//! Session configuration for the PRISM codec.

use crate::codec::alphabet_mode::AlphabetMode;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct SessionConfig {
    pub format_version: u32,
    pub alphabet_mode: AlphabetMode,
    pub segment_bytes: usize,
    pub symbol_bytes: usize,
    pub target_overhead: f32,
    pub _padding: u32,
}

impl Default for SessionConfig {
    fn default() -> Self {
        Self {
            format_version: 1,
            alphabet_mode: AlphabetMode::Dense64,
            segment_bytes: 32 * 1024 * 1024,
            symbol_bytes: 7_500,
            target_overhead: 1.30,
            _padding: 0,
        }
    }
}
