package ru.projectkingdom.assistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Atomic, bounded report storage inside the current world directory. */
public final class AssistantReportStore {
    public static final int MAX_REPORT_BYTES = 1_048_576;
    public static final int MAX_REPORTS = 20;
    private static final Pattern SAFE_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,126}\\.json");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .setPrettyPrinting().create();

    public record Report(
            String title,
            List<AssistantFinding> findings,
            List<ModRecommendation> recommendations
    ) {
        public Report {
            title = Objects.requireNonNull(title, "title").trim();
            if (title.isEmpty() || title.length() > 256) throw new IllegalArgumentException("title");
            findings = List.copyOf(findings);
            recommendations = List.copyOf(recommendations);
        }
    }

    private final Path worldRoot;
    private final Path reportsDirectory;

    public AssistantReportStore(Path worldRoot) {
        this.worldRoot = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        this.reportsDirectory = this.worldRoot.resolve("kingdom-assistant/reports").normalize();
        if (!this.reportsDirectory.startsWith(this.worldRoot))
            throw new IllegalArgumentException("reports directory escapes world root");
    }

    public synchronized Path write(String fileName, Report report) throws IOException {
        Path target = target(fileName);
        Objects.requireNonNull(report, "report");
        byte[] bytes = GSON.toJson(report).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REPORT_BYTES) throw new IllegalArgumentException("report exceeds 1 MiB");
        rejectSensitiveContent(bytes);
        ensureDirectoryInsideWorld();
        long newestTimestamp = sortedReports().stream()
                .mapToLong(AssistantReportStore::lastModified).max().orElse(Long.MIN_VALUE);
        long writeTimestamp = Math.max(System.currentTimeMillis(),
                newestTimestamp == Long.MAX_VALUE ? Long.MAX_VALUE : newestTimestamp + 1L);

        Path temporary = Files.createTempFile(reportsDirectory, ".assistant-report-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(target, FileTime.fromMillis(writeTimestamp));
        } finally {
            Files.deleteIfExists(temporary);
        }
        retainNewest();
        return target;
    }

    public synchronized List<Path> list() throws IOException {
        if (!Files.exists(reportsDirectory)) return List.of();
        ensureDirectoryInsideWorld();
        return sortedReports();
    }

    private Path target(String fileName) {
        String name = Objects.requireNonNull(fileName, "fileName").trim();
        if (!SAFE_NAME.matcher(name).matches() || name.contains("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
            throw new IllegalArgumentException("unsafe report name");
        Path target = reportsDirectory.resolve(name).normalize();
        if (!target.getParent().equals(reportsDirectory))
            throw new IllegalArgumentException("report escapes directory");
        return target;
    }

    private void ensureDirectoryInsideWorld() throws IOException {
        Files.createDirectories(worldRoot);
        Files.createDirectories(reportsDirectory);
        Path realRoot = worldRoot.toRealPath();
        Path realReports = reportsDirectory.toRealPath();
        if (!realReports.startsWith(realRoot))
            throw new IOException("reports directory escapes world root through a link");
    }

    private void retainNewest() throws IOException {
        List<Path> reports = sortedReports();
        for (int index = MAX_REPORTS; index < reports.size(); index++) {
            Files.deleteIfExists(reports.get(index));
        }
    }

    private List<Path> sortedReports() throws IOException {
        List<Path> reports = new ArrayList<>();
        try (var files = Files.list(reportsDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> SAFE_NAME.matcher(path.getFileName().toString()).matches())
                    .forEach(reports::add);
        }
        reports.sort(Comparator.comparingLong(AssistantReportStore::lastModified)
                .reversed().thenComparing(
                        path -> path.getFileName().toString(), Comparator.reverseOrder()));
        return List.copyOf(reports);
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static void rejectSensitiveContent(byte[] bytes) {
        String lower = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (lower.contains("api_key") || lower.contains("api-key")
                || lower.contains("authorization:") || lower.contains("bearer ")
                || lower.contains("sk-") || lower.contains("chat_history")
                || lower.contains("level.dat") || lower.contains("chunk nbt"))
            throw new IllegalArgumentException("report contains forbidden sensitive data");
    }
}
