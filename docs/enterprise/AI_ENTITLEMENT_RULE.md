\# AI Entitlement Rule



AI follows a two-step rule:



1\. \*\*Entitlement grants the feature\*\*

&nbsp;  - `FeatureFlag.Ai` must be present in `EntitlementStateModel.features`.



2\. \*\*Admin policy may restrict it\*\*

&nbsp;  - `AdminPolicyModel.aiEnabled` may disable AI usage.

&nbsp;  - Admin policy may not grant AI to a plan that does not include `FeatureFlag.Ai`.



This means:

\- Free without `FeatureFlag.Ai` → AI unavailable

\- Premium without `FeatureFlag.Ai` → AI unavailable

\- Premium with `FeatureFlag.Ai` + `aiEnabled=false` → AI unavailable

\- Enterprise without `FeatureFlag.Ai` → AI unavailable

\- Enterprise with `FeatureFlag.Ai` + `aiEnabled=true` → AI available

