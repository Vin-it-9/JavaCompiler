package org;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class CodeSnippet {

    public Long id;
    public String title;
    public String sourceCode;
    public String language = "java";
    public String compilationOutput;
    public String executionOutput;
    public LocalDateTime createdAt = LocalDateTime.now();
    public Boolean compilationSuccess = false;
    public Boolean executionSuccess = false;
    public long compilationTimeMs;
    public long executionTimeMs;
    public long peakMemoryBytes;

    public Map<String, String> additionalFiles = new HashMap<>();
}