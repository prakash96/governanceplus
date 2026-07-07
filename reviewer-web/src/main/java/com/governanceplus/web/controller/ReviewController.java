package com.governanceplus.web.controller;

import com.governanceplus.web.dto.ReviewJobResponse;
import com.governanceplus.web.service.ProjectZipExtractor;
import com.governanceplus.web.service.ReviewJob;
import com.governanceplus.web.service.ReviewJobRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewJobRegistry jobRegistry;
    private final ProjectZipExtractor zipExtractor;

    public ReviewController(ReviewJobRegistry jobRegistry, ProjectZipExtractor zipExtractor) {
        this.jobRegistry = jobRegistry;
        this.zipExtractor = zipExtractor;
    }

    /**
     * Submits a Mulesoft project for review; runs asynchronously, returns immediately.
     * Accepts exactly one of:
     *  - `file`: a project zip, extracted into a temp dir and deleted after the job runs.
     *  - `projectPath`: a path to a project already on the server's filesystem — read
     *    directly, never deleted. There's no access control on this beyond whatever the
     *    OS/filesystem permissions of the process already grant (consistent with this
     *    app's no-auth, single-trusted-user design) — only wire this up somewhere
     *    reachable by people you'd already trust with local filesystem access.
     */
    @PostMapping
    public ResponseEntity<ReviewJobResponse> submitReview(
            @RequestParam(value = "file", required = false) MultipartFile projectZip,
            @RequestParam(value = "projectPath", required = false) String projectPath) {

        boolean hasFile = projectZip != null && !projectZip.isEmpty();
        boolean hasPath = projectPath != null && !projectPath.isBlank();

        if (hasFile == hasPath) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide exactly one of `file` (zip upload) or `projectPath` (server-side path)");
        }

        Path projectDir;
        boolean cleanupAfter;
        if (hasFile) {
            try {
                projectDir = zipExtractor.extract(projectZip);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid zip: " + e.getMessage());
            }
            cleanupAfter = true;
        } else {
            projectDir = Paths.get(projectPath);
            if (!Files.isDirectory(projectDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Not a directory on the server's filesystem: " + projectPath);
            }
            cleanupAfter = false;
        }

        ReviewJob job = jobRegistry.submit(projectDir, cleanupAfter);

        return ResponseEntity.accepted().body(toResponse(job));
    }

    @GetMapping("/{jobId}")
    public ReviewJobResponse getJob(@PathVariable String jobId) {
        return jobRegistry.get(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job: " + jobId));
    }

    private ReviewJobResponse toResponse(ReviewJob job) {
        return new ReviewJobResponse(job.getId(), job.getStatus(), job.getReport(), job.getErrorMessage());
    }
}
