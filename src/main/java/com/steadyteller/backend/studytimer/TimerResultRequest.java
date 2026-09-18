package com.steadyteller.backend.studytimer;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record TimerResultRequest(
    @NotNull UUID attemptId,
    @NotNull @Min(0) @Max(1440) Integer actualMinutes,
    @NotNull StudyResult result,
    @Size(max = 50) String reasonCode,
    @Size(max = 1000) String reasonDetail,
    @Size(max = 2000) String learnedContent,
    @Size(max = 2000) String remainingContent
) {}
