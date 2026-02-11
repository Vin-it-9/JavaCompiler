package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class ExecutionManager {

    private static final long EXEC_TIMEOUT_SECONDS = 30;
    private static final int MAX_MEMORY_MB = 256;
    private static final int STACK_SIZE_KB = 1024;

    public ExecutionResult execute(String className, Path workingDir) {
        return runJavaProcess(className, workingDir);
    }
    
    private ExecutionResult runJavaProcess(String className, Path workingDir) {
        var output = new StringBuilder(512);
        
        try {
            List<String> command = buildExecutionCommand(className, workingDir);
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Map<String, String> env = processBuilder.environment();
            env.remove("JAVA_TOOL_OPTIONS");
            env.remove("_JAVA_OPTIONS");
            env.remove("JDK_JAVA_OPTIONS");
            
            Process process = processBuilder.start();
            
            try (var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isValidOutputLine(line)) {
                        output.append(line).append('\n');
                    }
                }
            }
            
            boolean completedInTime = process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!completedInTime) {
                process.destroyForcibly();
                return new ExecutionResult("Timeout", false, 0L);
            }
            
            int exitCode = process.exitValue();
            long memoryUsed = estimateMemoryUsage();
            
            String result = output.isEmpty() ? "OK" : output.toString().trim();
            return new ExecutionResult(result, exitCode == 0, memoryUsed);
            
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ExecutionResult(e.getMessage(), false, 0L);
        }
    }
    
    private List<String> buildExecutionCommand(String className, Path workingDir) {
        return List.of(
            "java",
            "-Xshare:on",
            "-XX:+UseSerialGC",
            "-XX:TieredStopAtLevel=1",
            "-Xms8m",
            "-Xmx" + MAX_MEMORY_MB + "m",
            "-Xss" + STACK_SIZE_KB + "k",
            "-XX:MaxMetaspaceSize=16m",
            "-XX:MetaspaceSize=8m",
            "-XX:+DisableAttachMechanism",
            "-Djava.awt.headless=true",
            "-cp", workingDir.toString(),
            className
        );
    }
    
    private boolean isValidOutputLine(String line) {
        return !line.startsWith("Picked up") &&
               !line.contains("JAVA_TOOL_OPTIONS") &&
               !line.contains("WARNING:");
    }
    
    private long estimateMemoryUsage() {
        return 150_000L; // 150KB baseline
    }
    
    public record ExecutionResult(String output, boolean success, long memoryUsed) {}
}
