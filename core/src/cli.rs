//! CLI definition for the PRISM CLI tool.

use clap::{Args, Parser, Subcommand, ValueEnum};
use std::path::PathBuf;

#[derive(Parser, Debug)]
#[command(name = "prism", version, about = "PRISM optical transport encoder")]
pub struct Cli {
    #[command(subcommand)]
    pub command: Command,
}

#[derive(Subcommand, Debug)]
pub enum Command {
    EncodeStatic(EncodeStaticArgs),
    EncodeKinetic(EncodeKineticArgs),
    Inspect(InspectArgs),
    SelfTest,
}

#[derive(Args, Debug)]
pub struct EncodeStaticArgs {
    pub input: PathBuf,
    pub output: PathBuf,
    pub dpi: u32,
    pub tiles_per_page: u32,
    pub alphabet: AlphabetArg,
    pub overhead: f32,
}

#[derive(Args, Debug)]
pub struct EncodeKineticArgs {
    pub input: PathBuf,
    pub output: PathBuf,
    pub faces: u32,
    pub face_bytes: usize,
    pub alphabet: AlphabetArg,
    pub overhead: f32,
}

#[derive(Args, Debug)]
pub struct InspectArgs {
    pub path: PathBuf,
}

#[derive(ValueEnum, Clone, Copy, Debug)]
pub enum AlphabetArg {
    Dense64,
    DensePlus128,
    Readable52,
}
