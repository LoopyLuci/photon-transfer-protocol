use clap::Parser;
use prism_core::cli::{Cli, Command};

mod commands;

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    // Exit codes are a real contract with the shell. 0 = success,
    // 1 = user error (bad input), 2 = I/O error, 3 = codec error.
    let outcome = match cli.command {
        Command::EncodeStatic(args) => commands::encode_static::run(args),
        Command::EncodeKinetic(args) => commands::encode_kinetic::run(args),
        Command::Inspect(args) => commands::inspect::run(args),
        Command::SelfTest => commands::self_test::run(),
    };

    match outcome {
        Ok(()) => Ok(()),
        Err(e) => {
            eprintln!("prism: {e:#}");
            std::process::exit(e.exit_code());
        }
    }
}
