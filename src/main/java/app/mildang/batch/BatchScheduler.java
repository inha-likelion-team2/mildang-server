package app.mildang.batch;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 스케줄 등록 — 데모 발표 중에는 /demo/run-batch 수동 트리거를 병행한다 (명세 §14.1). */
@Component
public class BatchScheduler {

    private final BatchJobs batchJobs;

    public BatchScheduler(BatchJobs batchJobs) {
        this.batchJobs = batchJobs;
    }

    /** 매일 05:00 KST — 세 배치 전부 (v1.3: 선차감 전환도 §0.8 일자 경계로 통일) */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void fiveAm() {
        Instant now = Instant.now();
        batchJobs.expireItems(now);
        batchJobs.convertPrepaid(now);
        batchJobs.closeChallenges(now);
    }
}
