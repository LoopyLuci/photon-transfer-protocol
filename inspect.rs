// inspect.rs
use anyhow::{Context, Result};
use std::fs;

pub fn run(args: InspectArgs) -> Result<()> {
    let manifest_path = if args.path.is_dir() {
        args.path.join("manifest.json")
    } else {
        args.path.clone()
    };
    let raw = fs::read_to_string(&manifest_path)
        .with_context(|| format!("reading {}", manifest_path.display()))?;
    let value: serde_json::Value = serde_json::from_str(&raw)?;
    println!("{}", serde_json::to_string_pretty(&value)?);
    Ok(())
}

// self_test.rs
pub fn run() -> anyhow::Result<()> {
    if prism_core::self_test() {
        println!("self-test passed");
        Ok(())
    } else {
        anyhow::bail!("self-test failed")
    }
}
