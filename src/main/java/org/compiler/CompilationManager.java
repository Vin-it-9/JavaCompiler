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
            
            // Build ECJ arguments - use lower compliance level where bootclasspath works
            var args = new java.util.ArrayList<String>();
            args.add("-d");
            args.add(workingDir.toString());
            args.add("-encoding");
            args.add("UTF-8");
            args.add("-source");
            args.add("1.8");  // Use Java 8 compliance (bootclasspath supported)
            args.add("-target");
            args.add("1.8");
            args.add("-nowarn");
            args.add("-g:none");
            args.add("-proc:none");  // Disable annotation processing
            
            // Use extracted JDK classes as bootclasspath (works with Java 8 compliance)
            String jdkClasses = System.getenv("JDK_CLASSES");
            if (jdkClasses != null && !jdkClasses.isEmpty()) {
                args.add("-bootclasspath");
                args.add(jdkClasses);
            }
            
            args.add(sourceFile.toString());
            
            // Compile using ECJ
            var compiler = new Main(out, err, false, null, null);
            boolean success = compiler.compile(args.toArray(new String[0]));
            
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
