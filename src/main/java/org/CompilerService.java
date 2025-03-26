package org;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CompilerService {

    private static final long PROCESS_TIMEOUT_SECONDS = 10;

    public CompletableFuture<CodeSnippet> compileAndRun(CodeSnippet snippet) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempDir = null;

            try {

                String uniqueId = UUID.randomUUID().toString();
                tempDir = Files.createTempDirectory("java-compiler-" + uniqueId);

                String className = "Main";

                File sourceFile = new File(tempDir.toFile(), className + ".java");
                try (PrintWriter writer = new PrintWriter(sourceFile)) {
                    writer.println(snippet.sourceCode);
                }

                ProcessBuilder compilerProcessBuilder = new ProcessBuilder(
                        "javac", sourceFile.getAbsolutePath()
                );
                compilerProcessBuilder.directory(tempDir.toFile());

                Process compilerProcess = compilerProcessBuilder.start();
                boolean compiledInTime = compilerProcess.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!compiledInTime) {
                    compilerProcess.destroyForcibly();
                    snippet.compilationOutput = "Compilation timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                String compilerOutput = new String(compilerProcess.getErrorStream().readAllBytes());
                int compileExitCode = compilerProcess.exitValue();

                snippet.compilationOutput = compilerOutput;
                snippet.compilationSuccess = compileExitCode == 0;

                if (snippet.compilationSuccess) {
                    ProcessBuilder javaProcessBuilder = new ProcessBuilder(
                            "java",
                            "-Xmx128m",
                            "-Xss1m",
                            "-cp", tempDir.toString(),
                            className
                    );
                    javaProcessBuilder.directory(tempDir.toFile());

                    Process javaProcess = javaProcessBuilder.start();

                    boolean executedInTime = javaProcess.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (!executedInTime) {
                        javaProcess.destroyForcibly();
                        snippet.executionOutput = "Execution timed out after " + PROCESS_TIMEOUT_SECONDS +
                                " seconds. Check for infinite loops or optimize your code.";
                        snippet.executionSuccess = false;
                        return snippet;
                    }
                    String executionStdOut = new String(javaProcess.getInputStream().readAllBytes());
                    String executionStdErr = new String(javaProcess.getErrorStream().readAllBytes());
                    int javaExitCode = javaProcess.exitValue();

                    snippet.executionOutput = executionStdOut;
                    if (!executionStdErr.isEmpty()) {
                        snippet.executionOutput += "\n\nErrors/Warnings:\n" + executionStdErr;
                    }
                    snippet.executionSuccess = javaExitCode == 0;
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
                // Clean up temp files
                if (tempDir != null) {
                    deleteDirectory(tempDir.toFile());
                }
            }
        });
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
}