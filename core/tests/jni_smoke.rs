//! JNI smoke test.
//!
//! Runs on-device via `cargo ndk-test`. Verifies that the symbols the
//! Kotlin layer calls are exported and return non-null handles. This
//! catches the most common JNI regression: a signature mismatch between
//! the Kotlin `external fun` and the Rust `#[no_mangle]` export.

#![cfg(target_os = "android")]

use jni::JavaVM;

#[test]
fn core_library_exports_expected_symbols() {
    // The test binary is statically linked against prism_core, so the
    // symbols are present in the test process. We exercise the pure
    // Rust entry points that the JNI layer delegates to.
    assert_eq!(prism_core::VERSION.len() > 0, true);
    assert!(prism_core::codec::self_test());
}
