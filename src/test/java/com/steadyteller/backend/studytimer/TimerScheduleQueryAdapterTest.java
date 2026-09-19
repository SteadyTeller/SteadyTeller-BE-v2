package com.steadyteller.backend.studytimer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class TimerScheduleQueryAdapterTest {
    @Mock ScheduleRepository schedules;
    @Mock ScheduleItemRepository items;
    @InjectMocks TimerScheduleQueryAdapter adapter;
    @Test void rejectsAnotherMemberBeforeReadingItem() {
        when(schedules.findById(2L)).thenReturn(Optional.of(Schedule.create(1L, 3L, LocalDate.now(), LocalDate.now())));
        assertThatThrownBy(() -> adapter.findOwnedItemForUpdate(9L, 2L, 5L)).isInstanceOf(CustomException.class);
        verifyNoInteractions(items);
    }
    @Test void rejectsItemFromAnotherSchedule() {
        when(schedules.findById(2L)).thenReturn(Optional.of(Schedule.create(1L, 3L, LocalDate.now(), LocalDate.now())));
        when(items.findByIdAndScheduleIdForUpdate(5L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.findOwnedItemForUpdate(1L, 2L, 5L)).isInstanceOf(CustomException.class);
    }

    @Test void readOnlyLookupDoesNotAcquireItemWriteLock() {
        var schedule = mock(Schedule.class);
        var item = mock(ScheduleItem.class);
        when(schedules.findById(2L)).thenReturn(Optional.of(schedule));
        when(schedule.getMemberId()).thenReturn(1L);
        when(schedule.getGoalId()).thenReturn(3L);
        when(items.findByIdAndScheduleId(5L, 2L)).thenReturn(Optional.of(item));
        when(item.getLearningTaskId()).thenReturn(4L);
        when(item.getTitle()).thenReturn("Java");
        when(item.getAllocatedMinutes()).thenReturn(45);

        assertThat(adapter.findOwnedItem(1L, 2L, 5L).learningTaskId()).isEqualTo(4L);
        verify(items, never()).findByIdAndScheduleIdForUpdate(anyLong(), anyLong());
    }
}
