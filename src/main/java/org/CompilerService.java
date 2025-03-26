package org;


import jakarta.enterprise.context.ApplicationScoped;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@ApplicationScoped
public class CompilerService {

    private static final long PROCESS_TIMEOUT_SECONDS = 10;
    private static final int MAX_MEMORY_MB = 128;
    private static final int STACK_SIZE_KB = 1024;
    private static final int MAX_SOURCE_SIZE_KB = 500;

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("(?m)^\\s*public\\s+class\\s+(\\w+)\\s*\\{");



    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*\\]");


    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?m)^\\s*(?:public\\s+)?class\\s+(\\w+)");


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
                if (snippet.sourceCode.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_SIZE_KB * 1024) {
                    snippet.compilationOutput = "Error: Source code exceeds maximum size of " + MAX_SOURCE_SIZE_KB + "KB";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                tempDir = createSecureTempDirectory();

                String mainClassName = extractPublicClassName(snippet.sourceCode);
                if (mainClassName == null) {
                    snippet.compilationOutput = "Error: Could not find a public class in your code.\n" +
                            "Please ensure your code contains a public class declaration like 'public class YourClassName {...}'";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                File sourceFile = new File(tempDir.toFile(), mainClassName + ".java");
                try (PrintWriter writer = new PrintWriter(sourceFile, StandardCharsets.UTF_8)) {
                    writer.print(snippet.sourceCode);
                }

                long compilationStart = System.currentTimeMillis();

                CompilationResult compilationResult = compileCode(sourceFile, tempDir);

                long compilationEnd = System.currentTimeMillis();
                snippet.compilationTimeMs = compilationEnd - compilationStart;

                snippet.compilationOutput = compilationResult.output;
                snippet.compilationSuccess = compilationResult.success;


                if (snippet.compilationSuccess) {
                    if (!hasMainMethod(snippet.sourceCode)) {
                        snippet.executionOutput = "Class '" + mainClassName + "' compiled successfully, but no main method found.\n" +
                                "To run this code, add: public static void main(String[] args) {...}";
                        snippet.executionSuccess = true;
                        return snippet;
                    }

                    long executionStart = System.currentTimeMillis();

                    ExecutionResult executionResult = executeCode(mainClassName, tempDir);

                    long executionEnd = System.currentTimeMillis();
                    snippet.executionTimeMs = executionEnd - executionStart;

                    snippet.executionOutput = executionResult.output;
                    snippet.executionSuccess = executionResult.success;

                    Runtime runtime = Runtime.getRuntime();
                    long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
                    System.gc();
                    long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
                    snippet.peakMemoryBytes = Math.max(0, usedMemoryBefore - usedMemoryAfter);

                } else {
                    snippet.executionOutput = "Compilation failed, execution skipped.";
                    snippet.executionSuccess = false;
                }

                long totalTime = System.currentTimeMillis() - startTime;

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

    private Path createSecureTempDirectory() throws IOException {
        String baseTempDir = System.getProperty("java.io.tmpdir");
        SecureRandom random = new SecureRandom();
        String randomId = String.format("java-compiler-%016x", random.nextLong());
        Path tempDir = Paths.get(baseTempDir, randomId);

        try {
            return Files.createDirectory(tempDir,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) {
            return Files.createDirectory(tempDir);
        }
    }

    private CompilationResult compileCode(File sourceFile, Path workingDir) {
        StringBuilder output = new StringBuilder();
        boolean success = false;

        try {
            List<String> command = new ArrayList<>();
            command.add("javac");
            command.add("-Xlint:all");
            command.add("-encoding");
            command.add("UTF-8");
            command.add(sourceFile.getAbsolutePath());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completedInTime = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completedInTime) {
                process.destroyForcibly();
                output.append("Compilation timed out after ")
                        .append(PROCESS_TIMEOUT_SECONDS)
                        .append(" seconds.\nYour code might be too complex or contain an error.");
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

        try {

            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-Xmx" + MAX_MEMORY_MB + "m");
            command.add("-Xss" + STACK_SIZE_KB + "k");
            command.add("-XX:+UseSerialGC");
            command.add("-XX:MaxMetaspaceSize=64m");
            command.add("-Djava.awt.headless=true");
            command.add("-cp");
            command.add(workingDir.toString());
            command.add(className);

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completedInTime = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completedInTime) {
                process.destroyForcibly();
                output.append("Execution timed out after ")
                        .append(PROCESS_TIMEOUT_SECONDS)
                        .append(" seconds.\nCheck for infinite loops or optimize your code.");
                return new ExecutionResult(output.toString(), false);
            }

            int exitCode = process.exitValue();
            success = (exitCode == 0);

            if (output.length() == 0) {
                output.append("Program executed successfully with no output.");
            }

        } catch (Exception e) {
            output.append("Execution error: ").append(e.getMessage());
            success = false;
        }

        return new ExecutionResult(output.toString().trim(), success);
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
                        if (!file.delete()) {
                        }
                    }
                }
            }
            if (!directory.delete()) {
            }
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

        ExecutionResult(String output, boolean success) {
            this.output = output;
            this.success = success;
        }
    }
}