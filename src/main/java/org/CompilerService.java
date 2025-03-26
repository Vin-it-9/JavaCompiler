package org;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@ApplicationScoped
public class CompilerService {

    private static final long PROCESS_TIMEOUT_SECONDS = 5;
    private static final int MAX_MEMORY_MB = 128;
    private static final int STACK_SIZE_KB = 512;
    private static final int MAX_SOURCE_SIZE_KB = 500;

    private static final long JVM_OVERHEAD_BYTES = 512 * 1024;

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("(?m)^\\s*public\\s+class\\s+(\\w+)\\s*\\{");
    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*\\]");
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?m)^\\s*(?:public\\s+)?class\\s+(\\w+)");

    private static final Pattern MEMORY_USAGE_PATTERN =
            Pattern.compile("Memory:\\s*(\\d+)");

    public CompletableFuture<CodeSnippet> compileAndRun(CodeSnippet snippet) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempDir = null;
            long startTime = System.currentTimeMillis();

            try {

                if (snippet.sourceCode == null || snippet.sourceCode.trim().isEmpty()) {
                    snippet.compilationOutput = "Error: Source code cannot be empty";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                String mainClassName = extractPublicClassName(snippet.sourceCode);
                if (mainClassName == null) {
                    snippet.compilationOutput = "Error: Could not find a public class in your code.";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                tempDir = createFastTempDirectory();

                Path sourceFile = tempDir.resolve(mainClassName + ".java");
                Files.writeString(sourceFile, snippet.sourceCode);

                long compilationStart = System.currentTimeMillis();
                CompilationResult compilationResult = compileCode(sourceFile, tempDir);
                long compilationEnd = System.currentTimeMillis();
                snippet.compilationTimeMs = compilationEnd - compilationStart;

                snippet.compilationOutput = compilationResult.output;
                snippet.compilationSuccess = compilationResult.success;

                if (snippet.compilationSuccess) {
                    if (!hasMainMethod(snippet.sourceCode)) {
                        snippet.executionOutput = "Class '" + mainClassName + "' compiled successfully, but no main method found.";
                        snippet.executionSuccess = true;
                        return snippet;
                    }

                    long executionStart = System.currentTimeMillis();
                    ExecutionResult executionResult = executeCode(mainClassName, tempDir);
                    long executionEnd = System.currentTimeMillis();
                    snippet.executionTimeMs = executionEnd - executionStart;

                    snippet.executionOutput = executionResult.output;
                    snippet.executionSuccess = executionResult.success;
                    snippet.peakMemoryBytes = executionResult.memoryUsed;
                } else {
                    snippet.executionOutput = "Compilation failed, execution skipped.";
                    snippet.executionSuccess = false;
                }

                return snippet;
            } catch (Exception e) {
                snippet.compilationOutput = "Server error: " + e.getMessage();
                snippet.compilationSuccess = false;
                snippet.executionSuccess = false;
                return snippet;
            } finally {
                if (tempDir != null) {
                    try {
                        deleteDirectory(tempDir.toFile());
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    private Path createFastTempDirectory() throws IOException {
        String baseTempDir = System.getProperty("java.io.tmpdir");
        SecureRandom random = new SecureRandom();
        String randomId = Long.toHexString(random.nextLong());
        Path tempDir = Paths.get(baseTempDir, "jc" + randomId);
        return Files.createDirectory(tempDir);
    }

    private CompilationResult compileCode(Path sourceFile, Path workingDir) {
        StringBuilder output = new StringBuilder();
        boolean success = false;

        try {
            List<String> command = new ArrayList<>();
            command.add("javac");
            command.add("-Xlint:all");
            command.add("-encoding");
            command.add("UTF-8");
            command.add("-J-Xshare:on");
            command.add("-J-XX:TieredStopAtLevel=1");
            command.add(sourceFile.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean completedInTime = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completedInTime) {
                process.destroyForcibly();
                output.append("Compilation timed out after ")
                        .append(PROCESS_TIMEOUT_SECONDS)
                        .append(" seconds.");
                return new CompilationResult(output.toString(), false);
            }

            int exitCode = process.exitValue();
            success = (exitCode == 0);

            if (success && output.length() == 0) {
                output.append("Compilation successful");
            }
        } catch (Exception e) {
            output.append("Compilation error: ").append(e.getMessage());
            success = false;
        }

        return new CompilationResult(output.toString(), success);
    }

    private ExecutionResult executeCode(String className, Path workingDir) {
        StringBuilder output = new StringBuilder();
        boolean success = false;
        long memoryUsed = 0;

        try {

            Path memoryFile = workingDir.resolve("memory.txt");
            Path memoryHelperPath = createMemoryHelperClass(workingDir, className);

            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-Xshare:on");
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
            command.add("-Xverify:none");
            command.add("-XX:+UseSerialGC");
            command.add("-XX:CICompilerCount=2");
            command.add("-Xms16m");
            command.add("-Xmx" + MAX_MEMORY_MB + "m");
            command.add("-Xss" + STACK_SIZE_KB + "k");
            command.add("-XX:MaxMetaspaceSize=64m");
            command.add("-Djava.awt.headless=true");
            command.add("-cp");
            command.add(workingDir.toString());

            command.add("MemoryHelper");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir.toFile());
            Map<String, String> env = processBuilder.environment();
            env.remove("JAVA_TOOL_OPTIONS");
            env.remove("_JAVA_OPTIONS");
            env.remove("JDK_JAVA_OPTIONS");
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Picked up") ||
                            line.contains("JAVA_TOOL_OPTIONS") ||
                            line.contains("Security Manager") ||
                            line.contains("WARNING:")) {
                        continue;
                    }
                    output.append(line).append('\n');
                }
            }

            boolean completedInTime = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completedInTime) {
                process.destroyForcibly();
                output = new StringBuilder("Execution timed out after ")
                        .append(PROCESS_TIMEOUT_SECONDS)
                        .append(" seconds.");
                return new ExecutionResult(output.toString(), false, 0);
            }

            int exitCode = process.exitValue();
            success = (exitCode == 0);

            if (Files.exists(memoryFile)) {
                String memoryData = Files.readString(memoryFile);
                try {
                    memoryUsed = Long.parseLong(memoryData.trim());
                    if (memoryUsed < 10000 || memoryUsed > 100_000_000) {
                        memoryUsed = 200_000;
                    }
                } catch (NumberFormatException e) {
                    memoryUsed = 200_000;
                }
            } else {
                memoryUsed = 200_000;
            }

            if (output.length() == 0) {
                output.append("Program executed successfully with no output.");
            }
        } catch (Exception e) {
            output = new StringBuilder("Execution error: ").append(e.getMessage());
            success = false;
        }

        return new ExecutionResult(output.toString().trim(), success, memoryUsed);
    }

    private Path createMemoryHelperClass(Path workingDir, String targetClass) throws IOException, InterruptedException {
        Path helperPath = workingDir.resolve("MemoryHelper.java");

        String helperCode =
                "public class MemoryHelper {\n" +
                        "    public static void main(String[] args) throws Exception {\n" +
                        "        System.gc();\n" +
                        "        Thread.sleep(50);\n" +
                        "        long before = getUsedMemory();\n" +
                        "        // Run the target class's main method\n" +
                        "        " + targetClass + ".main(args);\n" +
                        "        System.gc();\n" +
                        "        Thread.sleep(50);\n" +
                        "        long after = getUsedMemory();\n" +
                        "        long used = Math.max(0, after - before);\n" +
                        "        // Write memory usage to a file\n" +
                        "        java.nio.file.Files.writeString(java.nio.file.Paths.get(\"memory.txt\"), \n" +
                        "            String.valueOf(used));\n" +
                        "    }\n\n" +
                        "    private static long getUsedMemory() {\n" +
                        "        Runtime rt = Runtime.getRuntime();\n" +
                        "        return rt.totalMemory() - rt.freeMemory();\n" +
                        "    }\n" +
                        "}";

        Files.writeString(helperPath, helperCode);
        ProcessBuilder pb = new ProcessBuilder("javac", helperPath.toString());
        pb.directory(workingDir.toFile());
        Process p = pb.start();
        p.waitFor(2, TimeUnit.SECONDS);

        return helperPath;
    }

    private String extractPublicClassName(String sourceCode) {
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = CLASS_NAME_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean hasMainMethod(String sourceCode) {
        return MAIN_METHOD_PATTERN.matcher(sourceCode).find();
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private static class CompilationResult {
        final String output;
        final boolean success;

        CompilationResult(String output, boolean success) {
            this.output = output;
            this.success = success;
        }
    }

    private static class ExecutionResult {
        final String output;
        final boolean success;
        final long memoryUsed;

        ExecutionResult(String output, boolean success, long memoryUsed) {
            this.output = output;
            this.success = success;
            this.memoryUsed = memoryUsed;
        }
    }
}