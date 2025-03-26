package org;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

@Path("/api/compiler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompilerResource {

    @Inject
    CompilerService compilerService;

//    @POST
//    public CompletionStage<CodeSnippet> compileAndRun(CodeSnippet snippet) {
//        return compilerService.compileAndRun(snippet);
//    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response compileAndRun(CodeSnippet codeSnippet) {
        try {
            CompletableFuture<CodeSnippet> future = compilerService.compileAndRun(codeSnippet);
            CodeSnippet result = future.get();
            return Response.ok(result).build();
        } catch (InterruptedException | ExecutionException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error: " + e.getMessage())
                    .build();
        }
    }


}
