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
    public ApiResponse<TimerResult> save(@AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId,
                                        @PathVariable Long itemId, @Valid @RequestBody TimerResultRequest request) {
        return ApiResponse.success(service.save(memberId, scheduleId, itemId, request));
    }
}
