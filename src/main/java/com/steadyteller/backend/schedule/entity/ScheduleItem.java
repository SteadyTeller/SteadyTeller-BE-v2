package com.steadyteller.backend.schedule.entity;
import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @Table(name="schedule_item")
public class ScheduleItem extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="schedule_id",nullable=false) private Schedule schedule;
 @Column(nullable=false) private Long learningTaskId;
 @Column(nullable=false) private String title;
 @Column(nullable=false) private LocalDate date;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private DayOfWeek dayOfWeek;
 @Column(nullable=false) private int allocatedMinutes;
 private LocalTime startTime; private LocalTime endTime;
 @Column(name="order_index",nullable=false) private int orderIndex;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ScheduleItemStatus status;
 @Builder private ScheduleItem(Schedule schedule,Long learningTaskId,String title,LocalDate date,DayOfWeek dayOfWeek,int allocatedMinutes,int orderIndex){this.schedule=schedule;this.learningTaskId=learningTaskId;this.title=title;this.date=date;this.dayOfWeek=dayOfWeek;this.allocatedMinutes=allocatedMinutes;this.orderIndex=orderIndex;this.status=ScheduleItemStatus.PENDING;}
 public static ScheduleItem create(Schedule schedule,Long taskId,String title,LocalDate date,DayOfWeek day,int minutes,int order){return builder().schedule(schedule).learningTaskId(taskId).title(title).date(date).dayOfWeek(day).allocatedMinutes(minutes).orderIndex(order).build();}
 public void assignTimeRange(LocalTime start,LocalTime end){this.startTime=start;this.endTime=end;}
 public void reschedule(LocalDate date,DayOfWeek day,int minutes){this.date=date;this.dayOfWeek=day;this.allocatedMinutes=minutes;}
 public void fail(){this.status=ScheduleItemStatus.FAILED;}
 public void updateOrderIndex(int order){this.orderIndex=order;}
 public void start(){if(status!=ScheduleItemStatus.FINISHED)this.status=ScheduleItemStatus.IN_PROGRESS;}
 public void finish(){this.status=ScheduleItemStatus.FINISHED;}
 public void revertCompletion(){if(status==ScheduleItemStatus.FINISHED)this.status=ScheduleItemStatus.PENDING;}
}
