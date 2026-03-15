# Security Review Checklist

## Build and release
- Verify release signing secrets are injected only in CI or secure local properties.
- Confirm `prodRelease` uses minification, shrinking, and hardened network rules.
- Review generated SBOM and license inventory.

## Runtime protections
- Validate managed restrictions for tenant URL, watermark, sharing, and AI restrictions.
- Confirm secure logging is enabled in release mode.
- Review backup exclusions for databases, caches, diagnostics, and drafts.
- Validate network security config for each flavor.

## Test support
- Run the smoke test runner.
- Run instrumentation benchmark coverage for large document open.
- Export diagnostics bundle from a release candidate and confirm secrets are redacted.
