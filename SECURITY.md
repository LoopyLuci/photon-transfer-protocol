# Security Policy

## Supported Versions

Only the latest release of PRISM is supported with security updates.

## Reporting a Vulnerability

If you discover a security vulnerability, please report it privately:

1. **Do not** open a public issue
2. Email the maintainers at: security@prism.example.com
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Impact assessment
   - Suggested fix (if any)

We will acknowledge receipt within 48 hours and aim to provide a fix within 30 days.

## Security practices

PRISM uses:
- **AES-256-GCM** encryption for sensitive data
- **SHA-256** hashing for integrity verification
- **Fountain codes** for forward error correction

All cryptographic operations are implemented in the Rust core (`core/src/`).
