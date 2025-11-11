package uj.wmii.pwj.gvt;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class FileHistoryManager {

    private static final Path HISTORY_PATH = Paths.get(".gvt/history");
    private static final Path VERSION_PATH = Paths.get(".gvt/versions");
    private static final Path PROJECT_PATH = Paths.get(".");
    private static final Path COPY_PREFIX = Paths.get("copy_");
    private static final Path HISTORY_PREFIX = Paths.get("history_");

    private Path getCopyFile(Path fileName) {
        return HISTORY_PATH.resolve(COPY_PREFIX + fileName.toString());
    }
    private Path getHistoryFile(Path fileName) {
        return HISTORY_PATH.resolve(HISTORY_PREFIX + fileName.toString());
    }
    public void initFileTracker(Path file, int version) throws IOException {
        Files.copy(file, getCopyFile(file), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(getHistoryFile(file), "Version: " + version + "\nChanges: init\n");
    }

    public void recordFileChanges(Path file, int numberVersion) throws IOException {
        Path historyFile = getHistoryFile(file);
        Path copyFile = getCopyFile(file);
        List<String> oldLines = Files.readAllLines(copyFile);
        List<String> newLines = Files.readAllLines(file);

        try (BufferedWriter writer = Files.newBufferedWriter(historyFile, StandardOpenOption.APPEND)) {
            writer.write("Version: " + numberVersion + "\n");
            writer.write("Changes: change\n");

            int maxLines = Math.max(oldLines.size(), newLines.size());
            for (int i = 0; i < maxLines; i++) {
                String oldLine = i < oldLines.size() ? oldLines.get(i) : "";
                String newLine = i < newLines.size() ? newLines.get(i) : "";
                if (!oldLine.equals(newLine)) {
                    writer.write("line " + i + "\n");
                    writer.write("-- " + oldLine.trim() + "\n");
                    writer.write("++ " + newLine.trim() + "\n");
                }
            }
        }
        Files.copy(file, copyFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public void detachFile(Path file) throws IOException {
        Path historyFile = getHistoryFile(file);
        Path copyFile = getCopyFile(file);
        List<String> lines = Files.readAllLines(historyFile);
        int lastIndex = -1;

        for (int i = lines.size() - 1; i >= 0; i--)
            if (lines.get(i).startsWith("Changes: ")) {
                lastIndex = i;
                break;
            }

        if ("init".equals(lines.get(lastIndex).split(" ")[1])) {
            Files.deleteIfExists(copyFile);
            Files.deleteIfExists(historyFile);
            return;
        }

        List<String> copyLines = Files.readAllLines(copyFile);
        for (int i = lastIndex + 1; i < lines.size(); i += 3) {
            int lineNumber = Integer.parseInt(lines.get(i).split(" ")[1]);
            String removed = lines.get(i + 1).substring(2);

            if (lineNumber < copyLines.size())
                copyLines.set(lineNumber, removed);
            else if (!removed.isEmpty())
                copyLines.add(removed);

        }
        Files.write(copyFile, copyLines);
        Files.write(historyFile, lines.subList(0, lastIndex - 1));
    }

    public void restoreChanges(int targetVersion, int lastVersion) throws IOException {
        Path versionFile = VERSION_PATH.resolve("version" + targetVersion + ".txt");
        String filesLine = Files.lines(versionFile).filter(l -> l.startsWith("Files:")).findFirst().orElse("");
        String filesPart = filesLine.contains(":") ? filesLine.split(":", 2)[1].trim() : filesLine.trim();
        String[] filesInVersion = filesPart.isEmpty() ? new String[0] : filesPart.split("\\s+");

        for (String fileName : filesInVersion) {
            Path historyFile = getHistoryFile(Path.of(fileName));
            Path copyFile = getCopyFile(Path.of(fileName));
            List<String> historyLines = Files.readAllLines(historyFile);
            List<String> lines = new ArrayList<>(Files.readAllLines(copyFile));
            if (targetVersion == lastVersion) {
                Files.copy(copyFile, PROJECT_PATH.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                continue;
            }

            for (int i = historyLines.size() - 1; i >= 0; i--) {
                String line = historyLines.get(i);
                if (!line.startsWith("Version: ")) continue;
                if (Integer.parseInt(line.split(":")[1].trim()) <= targetVersion) break;

                if (i + 1 < historyLines.size() && historyLines.get(i + 1).startsWith("Changes: change")) {
                    for(int j = i + 2; j < historyLines.size() && !historyLines.get(j).startsWith("Version: "); j += 3) {
                        int lineNum = Integer.parseInt(historyLines.get(j).split(" ")[1]);
                        String removed = historyLines.get(j + 1).substring(2);

                        if (lineNum < lines.size()) {
                            lines.set(lineNum, removed);
                        } else if (!removed.isEmpty()) {
                            while (lines.size() < lineNum) lines.add("");
                            lines.add(removed);
                        }
                    }
                }
            }
            Files.write(PROJECT_PATH.resolve(fileName), lines);
        }
    }
}
