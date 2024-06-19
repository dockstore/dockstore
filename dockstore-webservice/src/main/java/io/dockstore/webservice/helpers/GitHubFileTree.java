package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubFileTree implements FileTree {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubFileTree.class);

    private final GitHubSourceCodeRepo gitHubSourceCodeRepo;
    private final String repository;
    private final String ref;
    private final byte[] zip;

    public GitHubFileTree(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String ref) {
        this.gitHubSourceCodeRepo = gitHubSourceCodeRepo;
        this.repository = repository;
        this.ref = ref;
        zip = gitHubSourceCodeRepo.readZip(repository, ref);
    }

    public String readFile(String path) {
        try (ZipInputStream in = zipInputStream()) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory() && getPathFromEntry(entry).equals(path)) {
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new CustomWebApplicationException("could not read file from zip archive", HttpStatus.SC_BAD_REQUEST);
        }
        return gitHubSourceCodeRepo.readFile(path, repository, ref);
    }

    public List<String> listFiles(String path) {
        return gitHubSourceCodeRepo.listFiles(path, repository, ref);
    }

    public List<String> listPaths() {
        try (ZipInputStream in = zipInputStream()) {
            List<String> paths = new ArrayList<>();
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                LOG.error("ZIPENTRY " + entry.getName() + " " + entry.getComment() + " " + entry.getExtra());
                if (!entry.isDirectory()) {
                    paths.add(getPathFromEntry(entry));
                }
            }
            return paths;
        } catch (IOException e) {
            throw new CustomWebApplicationException("could not read paths from zip archive", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private ZipInputStream zipInputStream() throws IOException {
        return new ZipInputStream(new ByteArrayInputStream(zip));
    }

    private String getPathFromEntry(ZipEntry entry) {
        return "/" + entry.getName().split("/", 2)[1];
    }
}
