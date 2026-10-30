use anyhow::{Context, Result};
use prism_core::codec::{AlphabetMode, Encoder, SessionConfig};
use std::fs;
use std::path::PathBuf;

pub fn run(args: EncodeKineticArgs) -> Result<()> {
    let input = fs::read(&args.input)
        .with_context(|| format!("reading {}", args.input.display()))?;
    fs::create_dir_all(&args.output)?;

    let config = SessionConfig {
        format_version: 1,
        alphabet_mode: match args.alphabet {
            AlphabetArg::Dense64 => AlphabetMode::Dense64,
            AlphabetArg::DensePlus128 => AlphabetMode::DensePlus128,
            AlphabetArg::Readable52 => AlphabetMode::Readable52,
        },
        segment_bytes: 32 * 1024 * 1024,
        symbol_bytes: args.face_bytes,
        target_overhead: args.overhead,
        ..Default::default()
    };

    let mut encoder = Encoder::new(config.clone()).context("creating encoder")?;
    let tiles = encoder.encode(&input).context("encoding payload")?;

    // Faces are tiles modulo the face count: face i carries tiles
    // i, i+F, i+2F, … so that every face is an independent fountain
    // stream position.
    for (i, tile) in tiles.iter().enumerate() {
        let face = i as u32 % args.faces;
        let seq = i as u32 / args.faces;
        let path = args.output.join(format!("face-{face:02}-{seq:04}.png"));
        prism_core::render::render_kinetic_face_to_png(tile, &path)?;
    }

    let manifest = prism_core::render::KineticManifest {
        file_name: args.input.file_name().unwrap().to_string_lossy().to_string(),
        size_bytes: input.len() as u64,
        sha256: prism_core::hash::sha256_hex(&input),
        faces: args.faces,
        face_bytes: args.face_bytes,
        tiles: tiles.len() as u32,
        config,
    };
    fs::write(
        args.output.join("manifest.json"),
        serde_json::to_vec_pretty(&manifest)?,
    )?;

    println!(
        "Encoded {} bytes → {} faces × {} tiles",
        input.len(),
        args.faces,
        tiles.len() as u32 / args.faces
    );
    Ok(())
}
