package com.steadyteller.backend.studytimer;
import com.steadyteller.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class TimerResultController {
    private final TimerResultService service;
    @PostMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/timer-results")
    public ApiResponse<TimerResultResponse> save(@AuthenticationPrincipal Long memberId,
                                                 @PathVariable Long scheduleId,
                                                 @PathVariable Long itemId,
                                                 @Valid @RequestBody TimerResultRequest request) {
        TimerResult result = service.save(memberId, scheduleId, itemId, request);
        return ApiResponse.success(TimerResultResponse.from(result));
    }
}
