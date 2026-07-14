package com.governanceplus.reviewermule.io;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Clones a Git repository into a temporary local directory using JGit (pure Java, no native `git`
 * executable required). Works against GitHub, Bitbucket, GitLab, or any server speaking standard
 * Git-over-HTTPS. A self-contained copy of reviewer-core's GitFetcher (ported, not depended on) —
 * reviewer-mule has no Maven dependency on reviewer-core at all.
 *
 * Authentication: either set GIT_USERNAME / GIT_TOKEN as environment variables on the Mule server
 * (used when a request doesn't supply its own), or pass a username/token per request (see the
 * 4-arg {@link #cloneRepositoryAsPath} overload) — the request-supplied pair wins when both are
 * present. Most providers accept a personal access token as the password with any non-empty
 * username (GitHub: token as password, username can be anything non-empty; Bitbucket/GitLab:
 * check their current token-auth conventions, as exact requirements vary and can change).
 *
 * Called from reviews.xml via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::io::GitFetcher`), never the Mule Java
 * Module — see {@link #cloneRepositoryAsPath}/{@link #cleanupPath} for the DataWeave-callable
 * static bridges (DataWeave's interop only supports static methods with DataWeave-representable
 * parameters/return types).
 */
public class GitFetcher {

    private final String username;
    private final String token;
    private final Path tempDir;

    /** No-arg constructor: reads credentials from GIT_USERNAME / GIT_TOKEN env vars, if present. */
    public GitFetcher() {
        this(System.getenv("GIT_USERNAME"), System.getenv("GIT_TOKEN"));
    }

    public GitFetcher(String username, String token) {
        this.username = username;
        this.token = token;
        this.tempDir = Paths.get(System.getenv("zip.extraction.dir"));
    }

    /**
     * DataWeave-callable bridge (reviews.xml's submit-review, git-clone branch): clones repoUrl at
     * branch into a fresh temp directory and returns its absolute path as a plain string. Falls
     * back to GIT_USERNAME/GIT_TOKEN env vars for authentication.
     */
    public static String cloneRepositoryAsPath(String repoUrl, String branch) throws GitOperationException {
        return new GitFetcher().cloneRepository(repoUrl, branch).toAbsolutePath().toString();
    }

    /**
     * Same as {@link #cloneRepositoryAsPath(String, String)}, but with an explicit username/token
     * for this one request — used when the caller supplied gitUsername/gitToken instead of relying
     * on server-side env vars. Pass blank/null for either to fall back to GIT_USERNAME/GIT_TOKEN.
     */
    public static String cloneRepositoryAsPath(String repoUrl, String branch, String username, String token) throws GitOperationException {
        boolean hasRequestCredentials = username != null && !username.isBlank() && token != null && !token.isBlank();
        GitFetcher fetcher = hasRequestCredentials ? new GitFetcher(username, token) : new GitFetcher();
        return fetcher.cloneRepository(repoUrl, branch).toAbsolutePath().toString();
    }

    /** DataWeave-callable counterpart to {@link #cloneRepositoryAsPath} — deletes the cloned temp directory. */
    public static void cleanupPath(String dirPath) {
        cleanup(Paths.get(dirPath));
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
            targetDir = Files.createTempDirectory(tempDir, "reviewer-mule-clone-" + UUID.randomUUID());
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

    /**
     * Best-effort cleanup of a cloned temp directory after the review is done. Git (and JGit)
     * deliberately mark files under .git/objects (loose objects AND pack files) READ-ONLY to guard
     * the object database against accidental modification — on Windows, Files.deleteIfExists
     * throws AccessDeniedException for a read-only file, which the try/catch below was silently
     * swallowing, leaving those files (confirmed: pack/*.pack, pack/*.idx) undeletable even from a
     * completely separate process afterward. setWritable(true) clears that attribute (Windows:
     * FILE_ATTRIBUTE_READONLY; POSIX: owner-write bit) right before each delete. Also closes the
     * Files.walk Stream via try-with-resources, per its own Javadoc, to release the native
     * directory-handle resources it opens promptly rather than whenever the GC gets to it.
     */
    public static void cleanup(Path clonedDir) {
        try (var paths = Files.walk(clonedDir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()) // delete children before parents
                    .forEach(p -> {
                        try {
                            p.toFile().setWritable(true);
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
