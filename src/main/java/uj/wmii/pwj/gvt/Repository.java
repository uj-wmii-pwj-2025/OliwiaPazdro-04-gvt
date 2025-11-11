package uj.wmii.pwj.gvt;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Repository {

    private static final Path GVT = Paths.get(".gvt");
    private static final Path VERSIONS = GVT.resolve("versions");
    private static final Path HISTORY = GVT.resolve("history");
    private static final Path CONFIG = GVT.resolve("config.txt");
    private static final FileHistoryManager fileHistoryManager = new FileHistoryManager();

    public Result init() {
        try {
            if(Files.exists(GVT))
                return new Result(10, "Current directory is already initialized.");

            Files.createDirectories(VERSIONS);
            Files.createDirectories(HISTORY);
            Files.writeString(CONFIG, "Active version: 0 \nLast Version: 0\n");
            addVersion("GVT initialized.", 0, "");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(-3, "Underlying system problem. See ERR for details.");
        }
        return new Result("Current directory initialized successfully.");
    }

    public Result add(String[] args) {
        if (args.length < 2) return new Result(20, "Please specify file to add.");
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) return new Result(21, "File not found. File: " + file);
        String message = args.length > 3 ? args[3] : "File added successfully. File: " + file;

        try {
            List<String> lines = Files.readAllLines(Path.of(".gvt/versions/version" + getLastVersion() + ".txt"));
            String line = lines.size() >= 3 ? lines.get(2) : null;

            if (line != null) {
                if (line.contains(file.getFileName().toString()))
                    return new Result("File already added. File: " + file);

                fileHistoryManager.initFileTracker(file, Integer.parseInt(getLastVersion()) + 1);
                addVersion(message, Integer.parseInt(getLastVersion()) + 1,
                        line.replaceFirst("Files:", "") + " " + file);
            }
            return new Result("File added successfully. File: " + file);
        }  catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(22, "File cannot be added. See ERR for details. File: " + file);
        }
    }

    public Result detach(String[] args) {
        if (args.length < 2) return new Result(30, "Please specify file to detach.");
        String file = Path.of(args[1]).getFileName().toString();
        String message = args.length > 3 ? args[3] : "File detached successfully. File: " + file;

        try {
            List<String> lines = Files.readAllLines(Path.of(".gvt/versions/version" + getLastVersion() + ".txt"));
            String line = lines.size() >= 3 ? lines.get(2) : null;

            if (line == null || !Arrays.asList(line.split("\\s+")).contains(file))
                return new Result("File is not added to gvt. File: " + file);

            int startId = line.indexOf(file), endId = startId + file.length();
            if(startId > 0 && line.charAt(startId - 1) == ' ') startId--;
            if(endId < line.length() && line.charAt(endId + 1) == ' ') endId++;

            fileHistoryManager.detachFile(Path.of(file));
            addVersion(message, Integer.parseInt(getLastVersion()) + 1,
                    (line.substring(0, startId) + line.substring(endId)).replaceFirst("Files:", ""));
            return new Result("File detached successfully. File: " + file);
        }  catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(31, "File cannot be detached, see ERR for details. File: " + file);
        }
    }

    public Result checkout(String[] args){
        try {
            String number = args[1];
            if (Integer.parseInt(number) > Integer.parseInt(getLastVersion()))
                return new Result(60, "Invalid version number: " + number);

            List<String> lines = Files.readAllLines(CONFIG);
            if (!lines.isEmpty() && lines.get(0).startsWith("Active version:"))
                lines.set(0, "Active version: " + number);

            Files.write(CONFIG, lines, StandardOpenOption.TRUNCATE_EXISTING);
            fileHistoryManager.restoreChanges(Integer.parseInt(number), Integer.parseInt(getLastVersion()));
            return new Result("Checkout successful for version: " + number);
        }  catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(31, "Underlying system problem. See ERR for details.");
        }
    }

    public Result commit(String[] args) {
        if (args.length < 2) return new Result(50, "Please specify file to commit.");
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) return new Result(51, "File not found. File: " + file);
        String message = args.length > 3 ? args[3] : "File committed successfully. File: " + file;

        try {
            List<String> lines = Files.readAllLines(VERSIONS.resolve("version" + getLastVersion() + ".txt"));
            String line = lines.size() >= 3 ? lines.get(2) : null;

            if (line != null) {
                if (!line.contains(file.toString()))
                    return new Result("File is not added to gvt. File: " + file);

                fileHistoryManager.recordFileChanges(file, Integer.parseInt(getLastVersion()) + 1);
                addVersion(message , Integer.parseInt(getLastVersion()) + 1,
                        line.replaceFirst("Files:", ""));
            }
            return new Result("File committed successfully. File: " + file);
        }  catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(52, "File cannot be committed, see ERR for details. File: " + file);
        }
    }

    public Result history(String[] args) {
        try {
            List<Path> versionFiles = Files.list(VERSIONS).sorted(Comparator.reverseOrder()).toList();
            StringBuilder stringBuilder = new StringBuilder();
            int n = versionFiles.size();

            if (args.length > 2 && args[1].equals("-last") &&  Integer.parseInt(args[2]) < n)
                n = Integer.parseInt(args[2]);

            for (int i = 0; i < n; i++) {
                try(BufferedReader reader = Files.newBufferedReader(versionFiles.get(i))) {
                    String version = reader.readLine().split(":")[1].trim();
                    String message = reader.readLine().split(":", 2)[1].trim();
                    stringBuilder.append(version).append(": ").append(message).append("\n");
                }
            }
            return new Result(stringBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(-3, "Underlying system problem. See ERR for details.");
        }
    }

    public Result version(String[] args) {
        try {
            String number = args.length < 2 ? getLastVersion() : args[1];
            StringBuilder stringBuilder = new StringBuilder();

            if (Integer.parseInt(number) > Integer.parseInt(getLastVersion()))
                return new Result(60, "Invalid version number: " + number);

            try(BufferedReader reader = Files.newBufferedReader(VERSIONS.resolve("version" + getLastVersion() + ".txt"))) {
                stringBuilder.append(reader.readLine()).append("\n");
                StringBuilder msg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.contains("Files:"))
                    msg.append(line).append("\n");

                stringBuilder.append(msg.toString().replaceFirst("^\\s*Message:\\s*", "").trim());
            }
            return new Result(stringBuilder.toString());

        } catch (IOException e) {
            e.printStackTrace(System.err);
            return new Result(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private void addVersion(String message, int versionNumber, String files) throws IOException {
        Path version = VERSIONS.resolve("version" + versionNumber + ".txt");
        Files.writeString(version, "Version: " + versionNumber + "\nMessage: " + message + "\nFiles:" + files + "\n");
        List<String> lines = Files.readAllLines(CONFIG);
        if (lines.size() >= 2)
            lines.set(1, "Last Version: " + versionNumber);
        lines.set(0, "Active Version: " + versionNumber);
        Files.write(CONFIG, lines);
    }
    private static String getLastVersion() throws IOException {
        try(BufferedReader reader = Files.newBufferedReader(CONFIG)) {
            reader.readLine();
            return reader.readLine().split(":")[1].trim();
        }
    }
    public static boolean notInitialized() {
        return !Files.exists(GVT);
    }
}
