package app.mildang.haggle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class HaggleMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private int turnNo;

    /** USER / ASSISTANT */
    @Column(nullable = false)
    private String role;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private boolean hasProposal;

    private String proposalKey;

    private String proposalLabel;

    private Integer proposalPoints;

    private String proposalLever;

    private String proposalBasis;

    @Column(nullable = false)
    private String frame;

    private Integer balanceAfterAdjusted;

    private Integer balanceAfterOriginal;

    @Column(nullable = false)
    private boolean fallbackUsed;

    @Column(nullable = false)
    private Instant createdAt;
}
