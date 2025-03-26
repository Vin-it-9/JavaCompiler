package org;

import jakarta.enterprise.context.ApplicationScoped;
import org.CodeSnippet;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CompilerService {

    private static final long PROCESS_TIMEOUT_SECONDS = 10;

    private static final Pattern CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)\\s*\\{");

    public CompletableFuture<CodeSnippet> compileAndRun(CodeSnippet snippet) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempDir = null;

            try {
                String uniqueId = UUID.randomUUID().toString();
                tempDir = Files.createTempDirectory("java-compiler-" + uniqueId);
                String className = extractMainClassName(snippet.sourceCode);
                if (className == null) {
                    snippet.compilationOutput = "Error: Could not find a public class in your code.\n" +
                            "Please ensure your code contains a public class declaration like 'public class YourClassName {...}'";
                    snippet.compilationSuccess = false;
                    snippet.executionSuccess = false;
                    return snippet;
                }
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
                    snippet.compilationOutput = "Compilation timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds.\n" +
                            "Your code might be too complex or there might be an issue with the compiler.";
                    snippet.compilationSuccess = false;
                    return snippet;
                }

                String compilerOutput = new String(compilerProcess.getErrorStream().readAllBytes());
                int compileExitCode = compilerProcess.exitValue();

                snippet.compilationOutput = compilerOutput.isEmpty() ? "Compilation successful" : compilerOutput;
                snippet.compilationSuccess = compileExitCode == 0;

                if (snippet.compilationSuccess) {
                    if (!hasMainMethod(snippet.sourceCode)) {
                        snippet.executionOutput = "Class '" + className + "' compiled successfully, but no main method found.\n" +
                                "To run this code, add: public static void main(String[] args) {...}";
                        snippet.executionSuccess = true;
                        return snippet;
                    }

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
                                " seconds.\nCheck for infinite loops or optimize your code.";
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

                    if (snippet.executionOutput.isEmpty()) {
                        snippet.executionOutput = "Program executed successfully with no output.";
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

    private String extractMainClassName(String sourceCode) {
        Matcher matcher = CLASS_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean hasMainMethod(String sourceCode) {
        return sourceCode.contains("public static void main(String[]") ||
                sourceCode.contains("public static void main (String[]") ||
                sourceCode.contains("public static void main( String[]") ||
                sourceCode.contains("public static void main ( String[]");
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