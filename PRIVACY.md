# AI Error Log Purifier Privacy Policy

Effective date and policy version: August 31, 2026 (`2026-08-31`)

This policy describes the data handled by the AI Error Log Purifier IntelliJ plugin. The plugin has no central vendor-operated backend by default. Its default backend URL is local, and users configure and operate the separate [self-hosted Error Purifier backend](https://github.com/Seongbin-Choo/errorPurifier) or use one operated by an organization they trust.

## Data flow

When a user starts an analysis after granting consent:

1. If console text is selected, only the selected text is used as the log payload. If nothing is selected, the full console content is used. In either case, a log payload over 100,000 characters is rejected and is not sent.
2. The plugin sends that log payload to the configured self-hosted backend together with filtered metadata from supported Gradle or Maven build files, the presence of supported Spring configuration files, and basic environment tags identifying IntelliJ and the plugin.
3. The backend applies sensitive-value masking and repeated-log compression, then returns a prepared prompt. Masking is best-effort and may not detect every secret or personal identifier. Users should avoid submitting data that they are not authorized to disclose.
4. The plugin appends the selected analysis-mode instruction and sends the prepared prompt directly from the IDE to the LLM provider selected by the user: OpenAI, Google Gemini, or Anthropic.
5. The provider's response is streamed back to the IDE. In the ordinary analysis flow, the AI answer body is not sent to or stored by the Error Purifier backend.

The current plugin does not call the backend's separate parsing-audit API. Consequently, audit-log content is not part of the plugin's ordinary analysis flow.

## API keys

The selected provider API key is stored through IntelliJ PasswordSafe. It is sent only to the selected LLM provider as required to authenticate provider requests. It is not included in requests to the Error Purifier backend.

The optional connection test in the settings screen sends the configured model and API credential to the selected provider, but it does not send console-log content to that provider or to the Error Purifier backend.

## Device and request identifiers

During client synchronization, the plugin sends its version and any previously issued device UUID to the configured backend. The backend issues or confirms a persistent UUID. The plugin stores it under the IntelliJ configuration directory at `error-purifier/device-uuid` and includes it in subsequent backend requests. This identifier connects prompt preparation, usage, feedback, and history records to the same plugin installation. It is not intended to contain a user's name or email address.

## Usage and feedback metadata

After an LLM request, the plugin reports the following metadata to the configured backend:

- selected provider and model;
- cache key and cache-hit status;
- a SHA-256 hash of the exact prompt sent to the provider, not the prompt text itself;
- original, prepared, and repeat-compression character counts;
- input, output, reasoning, and total token counts;
- request latency;
- referenced log line identifiers, such as `L001`, rather than the referenced line text; and
- ratings, resolution feedback, refinement-feedback type, applied-rule counts, protected-line count, and truncation status when the user submits that feedback.

## Backend storage and retention

The configured backend database stores client-device records, prompt-cache entries and templates, usage records, refinement feedback, and request history. The ordinary prompt-preparation flow does not store the submitted raw log, prepared prompt text, or AI answer body in those records. The backend currently has no automatic retention period for the device, cache, usage, feedback, or history records, so they remain until the self-hosted operator deletes them or removes the database.

The person or organization operating the configured backend is responsible for access control, transport security, retention decisions, backups, and deletion requests. To request deletion, contact that operator. If you operate the backend yourself, delete the relevant records or database according to your own operational procedures.

## Third-party LLM providers

The selected LLM provider processes the prepared prompt and API credential under its own terms and privacy policy. Review the policy for the provider you choose:

- [OpenAI Privacy Policy](https://openai.com/policies/privacy-policy/)
- [Google Privacy Policy](https://policies.google.com/privacy)
- [Anthropic Privacy Policy](https://www.anthropic.com/legal/privacy)

This project does not control a third-party provider's processing, retention, or model-training settings.

## Consent and revocation

The plugin asks for explicit consent before scheduling any backend or LLM network operation, including analysis, provider connection tests, usage-summary requests, and feedback. The consent dialog summarizes the backend transfer, LLM-provider transfer, persistent device UUID, usage and feedback reporting, and links to this policy. Declining cancels the requested operation without making its backend or LLM request. As a defense in depth, the plugin silently checks the current policy consent again immediately before every backend and LLM HTTP transmission; it never opens a consent dialog from a background thread.

The accepted policy version is stored in the IntelliJ application settings. A future policy-version change requires consent again. Users can review the policy, grant consent, or revoke consent under `Settings | Tools | AI Error Purifier`.

Revocation prevents future backend and LLM data transfers by the plugin, including an automatic usage report that has not yet been sent. It does not delete the locally stored device UUID or data already sent to the self-hosted backend or an LLM provider. Deletion of previously transmitted data must be handled by the applicable backend operator or third-party provider.

## Questions

Questions or corrections can be submitted through the [plugin repository issue tracker](https://github.com/Seongbin-Choo/error-purifier-plugin/issues).
