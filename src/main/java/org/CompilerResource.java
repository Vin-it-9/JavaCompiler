package org;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;

@Path("/api/compiler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompilerResource {

    @Inject
    CompilerService compilerService;

    @POST
    public CompletionStage<CodeSnippet> compileAndRun(CodeSnippet snippet) {
        return compilerService.compileAndRun(snippet);
    }
}