# SteadyTeller 백엔드 공통 가이드 (ApiResponse & Exception)

이 문서는 SteadyTeller 프로젝트 백엔드 개발 시 **API 공통 응답(ApiResponse)** 객체 사용법과 **도메인별 커스텀 예외(Exception)** 처리 방법에 대한 컨벤션 가이드입니다.

---

## 1. ApiResponse 사용 가이드

모든 REST API 컨트롤러의 반환 타입은 반드시 `ResponseEntity<ApiResponse<T>>` 형태를 유지해야 합니다. 이를 통해 프론트엔드와 일관된 포맷으로 통신할 수 있습니다.

### 1-1. 성공적인 응답 반환 (데이터가 있는 경우)
객체나 리스트 등 반환할 데이터가 있는 경우 `ApiResponse.success(데이터)`를 사용합니다.

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<MemberDto>> getMember(@PathVariable Long id) {
    MemberDto member = memberService.getMember(id);
    // 상태 코드 200(OK)와 함께 ApiResponse 포맷으로 반환
    return ResponseEntity.ok(ApiResponse.success(member));
}
```
**JSON 결과:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "홍길동"
  }
}
```

### 1-2. 성공적인 응답 반환 (커스텀 메시지 포함)
특정 동작이 완료되었을 때 커스텀 메시지를 전달하고 싶다면 `ApiResponse.success("메시지", 데이터)`를 사용합니다. 데이터가 필요 없다면 `null`을 넘깁니다.

```java
@PostMapping
public ResponseEntity<ApiResponse<Void>> createGoal(@RequestBody GoalRequest request) {
    goalService.createGoal(request);
    // 리소스 생성 시 201(Created) 반환 권장
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("목표가 성공적으로 생성되었습니다.", null));
}
```

---

## 2. 도메인별 커스텀 예외(Exception) 처리 가이드

모든 비즈니스 예외 처리는 `CustomException`과 `ErrorCode` 인터페이스를 통해 일관되게 관리되며, 최종적으로 `GlobalExceptionHandler`가 캐치하여 프론트엔드로 에러 JSON을 내려보냅니다.

### 2-1. 도메인 전용 ErrorCode Enum 만들기
새로운 도메인(예: Member)을 개발할 때는 반드시 해당 패키지 하위(예: `exception/` 또는 클래스 내부)에 `ErrorCode` 인터페이스를 구현하는 전용 Enum을 생성합니다.

```java
package com.steadyteller.backend.member.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {
    
    // Member 도메인에서만 발생하는 예외 코드 정의
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M002", "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 2-2. 서비스(Service) 레이어에서 예외 던지기
비즈니스 로직 중 예외 상황이 발생하면, 별도의 Exception 클래스를 만들지 않고 항상 **`CustomException`** 객체에 위에서 만든 **`Enum`**을 담아서 던집니다(`throw`).

```java
package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    public void validateEmail(String email) {
        boolean isDuplicate = memberRepository.existsByEmail(email);
        if (isDuplicate) {
            // 커스텀 예외 발생! (GlobalExceptionHandler가 알아서 처리함)
            throw new CustomException(MemberErrorCode.DUPLICATE_EMAIL);
        }
    }
}
```

**GlobalExceptionHandler에 의해 자동으로 변환되는 최종 JSON 에러 응답:**
```json
{
  "code": "M002",
  "message": "이미 가입된 이메일입니다."
}
```

---

## 요약 (Checklist)
- [ ] API 리턴 시 `ApiResponse.success(...)`로 매핑하여 리턴했는가?
- [ ] 도메인별 발생 가능한 에러를 해당 도메인의 `XXXErrorCode` Enum에 정의했는가?
- [ ] `XXXErrorCode`는 반드시 `ErrorCode` 인터페이스를 상속(`implements`) 받았는가?
- [ ] 비즈니스 로직 예외 상황 시 `throw new CustomException(...)`을 사용해 예외를 던졌는가?
