package app.mildang.checkin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"challengeId", "date"}))
@Getter
@Setter
@NoArgsConstructor
public class Checkin {

    @Id
    private String id;

    @Column(nullable = false)
    private String challengeId;

    @Column(nullable = false)
    private String userId;

    /** 논리적 날짜 (05:00 KST 경계) */
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int dayIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionValue bloat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionValue skin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConditionValue drowsy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
