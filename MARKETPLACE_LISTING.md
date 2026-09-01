# JetBrains Marketplace Listing Copy

This file contains release-ready copy and an asset checklist for AI Error Log Purifier 1.0.0. Verify every link and replace the marked support-email placeholder before submission.

## Short description

Prepare IntelliJ console errors for focused analysis by OpenAI, Gemini, or Claude through a self-hosted sanitization backend.

## Feature highlights

- Analyze selected console output or the full console log from the console context menu.
- Mask sensitive values and compress repeated log content through a separately deployed Error Purifier backend.
- Stream answers from user-configured OpenAI, Google Gemini, and Anthropic models.
- Keep the provider API key in IntelliJ PasswordSafe and send it only to the selected provider.
- Choose Fast, Precise, or Deep analysis modes with locale-aware response-language instructions.
- Inspect the prepared log, actual provider token usage, response latency, compression savings, and evidence references.
- Review and control explicit privacy consent before any backend or LLM network transfer.
- Use the English UI by default or the included Korean localization when the IDE locale is Korean.

## Requirements and self-hosting disclosure

AI Error Log Purifier does not include or operate a hosted backend. Before using the plugin, the user must deploy the separate [Error Purifier backend](https://github.com/Seongbin-Choo/errorPurifier) and configure its URL under `Settings | Tools | AI Error Purifier`. A no-cost local setup is available through the backend repository's [Docker Compose quick start](https://github.com/Seongbin-Choo/errorPurifier#self-hosted-quick-start).

The user must also supply credentials for one supported LLM provider. Provider usage may be billed by that provider under the user's account.

Supported IDE: IntelliJ IDEA 2026.2 or later (build 262 or later).

## Privacy disclosure

With explicit user consent, selected console text—or the full console log when nothing is selected—plus filtered project metadata is sent to the backend configured by the user for best-effort masking, repeated-log compression, and prompt preparation. The prepared prompt and analysis-mode instruction are then sent directly from the IDE to the selected LLM provider. A persistent device UUID and usage or user-submitted feedback metadata are sent to the configured backend.

The provider API key is stored in IntelliJ PasswordSafe and is sent to the selected LLM provider, not to the Error Purifier backend. Masking is best-effort, so users should review logs and avoid submitting data they are not authorized to disclose.

Read the complete [Privacy Policy](https://github.com/Seongbin-Choo/error-purifier-plugin/blob/main/PRIVACY.md) for transferred fields, retention, third-party providers, consent, revocation, and deletion responsibilities.

## Setup

1. Start the self-hosted backend with its Docker Compose quick start.
2. Install the plugin and open `Settings | Tools | AI Error Purifier`.
3. Configure the backend URL, LLM provider, model, analysis mode, and provider API key.
4. Review and grant privacy consent.
5. Select an exception in a Run/Debug console and choose `Analyze Error Log with AI (Cost Optimized)`.
6. Review the AI Answer, Prepared Log, and My Usage tabs.

Marketplace reviewers can follow the credential-safe [Marketplace Review Guide](https://github.com/Seongbin-Choo/error-purifier-plugin/blob/main/MARKETPLACE_REVIEW.md).

## Support and project links

- Source: https://github.com/Seongbin-Choo/error-purifier-plugin
- Issues and support: https://github.com/Seongbin-Choo/error-purifier-plugin/issues
- Backend source and self-hosting guide: https://github.com/Seongbin-Choo/errorPurifier
- Privacy Policy: https://github.com/Seongbin-Choo/error-purifier-plugin/blob/main/PRIVACY.md
- License: https://github.com/Seongbin-Choo/error-purifier-plugin/blob/main/LICENSE
- Support email: sxxxxxbin@gmail.com

## Suggested tags

Use only tags available in the Marketplace submission form. Suggested accurate terms:

- AI
- Debugging
- Developer Tools
- Logging
- Code Analysis
- OpenAI
- Gemini
- Anthropic

Do not describe the plugin as offline, zero-cost, or privacy-guaranteed. The backend can run locally without a vendor hosting fee, but LLM-provider usage and the user's infrastructure may incur costs.

## Screenshot checklist and captions

Capture screenshots in the default English locale with a synthetic, non-sensitive sample log. Keep API keys, tokens, device UUIDs, project paths, account names, and private URLs out of every image.

1. **Settings and self-hosted backend** — “Configure the self-hosted backend, LLM provider, model, analysis mode, and PasswordSafe-backed API key.”
2. **Explicit privacy consent** — “Review backend and LLM data transfers before any network request is scheduled.”
3. **Streaming AI answer** — “Analyze an IntelliJ console exception with a user-selected OpenAI, Gemini, or Anthropic model.”
4. **Prepared log** — “Inspect the masked and compressed log prepared by the self-hosted backend.”
5. **Usage and compression** — “Review actual provider token counts, response latency, feedback totals, and repeated-log compression savings.”

Recommended final QA: crop to the IDE content, use a consistent light or dark theme, confirm labels match the submitted build, and verify text remains readable at Marketplace preview size.
