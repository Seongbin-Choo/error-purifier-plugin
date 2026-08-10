# AI Error Log Purifier

IntelliJ IDEA 콘솔 로그를 정제해 사용자가 선택한 LLM에 분석을 요청하는 플러그인입니다. 서버는 민감정보 마스킹·반복 로그 압축·프롬프트 재사용을 담당하고, LLM API 키와 실제 모델 호출은 사용자의 IDE에서 처리합니다.

## 주요 기능

- Console 창에서 선택한 로그 또는 전체 로그 분석
- 민감정보 마스킹 및 반복 재시도 로그 압축
- OpenAI, Gemini, Claude 스트리밍 응답 지원
- IntelliJ PasswordSafe 기반 API 키 보관
- 빠른/정밀/심층 분석 모드
- AI 답변·정제 로그·내 사용량 탭 제공
- 실제 입력·출력·추론 토큰, 응답 시간, 반복 압축 절감량 표시
- LLM 사용량과 정제 품질 피드백 전송

## 요구 사항

- IntelliJ IDEA 2022.2~2023.2
- JDK 17
- 실행 중인 Error Purifier 백엔드

## 설정

`Settings > Tools > AI Error Purifier`에서 백엔드 URL, LLM 제공자, 모델, 분석 모드, API 키를 설정합니다. 연결 테스트 결과는 설정 창을 닫지 않아도 바로 표시됩니다. API 키는 프로젝트 파일이나 백엔드에 저장되지 않으며 IntelliJ PasswordSafe에 보관됩니다.

콘솔에서 로그를 선택한 뒤 우클릭 메뉴의 `에러 로그 AI 분석 (비용 최적화)`을 실행합니다. 도구 창의 `정제 로그` 탭에서는 AI에 전달된 마스킹·압축 완료 로그를, `내 사용량` 탭에서는 누적 사용량과 반복 압축 절감량을 확인할 수 있습니다.

## 빌드

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew buildPlugin
```

생성된 ZIP 파일은 `build/distributions`에 위치합니다.

## 로컬 IntelliJ 설치

1. `buildPlugin`으로 ZIP을 생성합니다.
2. IntelliJ의 `Settings > Plugins > 톱니바퀴 > Install Plugin from Disk...`에서 ZIP을 선택합니다.
3. IntelliJ를 재시작합니다.

백엔드와 플러그인 프로토콜이 변경된 경우에는 백엔드를 먼저 재시작한 뒤 최신 ZIP을 설치하세요.

## CI

GitHub Actions는 push와 pull request마다 JDK 17 환경에서 `./gradlew verifyPlugin buildPlugin`을 실행합니다. IntelliJ 호환성 검증과 ZIP 패키징이 함께 확인되며, 성공한 실행의 ZIP은 Actions artifact로 내려받을 수 있습니다.
