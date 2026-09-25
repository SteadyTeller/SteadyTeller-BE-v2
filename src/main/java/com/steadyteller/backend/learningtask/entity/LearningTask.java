package com.steadyteller.backend.learningtask.entity;
import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED) @Table(name="learning_task")
public class LearningTask extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long goalId;
 @Column(nullable=false) private String title;
 @Column(nullable=false) private String category;
 @Column(nullable=false) private String subject;
 @Column(nullable=false) private Integer difficulty;
 @Column(nullable=false) private Integer allocatedMinutes;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private LearningTaskStatus status;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private LearningTaskSource source;
 @Column(nullable=false) private boolean isModified;
 @Builder public LearningTask(Long goalId,String title,String category,String subject,Integer difficulty,Integer allocatedMinutes,LearningTaskSource source,boolean isModified){this.goalId=goalId;this.title=title;this.category=category;this.subject=subject;this.difficulty=difficulty;this.allocatedMinutes=allocatedMinutes;this.status=LearningTaskStatus.PENDING;this.source=source;this.isModified=isModified;}
 public void updateStatus(LearningTaskStatus status){this.status=status;}
}
