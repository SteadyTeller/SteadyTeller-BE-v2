package com.steadyteller.backend.member.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "ì¡´ì¬íì§ ìë íììëë¤."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M002", "ì´ë¯¸ ê°ìë ì´ë©ì¼ìëë¤."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "M003", "ì´ë©ì¼ ëë ë¹ë°ë²í¸ê° ì¼ì¹íì§ ììµëë¤."),
    AVAILABILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "íìµ ê°ë¥ ìê°ì ì°¾ì ì ììµëë¤."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "M005", "ì¢ë£ ìê°ì ìì ìê°ë³´ë¤ ë¦ì´ì¼ í©ëë¤."),
    AVAILABILITY_OVERLAP(HttpStatus.CONFLICT, "M006", "ê°ì ìì¼ì ê²¹ì¹ë íìµ ê°ë¥ ìê°ì´ ììµëë¤.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
