package com.steadyteller.backend.studytimer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.steadyteller.backend.global.exception.CustomException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class TimerResultServiceTest {
    @Mock TimerResultRepository repository;
    @Mock TimerScheduleQueryPort query;
    @InjectMocks TimerResultService service;
    private final TimerScheduleQueryPort.Context context = new TimerScheduleQueryPort.Context(3L, 4L, "Java", 45);
    private TimerResultRequest request(UUID id, int minutes, StudyResult result, String reason) {
        return new TimerResultRequest(id, minutes, result, reason, null, "배운 내용", null);
    }
    @Test void storesServerDerivedPlanSnapshotWithoutChangingSchedule() {
        var request = request(UUID.randomUUID(), 32, StudyResult.COMPLETED, null);
        when(query.findOwnedItemForUpdate(1L, 2L, 5L)).thenReturn(context);
        when(repository.findByAttemptId(request.attemptId())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.save(1L, 2L, 5L, request);
        assertThat(result.getGoalId()).isEqualTo(3L);
        assertThat(result.getPlannedMinutes()).isEqualTo(45);
        assertThat(result.getActualMinutes()).isEqualTo(32);
        assertThat(result.getLearnedContent()).isEqualTo("배운 내용");
    }
    @Test void identicalRetryReturnsExistingRecord() {
        var request = request(UUID.randomUUID(), 32, StudyResult.FAILED, "OTHER");
        var stored = TimerResult.create(1L, 2L, 5L, context, request);
        when(query.findOwnedItemForUpdate(1L, 2L, 5L)).thenReturn(context);
        when(repository.findByAttemptId(request.attemptId())).thenReturn(Optional.of(stored));
        assertThat(service.save(1L, 2L, 5L, request)).isSameAs(stored);
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void changedRetryIsRejected() {
        var request = request(UUID.randomUUID(), 32, StudyResult.FAILED, "OTHER");
        when(query.findOwnedItemForUpdate(1L, 2L, 5L)).thenReturn(context);
        when(repository.findByAttemptId(request.attemptId())).thenReturn(Optional.of(TimerResult.create(1L, 2L, 5L, context, request)));
        assertThatThrownBy(() -> service.save(1L, 2L, 5L, request(request.attemptId(), 33, StudyResult.FAILED, "OTHER"))).isInstanceOf(CustomException.class);
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void failedResultRequiresSupportedReason() {
        assertThatThrownBy(() -> service.save(1L, 2L, 5L, request(UUID.randomUUID(), 32, StudyResult.FAILED, null))).isInstanceOf(CustomException.class);
        verifyNoInteractions(query, repository);
    }
    @Test void invalidMinutesAreRejected() {
        for (int minutes : new int[]{-1, 1441}) assertThatThrownBy(() -> service.save(1L, 2L, 5L, request(UUID.randomUUID(), minutes, StudyResult.COMPLETED, null))).isInstanceOf(CustomException.class);
        verifyNoInteractions(query, repository);
    }
    @Test void ownershipFailureNeverWritesRecords() {
        when(query.findOwnedItemForUpdate(9L, 2L, 5L)).thenThrow(new CustomException(TimerErrorCode.EMPTY_ITEM));
        assertThatThrownBy(() -> service.save(9L, 2L, 5L, request(UUID.randomUUID(), 32, StudyResult.COMPLETED, null))).isInstanceOf(CustomException.class);
        verifyNoInteractions(repository);
    }

    @Test void historyIsReturnedOnlyAfterOwnershipCheck() {
        var request = request(UUID.randomUUID(), 32, StudyResult.COMPLETED, null);
        var stored = TimerResult.create(1L, 2L, 5L, context, request);
        when(query.findOwnedItem(1L, 2L, 5L)).thenReturn(context);
        when(repository.findAllByMemberIdAndScheduleIdAndScheduleItemIdOrderByCreatedAtDesc(1L, 2L, 5L))
            .thenReturn(List.of(stored));

        assertThat(service.findHistory(1L, 2L, 5L)).singleElement()
            .satisfies(result -> assertThat(result.actualMinutes()).isEqualTo(32));
        verify(query).findOwnedItem(1L, 2L, 5L);
    }
}
