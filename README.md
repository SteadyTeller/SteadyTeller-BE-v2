# SteadyTeller - 1:1 맞춤형 AI 공부 코치 시스템 (Backend)

SteadyTeller는 사용자의 학습 목표와 가용 시간을 기반으로 AI가 맞춤형 커리큘럼을 생성하고, 하루 일정을 동적으로 분배해주는 서비스입니다. 이 레포지토리는 백엔드를 다룹니다.

## 🚀 프로젝트 개요
- 사용자가 입력한 거시적 목표(예: "이번 주 자료구조 공부")를 AI(OpenAI API)를 활용하여 미시적 작업(예: 1일차 배열, 2일차 큐)으로 자동 세분화합니다.
- 사용자의 일일 가용 시간과 작업량을 매핑하여 최적의 하루 일정을 도출하고, 미뤄진 작업이나 복습 주기를 고려해 일정을 재배치(Reschedule)합니다.

## 🛠 기술 스택
- **Language**: Java 21
- **Framework**: Spring Boot 4.1.0
- **Database**: MySQL (운영) / H2 (로컬 테스트)
- **ORM**: Spring Data JPA
- **Security**: Spring Security + JWT (JSON Web Token)
- **AI Integration**: Spring AI (OpenAI GPT-4o)
- **Build Tool**: Gradle
- **API Documentation**: SpringDoc OpenAPI (Swagger)

## 📁 주요 도메인 (Packages)
도메인 주도 설계(DDD) 관점을 반영하여 각 관심사별로 패키지가 분리되어 있습니다.
- `global`: 예외 처리, 공통 응답(ApiResponse), 시큐리티/JWT 설정
- `member`: 사용자 프로필 및 계정 관리
- `learningprofile` & `availability`: 학습 성향 및 사용자의 가용 시간 관리
- `goal` & `learningtask`: 거시적 목표와 세분화된 세부 학습 과제 관리
- `schedule` & `reschedule`: 동적 일정 분배 알고리즘 및 재조정 내역 관리
- `studyrecord` & `schedulechange`: 학습 기록 분석, 통계, 일정 변경 이력 로깅

## ⚙️ 실행 방법 (Getting Started)

### 1. 환경 변수 세팅
AI 기능 사용 및 DB 연결을 위해 다음과 같은 환경 변수 또는 `application.yml` 세팅이 필요합니다.

```yaml
# src/main/resources/application.yml
spring:
  ai:
    openai:
      api-key: "본인의_OPENAI_API_KEY" # 필수 발급 필요

# Prod 프로필 사용 시 MySQL 연결 정보 필요 (DB_HOST, DB_PASSWORD 등)
```

### 2. 프로젝트 빌드 및 실행
IDE(IntelliJ IDEA 등)에서 프로젝트를 Import 하거나 터미널에서 다음 명령어를 실행합니다.

```bash
# 로컬 개발 환경(H2 메모리 DB)으로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 3. API 문서 확인
서버 실행 후, 아래 주소로 접속하여 Swagger UI를 통해 모든 API 명세를 확인하고 테스트할 수 있습니다.
- `http://localhost:8080/swagger-ui.html`

## 📑 백엔드 개발 컨벤션 (Docs)
프로젝트 내 예외 처리(Exception) 및 공통 응답(ApiResponse) 규약은 아래 문서를 참고해 주세요.
- [Backend Guide (docs/backend-guide.md)](docs/backend-guide.md)
