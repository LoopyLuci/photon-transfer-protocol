use anyhow::{Context, Result};
use prism_core::codec::{AlphabetMode, Encoder, SessionConfig};
use std::fs;
use std::path::PathBuf;

pub fn run(args: EncodeStaticArgs) -> Result<()> {
    let input = fs::read(&args.input)
        .with_context(|| format!("reading {}", args.input.display()))?;

    fs::create_dir_all(&args.output)
        .with_context(|| format!("creating {}", args.output.display()))?;

    let config = SessionConfig {
        format_version: 1,
        alphabet_mode: match args.alphabet {
            AlphabetArg::Dense64 => AlphabetMode::Dense64,
            AlphabetArg::DensePlus128 => AlphabetMode::DensePlus128,
            AlphabetArg::Readable52 => AlphabetMode::Readable52,
        },
        segment_bytes: 32 * 1024 * 1024,
        symbol_bytes: 7_500,
        target_overhead: args.overhead,
        ..Default::default()
    };

    let mut encoder = Encoder::new(config.clone()).context("creating encoder")?;
    let tiles = encoder.encode(&input).context("encoding payload")?;

    let pages = (tiles.len() as u32 + args.tiles_per_page - 1) / args.tiles_per_page;
    for (i, tile) in tiles.iter().enumerate() {
        let page = i as u32 / args.tiles_per_page;
        let path = args.output.join(format!("page-{page:03}.png"));
        // The renderer is shared with the Android asset pipeline.
        prism_core::render::render_static_tile_to_png(tile, args.dpi, &path)
            .with_context(|| format!("rendering {}", path.display()))?;
    }

    let manifest = prism_core::render::StaticManifest {
        file_name: args.input.file_name().unwrap().to_string_lossy().to_string(),
        size_bytes: input.len() as u64,
        sha256: prism_core::hash::sha256_hex(&input),
        tiles: tiles.len() as u32,
        pages,
        dpi: args.dpi,
        config,
    };
    let manifest_path = args.output.join("manifest.json");
    fs::write(&manifest_path, serde_json::to_vec_pretty(&manifest)?)
        .with_context(|| format!("writing {}", manifest_path.display()))?;

    println!(
        "Encoded {} bytes → {} tiles across {} pages at {} dpi",
        input.len(),
        tiles.len(),
        pages,
        args.dpi
    );
    Ok(())
}
