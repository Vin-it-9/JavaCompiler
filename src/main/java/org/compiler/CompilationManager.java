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
            
            // Build options with explicit --system if JAVA_HOME is set
            var options = new java.util.ArrayList<String>();
            options.add("-d");
            options.add(workingDir.toString());
            options.add("-encoding");
            options.add("UTF-8");
            options.add("-g:none");
            options.add("-nowarn");
            
            // Add system module path if JAVA_HOME is available
            String javaHome = System.getProperty("java.home");
            if (javaHome != null && !javaHome.isEmpty()) {
                options.add("--system");
                options.add(javaHome);
            }
            
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
