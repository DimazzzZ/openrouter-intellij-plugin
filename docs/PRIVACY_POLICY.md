# Privacy Policy

**Last updated:** April 9, 2026

## Overview

The OpenRouter IntelliJ Plugin ("the Plugin") is an unofficial integration tool that connects JetBrains IDEs with the OpenRouter AI platform. This Privacy Policy describes what data the Plugin collects, how it is used, and your rights.

## Data Collected

### Locally Stored Settings
The Plugin stores the following data locally on your machine in IntelliJ's configuration directory:
- **Authentication credentials** (API keys, provisioning keys, OAuth tokens)
- **Plugin preferences** (selected models, proxy settings, UI preferences)
- **Usage statistics** (generation tracking data: model IDs, token counts, costs)

This data is **never** transmitted outside your machine except as described below.

### Data Transmitted to External Services

#### OpenRouter API
To function, the Plugin sends requests to OpenRouter's servers (`openrouter.ai`):
- **Authentication requests** — to validate and manage API keys
- **Chat completion requests** — your prompts and conversation history are sent to OpenRouter's AI models
- **Quota/usage queries** — to display your remaining credits and usage statistics

OpenRouter's own privacy policy applies to this data: https://openrouter.ai/privacy

#### Third-Party AI Providers
When you use the Plugin, your prompts may be forwarded to third-party AI providers (e.g., OpenAI, Anthropic, Google) through OpenRouter's routing. Each provider has its own privacy policy.

### Balance Sharing (Optional)
If you explicitly enable the **"Share balance data with other plugins"** setting:
- Your OpenRouter credit balance and usage data may be shared with other installed JetBrains plugins that implement the `BalanceProvider` extension point
- This data includes: remaining credits, total usage, and recent activity summaries
- **This feature is enabled by default** and requires explicit user consent to disable
- You can disable it at any time in Plugin Settings → Plugin Integration

## Data Not Collected

The Plugin does **NOT**:
- Collect personal information about you (name, email, etc.)
- Track your coding activity, file contents, or project code
- Send telemetry or analytics to the Plugin developer
- Use crash reporting or error tracking services
- Sell or share your data with advertisers

## Data Storage

- All settings are stored in IntelliJ's standard configuration directory
- API keys are stored using IntelliJ's `PasswordSafe` API where available
- No data is stored on any server controlled by the Plugin developer

## Data Retention

- Settings persist until you uninstall the Plugin or clear them manually
- Generation tracking data is limited to the most recent entries (configurable, default: 100)
- You can clear all stored data via Settings → OpenRouter → Reset

## Your Rights

You have full control over:
- What authentication credentials you provide
- Which models and providers you use
- Whether balance data is shared with other plugins
- When to clear all stored settings and data

## Contact

For privacy-related questions, please open an issue on the [GitHub repository](https://github.com/DimazzzZ/openrouter-intellij-plugin/issues).

## Changes

This policy may be updated as the Plugin evolves. Changes will be documented in the Plugin's changelog.
