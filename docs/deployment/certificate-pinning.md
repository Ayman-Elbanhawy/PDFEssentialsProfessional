# Certificate Pinning Strategy Hooks

The app exposes `BuildConfig.CERTIFICATE_PIN_SET` and surfaces the configured pins through `AppRuntimeConfig`.

Current hook strategy:
- Pins are injected per environment at build time.
- Release builds default to system trust plus optional pin validation hooks.
- Development and demo flavors keep localhost cleartext support through flavor-specific network security configs.

Recommended production next step:
- Connect the runtime pin set to the HTTP client layer used by tenant, collaboration, AI, and connector clients.
- Keep at least two valid pins during rotations.
