package uk.gemwire.camelot.db.schemas;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public record GithubLocation(String repository, String ref, String location) {
    public GHContent resolveAsDirectory(GitHub gitHub, String relativePath) throws IOException {
        return gitHub.getRepository(repository)
                .getFileContent(location.endsWith("/") ? location + relativePath : location + "/" + relativePath, ref);
    }

    public void updateInDirectory(GitHub gitHub, String relativePath, String commitMessage, String newContent) throws IOException {
        String sha;
        try {
            sha = resolveAsDirectory(gitHub, relativePath).getSha();
        } catch (Exception ex) {
            sha = null;
        }
        gitHub.getRepository(repository)
                .createContent()
                .path(location.endsWith("/") ? location + relativePath : location + "/" + relativePath)
                .branch(ref)
                .content(newContent)
                .sha(sha)
                .message(commitMessage)
                .commit();
    }

    @Override
    public String toString() {
        return repository + "@" + ref + ":" + location;
    }

    public static GithubLocation parse(String str) {
        final String[] spl = str.split("@", 2);
        final String[] sub = spl[1].split(":", 2);
        return new GithubLocation(spl[0], sub[0], sub[1]);
    }
}
