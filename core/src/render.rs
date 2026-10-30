//! Rendering utilities for PRISM tiles to PNG images.

pub fn render_static_tile_to_png(_tile: &[u8], _dpi: u32, _path: &std::path::Path) -> Result<(), String> {
    // Placeholder: real implementation would render the tile to a PNG.
    Ok(())
}

pub fn render_kinetic_face_to_png(_tile: &[u8], _path: &std::path::Path) -> Result<(), String> {
    Ok(())
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct StaticManifest {
    pub file_name: String,
    pub size_bytes: u64,
    pub sha256: String,
    pub tiles: u32,
    pub pages: u32,
    pub dpi: u32,
    pub config: crate::codec::session_config::SessionConfig,
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct KineticManifest {
    pub file_name: String,
    pub size_bytes: u64,
    pub sha256: String,
    pub faces: u32,
    pub face_bytes: usize,
    pub tiles: u32,
    pub config: crate::codec::session_config::SessionConfig,
}
