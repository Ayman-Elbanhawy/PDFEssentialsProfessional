# Release Checklist

1. Run `./gradlew :app:lintProdRelease :editor-core:test :app:assembleProdRelease`.
2. Run `./gradlew :app:generateSbom :app:generateLicenseReport`.
3. Run the smoke suite from `scripts/run-smoke-tests.ps1` on a release candidate.
4. Review the diagnostics bundle and security checklist.
5. Verify Play metadata, data safety notes, backup rules, and network security config.
6. Upload signed `prodRelease` artifacts only after secret injection and CI pass.
