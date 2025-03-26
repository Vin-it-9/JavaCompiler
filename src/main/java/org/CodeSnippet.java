package org;

import java.time.LocalDateTime;

public class CodeSnippet {


    public Long id;
    public String title;
    public String sourceCode;
    public String language = "java";
    public String compilationOutput;
    public String executionOutput;
    public LocalDateTime createdAt = LocalDateTime.now();
    public Boolean compilationSuccess;
    public Boolean executionSuccess;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCompilationOutput() {
        return compilationOutput;
    }

    public void setCompilationOutput(String compilationOutput) {
        this.compilationOutput = compilationOutput;
    }

    public String getExecutionOutput() {
        return executionOutput;
    }

    public void setExecutionOutput(String executionOutput) {
        this.executionOutput = executionOutput;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getCompilationSuccess() {
        return compilationSuccess;
    }

    public void setCompilationSuccess(Boolean compilationSuccess) {
        this.compilationSuccess = compilationSuccess;
    }

    public Boolean getExecutionSuccess() {
        return executionSuccess;
    }

    public void setExecutionSuccess(Boolean executionSuccess) {
        this.executionSuccess = executionSuccess;
    }
}