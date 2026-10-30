//! Symbol alphabet modes for the PRISM codec.

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize)]
pub enum AlphabetMode {
    Dense64,
    DensePlus128,
    Readable52,
}
