package com.governanceplus.reviewer.io;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Clones a Git repository into a temporary local directory using JGit
 * (pure Java, no native `git` executable required). Works against GitHub,
 * Bitbucket, GitLab, or any server speaking standard Git-over-HTTPS.
 *
 * Authentication: for private repos, pass a username/token via the
 * constructor or via environment variables (GIT_USERNAME / GIT_TOKEN).
 * Most providers accept a personal access token as the password with any
 * non-empty username (GitHub: token as password, username can be anything
 * non-empty; Bitbucket/GitLab: check their current token-auth conventions,
 * as exact requirements vary and can change).
 */
public class GitFetcher {

    private final String username;
    private final String token;

    /** No-arg constructor: reads credentials from GIT_USERNAME / GIT_TOKEN env vars, if present. */
    public GitFetcher() {
        this(System.getenv("GIT_USERNAME"), System.getenv("GIT_TOKEN"));
    }

    public GitFetcher(String username, String token) {
        this.username = username;
        this.token = token;
    }

    /**
     * Clones the given repo URL at the given branch into a fresh temp directory
     * and returns the path to the checked-out working tree.
     *
     * @param repoUrl HTTPS clone URL, e.g.
     *                "https://github.com/org/repo.git" or
     *                "https://bitbucket.org/org/repo.git" or
     *                "https://gitlab.com/org/repo.git"
     * @param branch  branch name to check out, e.g. "main" or "develop"
     */
    public Path cloneRepository(String repoUrl, String branch) throws GitOperationException {
        Path targetDir;
        try {
            targetDir = Files.createTempDirectory("mulesoft-reviewer-clone-" + UUID.randomUUID());
        } catch (IOException e) {
            throw new GitOperationException("Could not create temp directory for clone", e);
        }

        var cloneCommand = Git.cloneRepository()
                .setURI(repoUrl)
                .setBranch(branch)
                .setDirectory(targetDir.toFile())
                .setCloneAllBranches(false)
                .setDepth(1); // shallow clone: only the latest commit on the branch, keeps it fast and small

        if (hasCredentials()) {
            cloneCommand.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(username, token));
        }

        try (Git ignored = cloneCommand.call()) {
            return targetDir;
        } catch (Exception e) {
            throw new GitOperationException(
                    "Failed to clone " + repoUrl + " (branch: " + branch + "): " + e.getMessage(), e);
        }
    }

    private boolean hasCredentials() {
        return username != null && !username.isBlank() && token != null && !token.isBlank();
    }

    /** Best-effort cleanup of a cloned temp directory after the review is done. */
    public void cleanup(Path clonedDir) {
        try {
            Files.walk(clonedDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort; OS will clean temp dirs eventually regardless
                        }
                    });
        } catch (IOException e) {
            System.err.println("Warning: failed to clean up temp clone directory " + clonedDir + ": " + e.getMessage());
        }
    }

    public static class GitOperationException extends Exception {
        public GitOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
