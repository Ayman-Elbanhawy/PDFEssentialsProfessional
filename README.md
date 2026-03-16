# PDF Essentials Professional

This fork keeps the original Pdfium-based Android PDF renderer as a reusable module and layers a Kotlin, AndroidX, Material 3, Compose-based enterprise document platform on top of it.

The repo is no longer only a viewer widget project. It now contains a modular mobile PDF editor and workflow application with editing, OCR, AI, collaboration, connectors, enterprise policy, diagnostics, migration, accessibility, and release-engineering support.

## Modules

- `:viewer-engine`
  - Legacy Java rendering engine module
  - Java interop preserved
  - Overlay hooks, coordinate mapping, and viewer bridge components
- `:editor-core`
  - Document sessions, write engine, OCR, search, Room persistence, WorkManager jobs, migrations, diagnostics, security, collaboration, connectors, workflows, and enterprise services
- `:ai-assistant`
  - Real AI provider runtime, secure provider configuration, grounded citations, multi-document workspace, audio/voice state, and enterprise policy-aware orchestration
- `:app`
  - Compose application shell, editor workspaces, admin/settings UI, diagnostics UI, connector UI, AI UI, collaboration UI, premium themes, and release configuration

## Product Areas

### Editing and write pipeline
- Session-based editing through `EditorSession`
- Command-based undo and redo
- Direct PDF mutation as the primary persistence model
- Legacy `.pageedits.json` migration support isolated to compatibility and migration code
- Save, save-as, export copy, rollback, checksum verification, transaction logging, and file locking
- Text edits, image edits, annotations, page reorder, page insert/delete/duplicate/extract/rotate, blank-page creation, and structural page persistence
- Write-through protected output for password protection, permission flags, watermarking, metadata scrub, and irreversible redaction apply

### Annotation and review
- Highlight, underline, strikeout, freehand ink, rectangle, ellipse, arrow, line, sticky note, and text box annotations
- Selection, recolor, resize, move, duplicate, and delete operations
- Review threads, replies, mentions, resolve/reopen, activity feed, share links, version-linked review state, and offline sync queue support
- Voice comments with attachment persistence and remote sync support

### Organize pages
- Page thumbnail generation and caching
- Drag/reorder support
- Rotate, delete, duplicate, extract, insert blank page, insert image page, merge, split, and batch operations
- Quick split, quick merge, and quick rearrange surfaces reimplemented natively from mobile-first product ideas inspired by zPDF
- Auto-hiding quick-tools chrome for organize mode so page thumbnails stay primary during drag interactions

### Forms and signatures
- AcroForm field detection and modeling
- Text, multiline, checkbox, radio, dropdown, date, and signature field support
- Form validation and navigation
- Handwritten signature appearance capture
- Certificate-backed digital signature support
- Signature verification state, invalidation after edits, request-sign metadata, signer ordering, reminder metadata, expiration metadata, and reusable form templates

### Security and protected output
- Password protection and permission flags written into saved output
- Watermarking and policy-driven export watermark enforcement
- Metadata scrub support
- Irreversible redaction apply pipeline
- Inspection reports for metadata, protection flags, hidden content flags, redactions, and signatures
- Audit trail events and policy-enforced export/share restrictions

### OCR, search, and scan import
- ML Kit-based OCR runtime
- Searchable PDF generation from imported scans and images
- OCR settings, job lifecycle, resumable persistence, diagnostics, and progress surfaces
- OCR text integrated into search, copy text, and AI grounding
- Search, bookmarks/outline, recent searches, text extraction, and scan import workflows

### AI assistant and audio
- Ask PDF, summarize document, summarize page, explain selection, extract action items, suggest next actions, and semantic search
- Grounded citations to exact document, page, and page-region coordinates
- Multi-document AI workspace with pinned files, saved document sets, conversation history, and workspace summaries
- Hands-free voice prompt capture with interruption and cancel support
- Read-aloud for pages and selections with playback controls and visible progress state
- Voice comments for review workflows with recording, playback, persistence, and sync support
- Real provider runtime for:
  - local Ollama-compatible endpoints
  - remote Ollama-compatible endpoints
  - OpenAI API
  - generic OpenAI-compatible APIs
- Secure provider credential storage using Android Keystore-backed encryption
- Provider discovery, model enumeration, health checks, capability metadata, streaming, cancellation, retry, timeout, and enterprise policy gating

### Collaboration and workflow automation
- Remote collaboration adapter with offline queue, optimistic replay, conflict handling, and WorkManager sync
- Compare workflow with page-level change markers and reviewable change summaries
- Share links, comments, replies, activity events, and review snapshots
- Request-sign workflow, form request workflow, lifecycle tracking, reminder events, and activity integration
- File workflows for text/markdown/Word/image export, import-to-PDF, merge, optimize, and compare-report export

### Enterprise platform hooks
- Personal mode and enterprise mode session architecture
- Tenant bootstrap, cached policy sync, entitlements, telemetry queueing, and diagnostics bundles
- Managed app configuration support for tenant bootstrap, AI restrictions, connector restrictions, telemetry policy, default provider endpoints, watermarking, metadata scrub, and external-sharing controls

### Connectors and storage
- Local file routing
- Android document provider support
- WebDAV connector support
- S3-compatible connector support
- Capability model for future enterprise connectors
- Conflict-aware remote metadata handling with etag/checksum, modified time, and version id
- Secure temp/cache lifecycle and DLP-aware destination filtering

### PDF open and SAF file access
- Proper Android `ACTION_VIEW` PDF open-with support in `MainActivity`
- `ACTION_SEND` PDF stream intake for shared files
- Single-document PDF opening through the Android Storage Access Framework system picker
- Dedicated fallback `Open from Files` flow through `GetContent("*/*")` for providers that behave better outside the SAF document contract
- Clear top-level file actions:
  - `Open PDF`
  - `Open from Files`
  - `Open Recent`
- Real PDF opens replace the seeded sample document instead of being overwritten later in initialization
- Recent PDF reopen support backed by existing Room recent-document state
- PDF open is intentionally separate from form-profile import, which remains JSON-only
- The primary file-open flow uses SAF so Google Drive, Downloads, Documents, Files providers, and on-device storage all appear through one consistent Android picker instead of custom provider integrations
- PDF detection now accepts broader PDF-like MIME types and can fall back to `%PDF-` header sniffing when Android providers return `application/octet-stream`, `*/*`, or incomplete metadata
- Persistable URI permission requests are limited to `content://` documents, which avoids unnecessary failures on non-persistable URIs

### Premium mobile UI and accessibility
- Premium Compose design system with upgraded light and dark themes
- Stronger icon contrast, larger touch targets, clearer hierarchy, and consistent icon sizing
- Reading mode controls for text size, line spacing, character spacing, and margins with graceful fallback when semantic reflow is limited
- Accessibility improvements across major flows, including pane titles, heading semantics, content descriptions, larger interaction surfaces, and stronger visual contrast
- Visual snapshot coverage for key assistant and search surfaces in light and dark themes
- Additional premium quick-tools and lightweight edit surfaces inspired by pdf-editor-android-app, reinterpreted natively in Compose
- Refined icon buttons, cards, inspector surfaces, and organize/edit chrome for faster page and object workflows
- Snapshot proof coverage for organize quick tools and lightweight edit tools in light and dark themes
- App-bar and overflow actions now disable themselves when no document or connector account is available, reducing dead-click states across save, share, export, optimize, and connector flows
- Search navigation, OCR batch actions, annotation recolor actions, and signature entry points now reflect whether the current document/page state can actually support them

## Latest Editor UX Updates

The latest pass focused on making the core file-open and editor controls feel honest and responsive instead of looking clickable while doing nothing.

- PDF open reliability:
  - `Open PDF` stays on the Android SAF picker with broader MIME acceptance
  - `Open from Files` is now a real fallback path instead of duplicating the SAF action
  - PDFs can still open when a provider omits the `.pdf` extension or reports a generic binary MIME type, as long as the file header begins with `%PDF-`
- Menu and top-bar behavior:
  - save and share actions disable when no document is open
  - connector export actions disable until both a document and connector account exist
  - export and optimization actions disable when there is nothing to export or optimize
  - the recent-files label now renders like a section header instead of a fake disabled action
- Annotate, forms, sign, and search behavior:
  - annotate controls now distinguish document-level actions from selected-annotation actions
  - signature entry in annotate only enables when the current page actually has signature fields
  - sign mode now focuses on signature fields and saved signatures instead of showing the full form-profile workflow
  - search previous/next and OCR batch controls disable when there are no hits or OCR jobs
  - bookmark entries now render as readable rows with title and page number instead of icon-only actions
- Organizer behavior:
  - the Organize rail action now opens a visible organizer surface
  - organizer mode hides normal annotate chrome and right-side sidebars while active
  - users can return with `Back to Editor` or jump directly to a page tile to reopen that page in the editor
- Form profile import:
  - form-profile import now uses `OpenDocument`
  - the import flow accepts `application/json`, `application/octet-stream`, and `text/plain` to better tolerate Android provider quirks while still routing into the existing JSON import logic

### Native inspiration report
- Product ideas were reviewed from:
  - `Zenqlo/zPDF`
  - `siddarth16/pdf-editor-android-app`
- The repo now includes a source-inspiration report and attribution note under:
  - `docs/source-inspiration/task-65-native-inspiration-report.md`
  - `docs/source-inspiration/ATTRIBUTION.md`
- No React Native, Expo, or browser-only implementation code was copied into the primary Android architecture
- No direct MIT code reuse was required for this pass; the current attribution update documents inspiration-only usage

### Diagnostics, recovery, and upgrade safety
- Runtime diagnostics snapshot with provider health, sync backlog, OCR queue, connector state, recent failures, and migration reports
- Startup repair for interrupted saves, interrupted sync, interrupted OCR, corrupted drafts, and stale local artifacts
- Versioned migration framework for Room, drafts, AI settings, connector/session state, OCR/search data, and older mutation/session formats
- Benchmark, smoke, regression, migration-oriented, and snapshot-oriented test coverage

## Build Variants

The app module exposes these product flavors:

- `dev`
- `qa`
- `prod`
- `enterpriseDemo`

Recommended local commands:

```powershell
.\gradlew.bat clean
.\gradlew.bat :editor-core:test
.\gradlew.bat :ai-assistant:test
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:assembleProdDebug
.\gradlew.bat :app:validateReleaseReadiness
.\gradlew.bat :app:testProdDebugUnitTest
```

## Release Gates and CI

The repo now includes repo-wide release gates that block merge when production source sets contain prohibited placeholders or fake-style implementations.

Protected patterns include:
- `Fake*`
- `NoOp*`
- `InMemory*`
- `Placeholder*`
- `TODO`
- `example.invalid`
- prohibited legacy save-path references in production code

CI workflows live in `.github/workflows/` and now cover:
- grep-based release gates
- `editor-core` lint and unit tests
- `ai-assistant` lint and unit tests
- app prod lint/build/unit gates
- targeted migration and upgrade-safety tests
- targeted OCR, collaboration, compare/export/protection, connector, forms/signature, workflow, and AI/audio regression tests
- instrumentation, snapshot, smoke, and benchmark workflows for core enterprise flows
- SBOM and license reporting
- signed prod artifact generation hooks

Useful local commands:

```powershell
.\gradlew.bat :app:validateReleaseReadiness
.\gradlew.bat :editor-core:lint :editor-core:test
.\gradlew.bat :ai-assistant:lint :ai-assistant:test
.\gradlew.bat :app:lintProdDebug :app:assembleProdDebug
.\gradlew.bat :app:testProdDebugUnitTest
.\gradlew.bat :app:connectedProdDebugAndroidTest
.\gradlew.bat releaseReadinessEvidence
powershell -ExecutionPolicy Bypass -File .\scripts\run-smoke-tests.ps1
```

## Release Evidence and Current Verification

The repo now includes a single root release evidence task:

```powershell
.\gradlew.bat releaseReadinessEvidence
```

That release evidence pass aggregates:
- repo-wide prohibited-pattern grep gates
- `:app:validateReleaseReadiness`
- `:editor-core:test`
- `:ai-assistant:test`
- `:app:lintProdDebug`
- `:app:assembleProdDebug`
- `:app:testProdDebugUnitTest`
- `:app:assembleProdDebugAndroidTest`

Current verification status from the latest local pass:
- repo-wide prohibited-pattern grep gates pass
- `:app:validateReleaseReadiness` passes
- `:editor-core:test` passes
- `:ai-assistant:test` passes
- `:app:lintProdDebug :app:assembleProdDebug :app:testProdDebugUnitTest` passes
- `:app:connectedProdDebugAndroidTest` passes on the current local emulator run
- `:app:releaseReadinessEvidence` passes and publishes the current visual, audio, write-engine, workflow, and source-inspiration proof bundles
- `ACTION_VIEW` / `ACTION_SEND` PDF intake, SAF `Open PDF`, recent reopen, and sample-replacement regressions are covered by the current app instrumentation suite on the supported API 35 emulator lane

## Current AI and Search Updates

The latest round of work focused on AI entitlement correctness, saved enterprise settings reload, and grounding quality for damaged embedded text.

- Premium and Enterprise plan resolution now keep `FeatureFlag.Ai` in entitlements, while policy still controls whether AI is usable at runtime
- Enterprise admin state now reloads from persisted storage before assistant refresh, which keeps saved plan and policy changes stable across app restarts
- Assistant availability in the app UI now re-evaluates from the current plan, entitlements, and policy instead of trusting stale repository state alone
- Search indexing now prefers OCR text when OCR exists and keeps embedded text when it is clean
- Suspicious embedded text can now be detected and recovered with an OCR fallback through `EmbeddedTextRecoveryRuntime`
- Garbled embedded-text pages are re-indexed for search and AI grounding with OCR-backed text when recovery succeeds

Relevant files:
- `ai-assistant/src/main/java/com/aymanelbanhawy/aiassistant/core/AiAssistantRepository.kt`
- `app/src/main/java/com/aymanelbanhawy/enterprisepdf/app/editor/EditorViewModel.kt`
- `editor-core/src/main/java/com/aymanelbanhawy/editor/core/enterprise/EnterpriseEngines.kt`
- `editor-core/src/main/java/com/aymanelbanhawy/editor/core/search/DefaultDocumentSearchService.kt`
- `editor-core/src/main/java/com/aymanelbanhawy/editor/core/search/RoomSearchIndexStore.kt`
- `editor-core/src/main/java/com/aymanelbanhawy/editor/core/search/EmbeddedTextRecoveryRuntime.kt`

Final AI entitlement rule:
- `FeatureFlag.Ai` grants AI inclusion
- admin policy may restrict AI with `aiEnabled = false`
- admin policy may not grant AI when the entitlement is absent

## Known Bugs

These are the current bugs or open issues we still know about in the repo:

1. Premium AI entitlement still needs final end-to-end runtime validation.
   - The saved Premium plan and AI-enabled policy persist correctly, but the assistant entitlement flow has only been verified live in Enterprise mode during the latest emulator pass.

2. Enterprise AI panel opens correctly, but the actual provider response path still needs more live validation.
   - In the latest emulator test, the Enterprise assistant entitlement gate cleared and the panel opened, but a quick `Summarize Document` action did not render a visible answer within the short wait window.

3. Embedded-text OCR fallback is covered by unit tests but still needs broader on-device validation with multiple real-world PDFs.
   - The new recovery path is designed to repair garbled text extraction for search and AI grounding, but it should still be validated on a wider set of scanned, damaged, and hybrid PDFs.

4. Quick-tools instrumentation is intentionally skipped on API 36 emulator images.
   - The quick-tools UI tests are implemented and wired into release evidence, but they are currently restricted to API 35 and below because the Android 16 / API 36 emulator image still triggers the Compose input-framework regression around `InputManager.getInstance`.

5. Some emulator-backed UI and benchmark tests remain sensitive to device-image behavior even when the suite is green.
   - We now have passing local connected coverage again, but the repo still contains test guards for unstable emulator/framework combinations and those may need revisiting when emulator images change.

6. Source-inspiration reporting is accurate for the current pass, but it should be kept current as new inspiration-driven UX work lands.
   - Any future direct reuse under MIT would need explicit file-level attribution and manifest updates.

7. The PDF-open instrumentation suite is skipped on API 36 emulator images.
   - The actual PDF open flow is implemented for `ACTION_VIEW`, `ACTION_SEND`, and SAF picker opens, but the automation class is currently restricted to API 35 and below because the Android 16 / API 36 emulator still hits the repo’s known Compose/Espresso `InputManager.getInstance` regression.

8. Folder browsing is still intentionally separate from normal PDF open.
   - The primary supported open path is the SAF single-document picker. A dedicated folder browser has not been added in this pass, which avoids replacing the standard PDF-open experience with a tree-only workflow.

9. The organizer view is currently a safe jump-surface, not a full reorder/split workspace.
   - The new organizer mode is now visible and usable for page navigation, but true drag reorder, delete, duplicate, and split actions still need to be wired through the current screen callback contract for end-to-end organizer editing.

## Task 65 Additions

The latest pass adds native quick-tools and visual polish inspired by external open-source products without importing incompatible architecture:

- Quick page workflows:
  - quick split
  - quick merge
  - quick rearrange
- Lightweight object workflows:
  - add text
  - add image
  - quick signature entry point into the dedicated sign flow
- UI refresh:
  - stronger premium dark/light color hierarchy
  - improved icon contrast and touch targets
  - more polished organize and inspector panels
  - refreshed cards, surfaces, and action chrome
- Evidence outputs:
  - `app/build/reports/release/visual-proof/`
  - `app/build/reports/release/quick-tools-proof/`
  - `app/build/reports/release/source-inspiration/`

## Managed Configuration

Enterprise-specific guidance that used to live under `docs/enterprise/` has been folded into the main docs tree. The remaining deployment, security, privacy, and release guidance now lives under:
- `docs/deployment/`
- `docs/security/`
- `docs/privacy/`
- `docs/release/`

Managed restrictions support enterprise deployment scenarios for:
- tenant bootstrap and issuer/base URLs
- AI provider defaults and restrictions
- connector restrictions and allowed destinations
- telemetry policy
- forced watermarking
- forced metadata scrub
- external sharing controls

See:
- `docs/deployment/managed-config.md`
- `docs/deployment/key-rotation.md`
- `docs/deployment/certificate-pinning.md`
- `docs/security/security-review-checklist.md`
- `docs/release/release-checklist.md`
- `docs/release/smoke-tests.md`
- `docs/privacy/data-safety.md`

## Upgrade and Migration Safety

The app includes a versioned migration and repair pipeline. On startup it can:
- back up upgrade-relevant state
- migrate legacy page-edit sessions forward
- preserve and upgrade older drafts and OCR/search/session state
- resume interrupted OCR and sync work
- quarantine corrupted drafts or outdated local artifacts
- generate supportable migration reports for diagnostics export

## Legacy Viewer Usage

The original viewer engine is still available for lower-level rendering use cases.

### XML usage

```xml
<com.github.barteksc.pdfviewer.PDFView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Java usage

```java
pdfView.fromUri(uri)
    .defaultPage(0)
    .enableSwipe(true)
    .swipeHorizontal(false)
    .enableDoubletap(true)
    .enableAnnotationRendering(false)
    .spacing(0)
    .autoSpacing(false)
    .pageSnap(false)
    .pageFling(false)
    .nightMode(false)
    .load();
```

Other sources remain supported:

```java
pdfView.fromFile(file)
pdfView.fromBytes(bytes)
pdfView.fromStream(inputStream)
pdfView.fromAsset("sample.pdf")
pdfView.fromSource(documentSource)
```

### Lightweight legacy edit/export surface

The compatibility-oriented lightweight edit API on `PDFView` is still present:

```java
PDFView pdfView = findViewById(R.id.pdfView);
pdfView.fromUri(uri)
    .defaultPage(0)
    .enableAnnotationRendering(true)
    .load();

pdfView.addEditElement(new PdfTextEdit(
    0,
    new RectF(0.08f, 0.10f, 0.70f, 0.16f),
    "Edited with PDF Essentials Professional",
    Color.RED,
    18f
));

pdfView.addEditElement(new PdfSignatureEdit(
    0,
    new RectF(0.58f, 0.78f, 0.92f, 0.88f),
    signatureBitmap,
    true,
    Color.BLUE
));

File output = new File(getExternalFilesDir(null), "edited.pdf");
pdfView.exportEditedPdf(output);
```

Normalized coordinates still map `RectF(0f, 0f, 1f, 1f)` to the full page.

## Notes

- This fork still uses Pdfium-based rendering in the viewer layer
- 16 KB page-size support and Play compatibility updates are included

## License

Created with the help of android-pdfview by [Joan Zapata](http://joanzapata.com/)

```text
Copyright 2017 Bartosz Schiller

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
