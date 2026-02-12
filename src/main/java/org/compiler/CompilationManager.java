package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class CompilationManager {
    
    @Inject
    FileManager fileManager;

    public CompilationResult compile(Path sourceFile, Path workingDir) {
        return executeCompilation(sourceFile, workingDir);
    }
    
    private CompilationResult executeCompilation(Path sourceFile, Path workingDir) {
        try {
            var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return new CompilationResult("Compiler unavailable", false);
            }
            
            var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
            // Set locale to en_US explicitly and use UTF-8 encoding
            var locale = java.util.Locale.US;
            var fileManager = compiler.getStandardFileManager(diagnostics, locale, StandardCharsets.UTF_8);
            var compilationUnits = fileManager.getJavaFileObjects(sourceFile);
            
            List<String> options = List.of(
                "-d", workingDir.toString(),
                "-encoding", "UTF-8",
                "-g:none",
                "-nowarn"
            );
            
            var task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            boolean success = task.call();
            
            var output = new StringBuilder(128);
            for (var diagnostic : diagnostics.getDiagnostics()) {
                output.append(diagnostic.getMessage(null)).append('\n');
            }
            
            fileManager.close();
            return new CompilationResult(
                success && output.isEmpty() ? "OK" : output.toString(), 
                success
            );
            
        } catch (Exception e) {
            return new CompilationResult(e.getMessage(), false);
        }
    }
    
    public record CompilationResult(String output, boolean success) {}
}
