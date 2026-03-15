# Migration Notes

## Module layout
- `:viewer-engine` remains the legacy rendering engine module used by the app.
- `:editor-core` owns editor session state, persistence, and background maintenance work.
- `:app` is a Compose and Material 3 application shell that consumes `editor-core` and `viewer-engine`.

## Wiring notes
- The app creates sessions through `EditorCoreContainer`.
- The Compose UI talks to `PdfEditorSession` only.
- `PdfSessionViewport` in `viewer-engine` is the only place that binds the legacy `PDFView` widget.
- `CleanupExportsWorker` is scheduled on app startup through `EditorCoreContainer`.

## Next extension points
- Replace the simple action messages in `DefaultPdfEditorSession` with real feature coordinators.
- Add page organization, search, forms, and security services to `editor-core`.
- Expand `viewer-engine` with page thumbnails and selection support without leaking `PDFView` into the app layer.
