# Managed App Configuration

The app reads Android managed app restrictions from [restrictions.xml](C:/Projects/PDFEssentialsProfessional/app/src/main/res/xml/restrictions.xml) and resolves them in `AppRuntimeConfigLoader`.

Supported keys:
- `managed_tenant_base_url`
- `managed_tenant_issuer_url`
- `managed_tenant_policy_base_url`
- `managed_ai_base_url`
- `managed_collaboration_base_url`
- `managed_connector_base_url`
- `managed_default_ai_provider`
- `managed_default_ai_model`
- `managed_approved_ai_providers`
- `managed_allowed_connectors`
- `managed_allowed_destinations`
- `managed_disable_ai`
- `managed_disable_cloud_ai`
- `managed_force_watermark`
- `managed_force_metadata_scrub`
- `managed_disable_external_sharing`
- `managed_telemetry_upload_enabled`
- `managed_telemetry_retention_days`
- `managed_secure_logging`

Recommended MDM rollout:
1. Ship `prod` flavor builds to managed devices.
2. Inject tenant bootstrap, policy, collaboration, AI, and connector endpoints through managed restrictions instead of baking tenant values into the APK.
3. Use `managed_approved_ai_providers`, `managed_disable_ai`, and `managed_disable_cloud_ai` to enforce sovereign or local-only AI deployments.
4. Use `managed_allowed_connectors` and `managed_allowed_destinations` to constrain storage destinations per tenant.
5. Force watermarking, metadata scrub, and secure logging for regulated deployments.
6. Set telemetry upload and retention centrally to align with customer retention policy.
