# AI Error Log Purifier

AI Error Log Purifier is an IntelliJ IDEA plugin that prepares console errors for analysis by a user-selected LLM provider. It uses a separately running, self-hosted Error Purifier backend to mask sensitive values, compress repeated log entries, and prepare a focused prompt. LLM requests are made by the plugin from the user's IDE.

## Features

- Analyze selected console output or the full console log.
- Mask sensitive values and compress repeated retry or stack-trace content through the configured backend.
- Stream responses from supported OpenAI, Google Gemini, and Anthropic models.
- Store the provider API key in IntelliJ PasswordSafe.
- Choose between fast, precise, and deep analysis modes.
- Review the AI response, prepared log, usage totals, response time, and compression savings in the tool window.
- Surface validation warnings when an answer lacks evidence references or may misinterpret an environment exit code.

## Requirements

- IntelliJ IDEA 2026.2 or later (build 262 or later).
- A separately deployed, self-hosted [Error Purifier backend](https://github.com/Seongbin-Choo/errorPurifier). The backend is not bundled with this plugin.
- Credentials for the LLM provider selected by the user.
- JDK 25 only when building the plugin from source.

## Data flow

1. Console log content, filtered metadata from supported Gradle or Maven build files, the presence of supported Spring configuration files, and basic plugin environment tags are sent to the backend URL configured by the user. When console text is selected, only that selection is sent as log content.
2. The backend masks sensitive values, compresses repeated log content, and returns a prepared prompt.
3. The plugin sends that prepared prompt directly from the IDE to the LLM provider selected by the user.
4. After the request, usage metrics such as provider, model, token counts, latency, character counts, prompt hash, and user-submitted feedback are reported to the configured backend.

The provider API key is stored in IntelliJ PasswordSafe. It is sent to the selected LLM provider when making a request, but it is not sent to or stored by the Error Purifier backend.

During client synchronization, the plugin sends its version and any previously issued device UUID to the backend. The backend-issued UUID is stored persistently under the IntelliJ configuration directory and is included in subsequent backend requests, including usage and feedback requests, so those records can be associated with the same plugin installation.

## Privacy and consent

The plugin requires explicit consent before scheduling any backend or LLM network operation, including analysis, provider connection tests, usage-summary requests, and feedback. Every HTTP transmission also checks the current consent version immediately before sending, so declining, revoking consent, or a policy-version change blocks future backend and LLM transfers. The accepted policy version is stored in IntelliJ application settings so a policy update can require consent again. Consent can be reviewed, granted, or revoked under `Settings > Tools > AI Error Purifier`; revocation does not delete data already sent to the self-hosted backend or an LLM provider.

Read the complete [Privacy Policy](PRIVACY.md) for the exact transferred fields, backend retention behavior, third-party providers, and deletion responsibilities.

## Setup and use

Deploy the self-hosted backend with the Docker Compose [Self-hosted Quick Start](https://github.com/Seongbin-Choo/errorPurifier#self-hosted-quick-start), then configure its URL, the LLM provider, model, analysis mode, and API key under `Settings > Tools > AI Error Purifier`. Connection-test results appear directly in the settings screen.

Select console output, then run `Analyze Error Log with AI (Cost Optimized)` from the console context menu. The `Prepared Log` tab shows the masked and compressed content prepared for the AI provider, while the `My Usage` tab shows accumulated usage and compression savings.

English is the default UI language. When IntelliJ IDEA uses the Korean locale, the action, settings, analysis modes, consent dialog, tool window, and user-facing errors are displayed in Korean, and the selected analysis mode explicitly asks the LLM to answer in Korean.

Marketplace reviewers can follow the complete, credential-safe [Marketplace Review Guide](MARKETPLACE_REVIEW.md), which includes backend startup, a sample exception, consent behavior, and expected results.

## 한국어 안내

IntelliJ IDEA 콘솔 로그를 정제해 사용자가 선택한 LLM에 분석을 요청하는 플러그인입니다. 별도로 실행되는 셀프호스팅 백엔드는 민감정보 마스킹·반복 로그 압축·프롬프트 준비를 담당하고, LLM API 키 보관과 실제 모델 호출은 사용자의 IDE에서 처리합니다.

## 주요 기능

- Console 창에서 선택한 로그 또는 전체 로그 분석
- 민감정보 마스킹 및 반복 재시도 로그 압축
- OpenAI, Gemini, Claude 스트리밍 응답 지원
- IntelliJ PasswordSafe 기반 API 키 보관
- 빠른/정밀/심층 분석 모드
- AI 답변·정제 로그·내 사용량 탭 제공
- 실제 입력·출력·추론 토큰, 응답 시간, 반복 압축 절감량 표시
- 누락된 근거 인용과 실행 환경 종료 코드의 오해석 가능성을 답변 검증 경고로 표시
- LLM 사용량과 정제 품질 피드백 전송

## 요구 사항

- IntelliJ IDEA 2026.2 이상 (빌드 262 이상)
- JDK 25 (플러그인을 소스에서 빌드하는 경우)
- 실행 중인 Error Purifier 백엔드

## 설정

`Settings > Tools > AI Error Purifier`에서 백엔드 URL, LLM 제공자, 모델, 분석 모드, API 키를 설정합니다. 연결 테스트 결과는 설정 창을 닫지 않아도 바로 표시됩니다. API 키는 프로젝트 파일이나 백엔드에 저장되지 않으며 IntelliJ PasswordSafe에 보관됩니다.

비용 없는 로컬 백엔드는 [Docker Compose 빠른 시작](https://github.com/Seongbin-Choo/errorPurifier#self-hosted-quick-start)으로 실행할 수 있습니다. Marketplace 검토 절차는 [MARKETPLACE_REVIEW.md](MARKETPLACE_REVIEW.md)를 참고하세요.

IntelliJ IDEA가 한국어 로케일을 사용하면 메뉴와 도구 창도 한국어로 표시됩니다. 콘솔에서 로그를 선택한 뒤 우클릭 메뉴의 `에러 로그 AI 분석 (비용 최적화)`을 실행합니다. 도구 창의 `정제 로그` 탭에서는 AI에 전달된 마스킹·압축 완료 로그를, `내 사용량` 탭에서는 누적 사용량과 반복 압축 절감량을 확인할 수 있습니다.

## 빌드

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew buildPlugin
```

생성된 ZIP 파일은 `build/distributions`에 위치합니다.

## 로컬 IntelliJ 설치

1. `buildPlugin`으로 ZIP을 생성합니다.
2. IntelliJ의 `Settings > Plugins > 톱니바퀴 > Install Plugin from Disk...`에서 ZIP을 선택합니다.
3. IntelliJ를 재시작합니다.

백엔드와 플러그인 프로토콜이 변경된 경우에는 백엔드를 먼저 재시작한 뒤 최신 ZIP을 설치하세요.

## CI

GitHub Actions는 push와 pull request마다 JDK 25 환경에서 `./gradlew test verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin buildPlugin`을 실행합니다. 테스트, 프로젝트 설정·플러그인 구조·IntelliJ 2026.2.1 바이너리 호환성과 ZIP 패키징이 함께 확인되며, 성공한 실행의 ZIP은 Actions artifact로 내려받을 수 있습니다.

## License

This project is available under the [MIT License](LICENSE).
