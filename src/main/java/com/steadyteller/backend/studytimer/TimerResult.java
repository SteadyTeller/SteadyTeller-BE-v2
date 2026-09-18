package com.steadyteller.backend.studytimer;
import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_timer_result", uniqueConstraints = @UniqueConstraint(name = "uk_timer_attempt", columnNames = "attempt_id"))
public class TimerResult extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_id", nullable = false, updatable = false) private UUID attemptId;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false) private Long scheduleId;
    @Column(nullable = false) private Long scheduleItemId;
    @Column(nullable = false) private Long goalId;
    @Column(nullable = false) private Long learningTaskId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private int plannedMinutes;
    @Column(nullable = false) private int actualMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StudyResult result;
    @Column(length = 50) private String reasonCode;
    @Column(length = 1000) private String reasonDetail;
    @Column(length = 2000) private String learnedContent;
    @Column(length = 2000) private String remainingContent;
    public static TimerResult create(Long memberId, Long scheduleId, Long itemId, TimerScheduleQueryPort.Context context, TimerResultRequest request) {
        var value = new TimerResult();
        value.attemptId = request.attemptId(); value.memberId = memberId;
        value.scheduleId = scheduleId; value.scheduleItemId = itemId; value.goalId = context.goalId();
        value.learningTaskId = context.learningTaskId(); value.title = context.title(); value.plannedMinutes = context.plannedMinutes();
        value.actualMinutes = request.actualMinutes(); value.result = request.result();
        value.reasonCode = request.reasonCode(); value.reasonDetail = request.reasonDetail();
        value.learnedContent = request.learnedContent(); value.remainingContent = request.remainingContent();
        return value;
    }
    public boolean matches(Long memberId, Long scheduleId, Long itemId, TimerResultRequest request) {
        return this.memberId.equals(memberId) && this.scheduleId.equals(scheduleId) && scheduleItemId.equals(itemId)
            && actualMinutes == request.actualMinutes() && result == request.result()
            && java.util.Objects.equals(reasonCode, request.reasonCode())
            && java.util.Objects.equals(reasonDetail, request.reasonDetail())
            && java.util.Objects.equals(learnedContent, request.learnedContent())
            && java.util.Objects.equals(remainingContent, request.remainingContent());
    }
}
