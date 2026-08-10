# AI Error Log Purifier

IntelliJ IDEA 콘솔 로그를 정제해 사용자가 선택한 LLM에 분석을 요청하는 플러그인입니다. 서버는 로그 정제와 프롬프트 재사용을 담당하고, LLM API 키와 실제 모델 호출은 사용자의 IDE에서 처리합니다.

## 주요 기능

- Console 창에서 선택한 로그 또는 전체 로그 분석
- 민감정보를 마스킹한 정제 프롬프트 생성
- OpenAI, Gemini, Claude 스트리밍 응답 지원
- IntelliJ PasswordSafe 기반 API 키 보관
- LLM 사용량과 정제 품질 피드백 전송

## 요구 사항

- IntelliJ IDEA 2022.2 이상
- JDK 17
- 실행 중인 Error Purifier 백엔드

## 설정

`Settings > Tools > AI Error Purifier`에서 백엔드 URL, LLM 제공자, 모델, API 키를 설정합니다. API 키는 프로젝트 파일이나 백엔드에 저장되지 않습니다.

## 빌드

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew buildPlugin
```

생성된 ZIP 파일은 `build/distributions`에 위치합니다.
