package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@ApplicationScoped
public class CompilationManager {
    
    @Inject
    FileManager fileManager;

    public CompilationResult compile(Path sourceFile, Path workingDir) {
        return executeCompilation(sourceFile, workingDir);
    }
    
    private CompilationResult executeCompilation(Path sourceFile, Path workingDir) {
        try {
            // Use Eclipse Compiler for Java (ECJ) - works in native image
            var outputStream = new ByteArrayOutputStream();
            var errorStream = new ByteArrayOutputStream();
            var out = new PrintWriter(outputStream);
            var err = new PrintWriter(errorStream);
            
            // ECJ arguments
            String[] args = {
                "-d", workingDir.toString(),
                "-encoding", "UTF-8",
                "-source", "21",
                "-target", "21",
                "-nowarn",
                "-g:none",
                sourceFile.toString()
            };
            
            // Compile using ECJ
            var compiler = new Main(out, err, false, null, null);
            boolean success = compiler.compile(args);
            
            out.flush();
            err.flush();
            
            String output = errorStream.toString(StandardCharsets.UTF_8);
            if (output.isEmpty()) {
                output = outputStream.toString(StandardCharsets.UTF_8);
            }
            
            return new CompilationResult(
                success && output.isEmpty() ? "OK" : output, 
                success
            );
            
        } catch (Exception e) {
            return new CompilationResult("Compilation error: " + e.getMessage(), false);
        }
    }
    
    public record CompilationResult(String output, boolean success) {}
}
