package com.governanceplus.web.service;

import com.governanceplus.reviewer.model.ReviewReport;
import com.governanceplus.reviewer.model.RuleEngineReviewAdapter;
import com.governanceplus.web.dto.ReviewStatus;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds in-memory review jobs and runs them one at a time on a single
 * background thread.
 *
 * A job's project directory is either a temp extraction from an uploaded zip
 * (deleted after the job runs) or a path the caller typed in directly,
 * pointing at a real project already on the server's filesystem (never
 * deleted — see the cleanupAfter parameter on {@link #submit}).
 *
 * Each job runs the deterministic com.governanceplus.reviewer.ruleengine rule
 * engine (XPath/pom/JSONPath checks) against the server's single, managed
 * rules.json (see RulesFileStore/RulesController for how that file is
 * viewed/edited) — there's no per-run rules override; edit rules.json via the
 * Rules UI before running a review. There's no AI review here — see
 * RuleAssistController for the separate rule-authoring assistant.
 *
 * No database: job records (not the caller's own project files) are purged
 * after governanceplus.jobs.ttl-minutes.
 */
@Service
public class ReviewJobRegistry implements DisposableBean {

    private final Map<String, ReviewJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ProjectZipExtractor zipExtractor;

    @Value("${governanceplus.jobs.ttl-minutes:120}")
    private long ttlMinutes;

    @Value("${governanceplus.rules.path:../rules/rules.json}")
    private String rulesPath;

    public ReviewJobRegistry(ProjectZipExtractor zipExtractor) {
        this.zipExtractor = zipExtractor;
    }

    /**
     * @param projectDir either a temp directory extracted from an uploaded zip
     *                    (cleanupAfter=true — safe to delete when done) or a
     *                    path the user typed in directly, pointing at a real
     *                    project already on the server's filesystem
     *                    (cleanupAfter=false — never delete the user's own files).
     */
    public ReviewJob submit(Path projectDir, boolean cleanupAfter) {
        String id = UUID.randomUUID().toString();
        ReviewJob job = new ReviewJob(id, projectDir, Instant.now());
        jobs.put(id, job);

        executor.submit(() -> runJob(job, cleanupAfter));
        return job;
    }

    public Optional<ReviewJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void runJob(ReviewJob job, boolean cleanupAfter) {
        job.setStatus(ReviewStatus.RUNNING);
        try {
            ReviewReport report = new RuleEngineReviewAdapter()
                    .review(job.getExtractedProjectDir().toFile(), rulesPath);

            job.setReport(report);
            job.setStatus(ReviewStatus.COMPLETED);
        } catch (Exception e) {
            job.setErrorMessage(e.getMessage());
            job.setStatus(ReviewStatus.FAILED);
        } finally {
            if (cleanupAfter) {
                zipExtractor.cleanup(job.getExtractedProjectDir());
            }
        }
    }

    /** Drops jobs older than the configured TTL. */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void purgeExpiredJobs() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));
        jobs.values().removeIf(job -> job.getSubmittedAt().isBefore(cutoff));
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
