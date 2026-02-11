package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.Optional;

@ApplicationScoped
public class CompilerService {
    
    @Inject
    CacheManager cacheManager;
    
    @Inject
    FileManager fileManager;
    
    @Inject
    CompilationManager compilationManager;
    
    @Inject
    ExecutionManager executionManager;
    
    @Inject
    SourceCodeAnalyzer analyzer;

    public CodeSnippet compileAndRun(CodeSnippet snippet) {
        String codeHash = analyzer.generateHash(snippet.sourceCode);
        return processCodeSnippet(snippet, codeHash);
    }

    private CodeSnippet processCodeSnippet(CodeSnippet snippet, String codeHash) {
        var validation = analyzer.validate(snippet.sourceCode);
        if (!validation.valid()) {
            snippet.compilationOutput = validation.message();
            snippet.compilationSuccess = false;
            return snippet;
        }
        
        String className = analyzer.extractClassName(snippet.sourceCode);
        
        Path tempDir = null;
        try {
            tempDir = fileManager.createTempDirectory();
            return executeCompilation(snippet, className, codeHash, tempDir);
        } catch (Exception e) {
            snippet.compilationOutput = e.getMessage();
            snippet.compilationSuccess = false;
            snippet.executionSuccess = false;
            return snippet;
        } finally {
            if (tempDir != null) {
                fileManager.deleteDirectory(tempDir);
            }
        }
    }

    private CodeSnippet executeCompilation(CodeSnippet snippet, String className, 
                                          String codeHash, Path workingDir) {
        Path sourceFile = workingDir.resolve(className + ".java");
        fileManager.writeFile(sourceFile, snippet.sourceCode);
        
        long compilationStart = System.nanoTime();
        CompilationManager.CompilationResult compilationResult;
        
        Optional<byte[]> cachedBytecode = cacheManager.get(codeHash);
        if (cachedBytecode.isPresent()) {
            Path classFile = workingDir.resolve(className + ".class");
            fileManager.writeBytes(classFile, cachedBytecode.get());
            compilationResult = new CompilationManager.CompilationResult("Cached", true);
        } else {
            compilationResult = compilationManager.compile(sourceFile, workingDir);
            if (compilationResult.success()) {
                Path classFile = workingDir.resolve(className + ".class");
                cacheManager.put(codeHash, fileManager.readBytes(classFile));
            }
        }
        
        snippet.compilationTimeMs = (System.nanoTime() - compilationStart) / 1_000_000;
        snippet.compilationOutput = compilationResult.output();
        snippet.compilationSuccess = compilationResult.success();
        
        if (snippet.compilationSuccess && analyzer.hasMainMethod(snippet.sourceCode)) {
            long execStart = System.nanoTime();
            ExecutionManager.ExecutionResult result = executionManager.execute(className, workingDir);
            snippet.executionTimeMs = (System.nanoTime() - execStart) / 1_000_000;
            snippet.executionOutput = result.output();
            snippet.executionSuccess = result.success();
            snippet.peakMemoryBytes = result.memoryUsed();
        } else if (snippet.compilationSuccess) {
            snippet.executionOutput = "No main method";
            snippet.executionSuccess = true;
        } else {
            snippet.executionOutput = "Compilation failed";
            snippet.executionSuccess = false;
        }
        
        return snippet;
    }
}
