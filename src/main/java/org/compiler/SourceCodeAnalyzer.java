package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class SourceCodeAnalyzer {

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("(?m)^\\s*public\\s+class\\s+(\\w+)\\s*\\{");
    
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?m)^\\s*(?:public\\s+)?class\\s+(\\w+)");
    
    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*\\]");

    public String extractClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }
        
        var publicMatcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (publicMatcher.find()) {
            return publicMatcher.group(1);
        }
        
        var classMatcher = CLASS_NAME_PATTERN.matcher(sourceCode);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        
        return null;
    }

    public boolean hasMainMethod(String sourceCode) {
        return sourceCode != null && 
               !sourceCode.isBlank() && 
               MAIN_METHOD_PATTERN.matcher(sourceCode).find();
    }

    public ValidationResult validate(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return new ValidationResult(false, "Empty source");
        }
        
        String className = extractClassName(sourceCode);
        if (className == null) {
            return new ValidationResult(false, "No class found");
        }
        
        return new ValidationResult(true, "Valid");
    }

    public String generateHash(String sourceCode) {
        return Integer.toHexString(sourceCode.hashCode());
    }
    
    public record ValidationResult(boolean valid, String message) {}
}
