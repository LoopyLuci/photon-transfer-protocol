fn main() {
    // Use the Android NDK linker for cross-compilation
    println!("cargo:rustc-link-arg=--eh-frame-hdr=llvm");
}
