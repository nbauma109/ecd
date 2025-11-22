#!/usr/bin/env groovy
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

final class Issue {
    final String path
    final int line
    final int col
    final String typo
    final String fix

    Issue(String path, int line, int col, String typo, String fix) {
        this.path = path
        this.line = line
        this.col = col
        this.typo = typo
        this.fix = fix
    }
}

final class FileText {
    final String text
    final Charset charset

    FileText(String text, Charset charset) {
        this.text = text
        this.charset = charset
    }
}

final String outputPath = (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty())
        ? args[0]
        : "typos.out"

final File outputFile = new File(outputPath)
if (!outputFile.exists()) {
    System.err.println("We cannot find typos output file: " + outputFile.absolutePath)
    System.exit(2)
}

final List<String> lines = outputFile.readLines(StandardCharsets.UTF_8)

final Pattern errorPattern = ~/^error:\s+`([^`]+)`\s+should be\s+`([^`]+)`/
final Pattern locationPattern = ~/.*╭▸\s+([^:]+):(\d+):(\d+)\s*$/

final List<Issue> issues = new ArrayList<>()
String pendingTypo = null
String pendingFix = null
boolean pending = false

lines.each { String line ->
    def err = (line =~ errorPattern)
    if (err.matches()) {
        pendingTypo = err[0][1]
        pendingFix = err[0][2]  // first suggestion only
        pending = true
        return
    }

    if (pending) {
        def loc = (line =~ locationPattern)
        if (loc.matches()) {
            String path = loc[0][1]
            int ln = Integer.parseInt(loc[0][2])
            int col = Integer.parseInt(loc[0][3])
            issues.add(new Issue(path, ln, col, pendingTypo, pendingFix))
            pending = false
            pendingTypo = null
            pendingFix = null
        }
    }
}

if (issues.isEmpty()) {
    println("No remaining ambiguous typos found.")
    System.exit(0)
}

final Map<String, List<Issue>> byFile = issues.groupBy { it.path }

int totalApplied = 0

byFile.each { String path, List<Issue> fileIssues ->
    File f = new File(path)
    if (!f.exists() || !f.isFile()) {
        return
    }

    FileText ft = readFileWithFallback(f)
    List<String> fileLines = splitPreservingNewlines(ft.text)

    fileIssues.sort { Issue a, Issue b ->
        int cmpLine = Integer.compare(b.line, a.line)
        if (cmpLine != 0) {
            return cmpLine
        }
        return Integer.compare(b.col, a.col)
    }

    boolean changed = false

    for (Issue issue : fileIssues) {
        int idx = issue.line - 1
        if (idx < 0 || idx >= fileLines.size()) {
            continue
        }

        String originalLine = fileLines.get(idx)
        int col0 = Math.max(issue.col - 1, 0)

        int pos = originalLine.indexOf(issue.typo, col0)
        if (pos < 0) {
            pos = originalLine.indexOf(issue.typo)
        }
        if (pos < 0) {
            continue
        }

        String updatedLine = originalLine.substring(0, pos) +
                issue.fix +
                originalLine.substring(pos + issue.typo.length())
        fileLines.set(idx, updatedLine)

        changed = true
        totalApplied++
        println("Applied '${issue.typo}' -> '${issue.fix}' at ${issue.path}:${issue.line}:${issue.col}")
    }

    if (changed) {
        String rebuilt = fileLines.join("")
        f.write(rebuilt, ft.charset.name())
    }
}

println("Total first-suggestion fixes applied: " + totalApplied)

static FileText readFileWithFallback(File f) {
    try {
        String t = f.getText(StandardCharsets.UTF_8.name())
        return new FileText(t, StandardCharsets.UTF_8)
    } catch (Exception ignored) {
        String t = f.getText("ISO-8859-1")
        return new FileText(t, Charset.forName("ISO-8859-1"))
    }
}

static List<String> splitPreservingNewlines(String text) {
    if (text == null || text.isEmpty()) {
        return new ArrayList<>()
    }
    return (text.split("(?<=\\r?\\n)", -1) as List<String>)
}
