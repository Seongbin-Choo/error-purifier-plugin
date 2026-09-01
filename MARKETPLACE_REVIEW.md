# Marketplace Review Guide

This guide lets a JetBrains Marketplace reviewer reproduce the core workflow locally without vendor-hosted infrastructure or credentials supplied by the plugin author.

## Prerequisites

- IntelliJ IDEA 2026.2 or later (build 262 or later).
- Docker Desktop on macOS/Windows, or Docker Engine with the Compose plugin on Linux.
- The submitted plugin ZIP.
- A test API key supplied by the reviewer for one supported provider: OpenAI, Google Gemini, or Anthropic.

Do not send the plugin author or backend repository maintainer any real API key, database password, administrator token, console log, or project secret. The provider key is entered only in IntelliJ and stored through IntelliJ PasswordSafe. It is sent to the selected LLM provider and is not sent to the self-hosted backend.

## 1. Start the self-hosted backend

Clone the backend and enter its directory:

```bash
git clone https://github.com/Seongbin-Choo/errorPurifier.git
cd errorPurifier
```

Create the local environment file.

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Replace every `replace-me` value in `.env` with a different test-only random value. Then start the backend and MariaDB:

```bash
docker compose up --build
```

Wait for the health endpoint to return `{"status":"UP"}`.

macOS/Linux:

```bash
curl http://localhost:8080/api/v1/health
```

Windows PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

The database is stored in a Docker named volume and its port is not published to the host. `docker compose down` stops the environment without deleting its data.

> **Permanent data deletion:** `docker compose down -v` deletes the MariaDB volume and all plugin device, cache, usage, feedback, history, and playbook records stored in it.

## 2. Install and configure the plugin

1. In IntelliJ IDEA, open `Settings | Plugins`, select the gear menu, and choose `Install Plugin from Disk...`.
2. Select the submitted plugin ZIP and restart the IDE if prompted.
3. Open `Settings | Tools | AI Error Purifier`.
4. Set the backend URL to `http://localhost:8080`.
5. Select a supported LLM provider and model, then enter a reviewer-owned test API key.
6. Review the privacy disclosure and grant consent. The first attempted connection test or analysis also presents the consent request if consent has not already been granted.
7. Optionally run the provider connection test. This sends the configured credential and model to the selected provider but does not send console-log content.

Declining or revoking consent blocks future backend and LLM network transmissions by the plugin. Previously transmitted data is not automatically deleted. The complete field list, retention behavior, and operator responsibilities are documented in the [Privacy Policy](PRIVACY.md).

## 3. Reproduce an analysis

Run a small Java program that produces an exception, or use the following non-sensitive sample from a Run/Debug console:

```text
Exception in thread "main" java.lang.IllegalStateException: Database connection failed
    at com.example.demo.OrderService.loadOrders(OrderService.java:42)
    at com.example.demo.Application.main(Application.java:12)
Caused by: java.net.ConnectException: Connection refused
    at java.base/sun.nio.ch.Net.connect0(Native Method)
    at java.base/sun.nio.ch.Net.connect(Net.java:579)
    at com.example.demo.DatabaseClient.connect(DatabaseClient.java:27)
    ... 2 more
```

Select the exception in the console and choose `Analyze Error Log with AI (Cost Optimized)` from the console context menu. IntelliJ installations using the Korean locale display this action as `에러 로그 AI 분석 (비용 최적화)`.

## 4. Expected result

The `AI Error Purifier` tool window opens and exposes three result areas:

- **AI Answer** (`AI 답변`): a streamed explanation from the selected provider with evidence references where available.
- **Prepared Log** (`정제 로그`): the backend-masked and compressed content that was prepared for the provider.
- **My Usage** (`내 사용량`): request totals, provider token counts, response time, and compression statistics reported by the backend.

The normal workflow sends the selected sample and filtered project metadata to the reviewer-operated local backend, then sends the prepared prompt from IntelliJ to the selected LLM provider. The backend does not receive the provider API key or AI answer body. Usage and feedback metadata are associated with a backend-issued persistent device UUID as described in the [Privacy Policy](PRIVACY.md).

## Troubleshooting and cleanup

- Use `docker compose ps` to confirm both services are healthy.
- Use `docker compose logs backend` if the health endpoint is unavailable.
- Confirm that port 8080 is free, or set another `BACKEND_PORT` in `.env` and use the same port in the plugin backend URL.
- Use a disposable provider key with a low spending limit when possible, then revoke it after review.
- Stop the environment with `docker compose down`. Add `-v` only when permanent deletion of all local review data is intended.
