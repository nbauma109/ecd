package org.sf.feeling.decompiler.source.attach.finder;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;

import org.sf.feeling.decompiler.source.attach.utils.SourceConstants;
import org.sf.feeling.decompiler.util.Logger;

public class LocalSourceFinder extends AbstractSourceCodeFinder {

    private boolean canceled = false;

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public String toString() {
        return this.getClass().toString();
    }

    @Override
    public void find(String binFile, String sha1, List<SourceFileResult> resultList) {
        if (canceled) {
            return;
        }
        File sourceFile = new File(binFile.replace(".jar", "-sources.jar"));
        if (sourceFile.exists()) {
            registerSource(binFile, resultList, sourceFile);
        } else {
            try {
                findGAVFromFile(binFile).ifPresent(gav -> find(gav, binFile, sha1, resultList));
            } catch (Exception e) {
                Logger.error(e);
            }
        }
    }

    private void find(GAV gav, String binFile, String sha1, List<SourceFileResult> resultList) {
        String groupId = gav.getGroupId();
        String artifactId = gav.getArtifactId();
        String version = gav.getVersion();
        String sourceFileName = artifactId + '-' + version + "-sources.jar";
        File groupIdDir = new File(SourceConstants.USER_M2_REPO_DIR, groupId.replace('.', File.separatorChar));
        File sourceFile = new File(groupIdDir, String.join(File.separator, artifactId, version, sourceFileName));
        if (sourceFile.exists()) {
            registerSource(binFile, resultList, sourceFile);
        } else {
            groupIdDir = new File(SourceConstants.USER_GRADLE_CACHE_DIR, groupId);
            File cacheDir = new File(groupIdDir, String.join(File.separator, artifactId, version));
            if (!cacheDir.exists()) {
                return;
            }
            File[] cacheSubDirs = cacheDir.listFiles((dir, name) -> !name.equals(sha1));
            if (cacheSubDirs != null && cacheSubDirs.length == 1) {
                sourceFile = new File(cacheSubDirs[0], sourceFileName);
                if (sourceFile.exists()) {
                    registerSource(binFile, resultList, sourceFile);
                }
            }
        }
    }

    private void registerSource(String binFile, List<SourceFileResult> resultList, File sourceFile) {
        try {
            resultList.add(new SourceFileResult(this, binFile, sourceFile, sourceFile, 100));
            setDownloadUrl(sourceFile.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Logger.error(e);
        }
    }
}
