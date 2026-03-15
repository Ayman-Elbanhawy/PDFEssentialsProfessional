# Key Rotation

Release signing and endpoint credentials are injected through CI secrets or local secure properties.

Rotation checklist:
1. Upload the new keystore or rotated alias/password into CI secrets.
2. Verify `:app:bundleProdRelease` succeeds with the new credentials.
3. Rotate API keys or tenant bootstrap credentials in managed configuration or secure property storage.
4. Regenerate the SBOM and license inventory after dependency updates.
5. Record the rotation in the audit/change log for enterprise support.
