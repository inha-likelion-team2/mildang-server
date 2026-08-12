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

    /** 매일 05:00 KST — 항목 만료 + 챌린지 정리 (명세 §6.8, §11.1) */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void fiveAm() {
        Instant now = Instant.now();
        batchJobs.expireItems(now);
        batchJobs.closeChallenges(now);
    }

    /** 매일 00:05 KST — 요일 지난 선차감 전환 (명세 §6.5) */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void prepaidConvert() {
        batchJobs.convertPrepaid(Instant.now());
    }
}
