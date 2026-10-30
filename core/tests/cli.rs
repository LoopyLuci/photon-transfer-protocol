// core/tests/cli.rs
use assert_cmd::Command;
use predicates::prelude::*;

#[test]
fn self_test_exits_zero() {
    Command::cargo_bin("prism")
        .unwrap()
        .arg("self-test")
        .assert()
        .success()
        .stdout(predicate::str::contains("self-test passed"));
}

#[test]
fn missing_input_fails_with_code_one() {
    Command::cargo_bin("prism")
        .unwrap()
        .args(["encode-static", "--input", "/nonexistent", "--output", "/tmp/out"])
        .assert()
        .failure()
        .code(1);
}

#[test]
fn encodes_a_small_file() {
    let dir = tempfile::tempdir().unwrap();
    let input = dir.path().join("payload.bin");
    std::fs::write(&input, vec![0xABu8; 4096]).unwrap();
    let out = dir.path().join("out");

    Command::cargo_bin("prism")
        .unwrap()
        .args([
            "encode-static",
            "--input", input.to_str().unwrap(),
            "--output", out.to_str().unwrap(),
            "--dpi", "600",
        ])
        .assert()
        .success()
        .stdout(predicate::str::contains("Encoded 4096 bytes"));

    assert!(out.join("manifest.json").exists());
}
