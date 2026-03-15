# Privacy and Data Safety Notes

Data handled by the app can include PDFs, extracted text, OCR results, review comments, diagnostics, and tenant settings.

Operational guidance:
- Release builds default to secure logging.
- Managed restrictions can disable cloud AI and external sharing.
- Backup and device-transfer rules exclude sensitive caches, diagnostics, and local working databases.
- CI-generated artifacts should be stored in restricted release channels only.
