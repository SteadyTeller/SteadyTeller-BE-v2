# SteadyTeller API/DB 설계 명세 (v1)
> 범위: 회원가입 → 학습 목표 설정 → AI 세부 태스크 생성 → 학습 항목 검토 → 스케줄 생성
> 이후 단계(학습 수행 ~ 재수행, 통계, 재학습 권장)는 별도 설계 예정이며 이 문서에는 포함하지 않음.

## 0. 전체 흐름

```
회원가입 → 학습 목표 설정 → AI 세부 태스크 생성 → 학습 항목 검토
→ 스케줄 생성 → 학습 수행 → 수행 결과 저장 → 미완료 일정 재조정
→ 통계 확인 → 부족한 개념 재학습 권장 → 재수행
```

- 인증: 모든 API는 JWT 토큰에서 `memberId`를 추출해서 사용 (Goal, LearningTask, Schedule 등 공통)
- 기술 스택: Java, Spring Boot, Spring Data JPA
- 계층 구조: Controller – Service – Repository

---

## 1. 학습 목표 설정 (회원가입 → 학습 정보 설정)

### Request: `MemberStudyInfoRequestDto`
| 필드 | 타입 | 설명 |
|---|---|---|
| memberId | Long | JWT 토큰에서 추출 |
| title | String | 학습 목표명 (예: "정보처리기사 합격하기") |
| startDate | Date | 학습 시작일 |
| targetDate | Date | 목표 달성일 |
| currentLevel | String | 현재 수준 (예: "초급") |
| dailyStudyHours | Number | 일일 가용 학습 시간 |
| availableDays | List\<String\> | 가용 요일 (예: ["MON","WED","FRI"]) |
| focusArea | String | 집중 학습 분야 |

### Entity: `MemberGoal` (DB 저장)
- 위 Request 필드 전체 + `id`, `created_at`, `updated_at`
- 한 회원이 여러 개의 MemberGoal을 가질 수 있음 (1:N)

---

## 2. AI 세부 태스크 생성

### Request
- Path Variable: `memberGoalId`
- 서버는 `MemberGoal`을 DB에서 조회 → AI에게 컨텍스트로 전달 → 세부 태스크 생성 요청

### AI 생성 결과: `LearningTask` (1차 생성, 아직 DB 확정 저장 아님 — 검토 대상)
| 필드 | 타입 | 설명 |
|---|---|---|
| title | String | 태스크명 (예: "정보처리기사") |
| category | String | AI가 자유 텍스트로 생성하는 대분류 (예: "데이터베이스") |
| subject | String | AI가 자유 텍스트로 생성하는 세부 주제 (예: "정규화") |
| importance | Int (1~5) | **프론트 표시 전용.** difficulty 값을 기준으로 5단계 라벨(매우 어려움~매우 쉬움)로 변환해 보여줌 |
| difficulty | Int (1~5) | 난이도 |
| allocatedMinutes | Int | 예상 소요 시간(분) |
| ~~orderIndex~~ | - | **이 단계에서 결정하지 않음.** orderIndex는 스케줄 생성 단계의 책임 (아래 4번 참고) |

> 설계 노트: `orderIndex`는 "AI가 태스크를 몇 번째로 생성했는가"가 아니라 "사용자가 실제로 수행할 순서"이므로, 스케줄링 로직이 확정된 이후 `ScheduleItem`에서 결정한다. 세부 태스크 생성/검토 단계에서는 값을 채우지 않는다.

---

## 3. 학습 항목 검토 (Task Review)

가장 최근에 확정된 설계. 사용자가 AI 생성 태스크를 승인/수정/삭제하거나 직접 태스크를 추가하는 단계.

### Status Enum
```
PENDING       // 시작 전
IN_PROGRESS   // 진행중
FINISHED      // 완료
```
> 승인된 태스크만 DB에 저장되고, 거부된 태스크는 애초에 저장되지 않는다. 따라서 "거부/삭제됨"을 나타내는 상태값은 필요 없다 — status는 오직 저장된(=승인된) 태스크의 **실행 진행 상태**만 나타낸다.

### Entity: `LearningTask` (최종 확정본)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| goalId | Long | FK → MemberGoal |
| title | String | 태스크명 |
| category | String | AI가 생성해주는 자유 텍스트 분류 (예: "데이터베이스") — 마스터 테이블/FK 아님 |
| subject | String | AI가 생성해주는 자유 텍스트 세부 주제 (예: "정규화") — 마스터 테이블/FK 아님 |
| importance | Int (1~5) | **프론트엔드 표시 전용.** 난이도 등급에 따라 매우 어려움/어려움/중간/쉬움/매우 쉬움 5단계로 표시. 스케줄링 로직에 직접 반영되는 값 아님 |
| difficulty | Int (1~5) | 난이도. importance 표시 라벨 산정의 기준값. 스케줄링 활용 방식은 스케줄 생성 모듈에서 자체 결정 |
| allocatedMinutes | Int | 예상 소요 시간(분) |
| status | Enum | PENDING(시작 전) / IN_PROGRESS(진행중) / FINISHED(완료) — 승인된(=저장된) 태스크의 실행 상태만 표현 |
| source | Enum | AI_GENERATED / USER_ADDED (검토 단계에서 사용자가 직접 추가한 항목 구분용) |
| isModified | Boolean | 사용자가 AI 생성 내용을 수정했는지 여부 (AI 품질 추적용) |
| reviewedAt | Timestamp | 사용자가 승인(확정)해서 DB에 저장된 시각 |
| created_at / updated_at | Timestamp | 공통 |
| ~~orderIndex~~ | - | 이 단계에는 없음 (스케줄링 단계 소관) |

### API 엔드포인트 (5종)
> 승인 전까지는 DB에 저장하지 않는다. 아래 GET/PATCH/DELETE/POST(추가)는 **아직 확정되지 않은 후보 태스크 집합**(AI 생성 결과 + 사용자 추가분)을 대상으로 동작하고, `confirm` 호출 시점에 최종 승인된 항목만 `LearningTask`로 DB에 저장된다.

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/goals/{goalId}/tasks` | 검토 대상 태스크(후보) 목록 조회 |
| PATCH | `/tasks/{taskId}` | 후보 태스크 내용 수정 (저장 시 isModified=true로 반영) |
| DELETE | `/tasks/{taskId}` | 후보 목록에서 제거 (=거부. DB에 저장된 적 없으므로 소프트 삭제 불필요, 그냥 후보에서 빠짐) |
| POST | `/goals/{goalId}/tasks` | 사용자가 후보 목록에 직접 태스크 추가 (source=USER_ADDED) |
| POST | `/goals/{goalId}/tasks/confirm` | **최종 승인.** 이 시점의 후보 목록을 `LearningTask`로 일괄 DB 저장 (status=PENDING, reviewedAt=저장 시각) |

> 설계 노트:
> - `category`/`subject`는 마스터 테이블/FK가 아니라 **AI가 그때그때 생성해주는 자유 텍스트**다. (예: 사용자가 "정보처리기사 학습하기"를 던지면 title="정보처리기사", category="데이터베이스", subject="정규화" 식으로 AI가 채움)
> - `source` 필드는 검토 단계에서 "AI가 만든 것"과 "사용자가 추가한 것"을 구분해야 하므로 필요한 것으로 확정.
> - **승인=저장, 거부=미저장** 원칙에 따라 REJECTED 같은 상태값이나 소프트 삭제 플래그는 필요 없다. `status`는 순수하게 저장된 태스크의 실행 진행도(PENDING/IN_PROGRESS/FINISHED)만 나타낸다.

---

## 4. 스케줄 생성

### Request
- Path Variable: `memberGoalId`
- 서버는 `MemberGoal` + 해당 goalId의 저장된(=승인 완료된) `LearningTask` 목록을 조회
- 두 데이터를 AI에게 전달 → 프롬프트 작성 → 일자별 스케줄 산출

### Entity: `Schedule` (DB 저장)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| userId | Long | 회원 ID |
| goalId | Long | FK → MemberGoal |
| startDate | Date | 스케줄 시작일 |
| endDate | Date | 스케줄 종료일 |
| created_at / updated_at | Timestamp | 공통 |

### Entity: `ScheduleItem` (DB 저장)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| scheduleId | Long | FK → Schedule |
| learningTaskId | Long | FK → LearningTask |
| title | String | 표시용 제목 |
| date | Date | 수행 예정일 |
| dayOfWeek | String | 요일 |
| allocatedMinutes | Int | 배정 시간(분) |
| order | Int | **여기서 orderIndex 최종 확정** (해당 날짜 내 수행 순서) |
| status | Enum | PENDING 등 (수행 단계에서 갱신) |
| created_at / updated_at | Timestamp | 공통 |

### Response
```json
{
  "scheduleId": 100,
  "goalId": 1,
  "scheduleStartDate": "2026-08-22",
  "scheduleEndDate": "2026-09-22",
  "dailySchedules": [
    {
      "date": "2026-08-24",
      "dayOfWeek": "MON",
      "totalAllocatedMinutes": 75,
      "items": [
        {
          "scheduleItemId": 1001,
          "learningTaskId": 1,
          "title": "데이터베이스 정규화 기초",
          "allocatedMinutes": 45,
          "order": 1,
          "status": "PENDING"
        }
      ]
    }
  ]
}
```

---

## 5. 아직 결론 나지 않은 / 다음 단계에서 논의할 사항

- 2차 흐름: 미완료 일정 재조정 / 부족 개념 재학습 권장 / 사용자 커스텀 태스크 추가 후 재스케줄링 로직

> 해결된 항목 (참고용):
> - status enum 변경으로 인한 삭제/확정 흐름 재정의 → **해결.** 승인 시에만 저장, 거부 시 미저장이므로 별도 상태값 불필요.
> - difficulty의 스케줄링 반영 방식 → **해결.** 스케줄 생성 모듈 구현 시 자체 결정, 이 스펙에서 규정하지 않음.
> - AI 프롬프트에 전달할 필드 범위 → **해결.** 프롬프트 작성 시점에 결정, 이 스펙에서 규정하지 않음.

---

## 6. Claude Code 작업 시 참고사항

- 이 문서는 **1차 흐름(회원가입 ~ 스케줄 생성)** 까지의 확정 스펙입니다. 통계/재조정/재학습 추천 단계는 아직 스펙 미정이니 임의로 확장 구현하지 말고, 필요 시 확인 요청할 것.
- `status` Enum은 PENDING/IN_PROGRESS/FINISHED 세 가지뿐이며, `category`/`subject`는 FK가 아닌 자유 텍스트 컬럼임 — 위 표에 명시된 대로 정확히 반영해줄 것.
- 학습 항목 검토 단계는 **승인 전까지 DB 저장 없음.** 후보 목록(GET/PATCH/DELETE/POST)은 미저장 상태로 다루다가 `confirm` 호출 시 일괄 저장하는 구조로 구현할 것.
- `orderIndex`(=ScheduleItem.order)는 오직 스케줄 생성 단계에서만 채워지는 값이라는 점에 주의 (LearningTask 생성/검토 단계 엔티티에는 존재하지 않음).
