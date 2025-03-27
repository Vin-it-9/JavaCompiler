package org;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@ApplicationScoped
public class CompilerService {

    private static final long PROCESS_TIMEOUT_SECONDS = 10;      
    private static final int MAX_MEMORY_MB = 256;
    private static final int STACK_SIZE_KB = 1024;
    private static final int MAX_SOURCE_SIZE_KB = 1000;
    private static final int IO_BUFFER_SIZE = 65536;
    private static final long JVM_OVERHEAD_BYTES = 1024 * 1024;

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("(?m)^\\s*public\\s+class\\s+(\\w+)\\s*\\{");
    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*\\]");
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?m)^\\s*(?:public\\s+)?class\\s+(\\w+)");

    private static final Pattern MEMORY_USAGE_PATTERN =
            Pattern.compile("Memory:\\s*(\\d+)");

    private final Map<String, byte[]> classCache = new ConcurrentHashMap<>();
    private final ExecutorService compilerService =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

    private final SecureRandom random = new SecureRandom();

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

                String codeHash = Integer.toHexString(snippet.sourceCode.hashCode());
                String mainClassName = extractPublicClassName(snippet.sourceCode);

                if (mainClassName == null) {
                    snippet.compilationOutput = "Error: Could not find a public class in your code.";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                tempDir = createFastTempDirectory();

                Path sourceFile = tempDir.resolve(mainClassName + ".java");
                writeStringToFile(sourceFile, snippet.sourceCode);

                long compilationStart = System.currentTimeMillis();
                CompilationResult compilationResult;

                if (classCache.containsKey(codeHash)) {
                    Path classFile = tempDir.resolve(mainClassName + ".class");
                    Files.write(classFile, classCache.get(codeHash));
                    compilationResult = new CompilationResult("Compilation successful (cached)", true);
                } else {
                    compilationResult = compileCode(sourceFile, tempDir);

                    if (compilationResult.success) {
                        try {
                            Path classFile = tempDir.resolve(mainClassName + ".class");
                            byte[] bytecode = Files.readAllBytes(classFile);
                            classCache.put(codeHash, bytecode);
                        } catch (IOException e) {

                        }
                    }
                }

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
                        deleteDirectoryAsync(tempDir.toFile());
                    } catch (Exception e) {

                    }
                }
            }
        }, compilerService);
    }

    private Path createFastTempDirectory() throws IOException {
        String baseTempDir = System.getProperty("java.io.tmpdir");
        String randomId = Long.toHexString(random.nextLong());
        Path tempDir = Paths.get(baseTempDir, "jc" + randomId);
        return Files.createDirectory(tempDir);
    }

    private void writeStringToFile(Path file, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            byte[] buffer = new byte[IO_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                channel.write(ByteBuffer.wrap(buffer, 0, bytesRead));
            }
        }
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
            command.add("-J-XX:+UseSerialGC");
            command.add("-d");
            command.add(workingDir.toString());
            command.add(sourceFile.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()), IO_BUFFER_SIZE)) {
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
            command.add("-Xms32m");
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

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), IO_BUFFER_SIZE)) {
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
                try {
                    memoryUsed = Long.parseLong(Files.readString(memoryFile).trim());
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
                        "        // Force several GCs to get a clean baseline\n" +
                        "        for (int i = 0; i < 3; i++) {\n" +
                        "            System.gc();\n" +
                        "            Thread.sleep(50);\n" +
                        "        }\n" +
                        "        \n" +
                        "        long baseline = getUsedMemory();\n" +
                        "        long peakUsage = 0;\n" +
                        "        \n" +
                        "        // Create a memory monitoring thread\n" +
                        "        Thread memoryMonitor = new Thread(() -> {\n" +
                        "            try {\n" +
                        "                long currentPeak = 0;\n" +
                        "                while (!Thread.currentThread().isInterrupted()) {\n" +
                        "                    long current = getUsedMemory() - baseline;\n" +
                        "                    if (current > currentPeak) {\n" +
                        "                        currentPeak = current;\n" +
                        "                        java.nio.file.Files.writeString(java.nio.file.Paths.get(\"memory.txt\"), \n" +
                        "                            String.valueOf(currentPeak));\n" +
                        "                    }\n" +
                        "                    Thread.sleep(10); // Sample every 10ms\n" +
                        "                }\n" +
                        "            } catch (Exception e) {\n" +
                        "                // Thread interrupted or other error\n" +
                        "            }\n" +
                        "        });\n" +
                        "        \n" +
                        "        memoryMonitor.setDaemon(true);\n" +
                        "        memoryMonitor.start();\n" +
                        "        \n" +
                        "        try {\n" +
                        "            " + targetClass + ".main(args);\n" +
                        "        } catch (Throwable t) {\n" +
                        "            System.out.println(\"Exception: \" + t.getClass().getName() + \": \" + t.getMessage());\n" +
                        "            t.printStackTrace();\n" +
                        "        } finally {\n" +
                        "            // Stop the monitoring thread\n" +
                        "            memoryMonitor.interrupt();\n" +
                        "            Thread.sleep(100); // Give time for final readings\n" +
                        "            \n" +
                        "            // If no memory readings were taken, use a calculation\n" +
                        "            if (!java.nio.file.Files.exists(java.nio.file.Paths.get(\"memory.txt\"))) {\n" +
                        "                System.gc();\n" +
                        "                long after = getUsedMemory();\n" +
                        "                long used = Math.max(10000, after - baseline);\n" +
                        "                java.nio.file.Files.writeString(java.nio.file.Paths.get(\"memory.txt\"), \n" +
                        "                    String.valueOf(used));\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n\n" +
                        "    private static long getUsedMemory() {\n" +
                        "        Runtime rt = Runtime.getRuntime();\n" +
                        "        return rt.totalMemory() - rt.freeMemory();\n" +
                        "    }\n" +
                        "}";

        Files.writeString(helperPath, helperCode);

        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-J-Xshare:on");
        command.add("-J-XX:TieredStopAtLevel=1");
        command.add("-J-XX:+UseSerialGC");
        command.add(helperPath.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
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

    private void deleteDirectoryAsync(File directory) {
        CompletableFuture.runAsync(() -> {
            if (directory.exists()) {
                try {
                    Files.walk(directory.toPath())
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                }
            }
        });
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

    @PreDestroy
    public void cleanup() {
        compilerService.shutdown();
        try {
            compilerService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}