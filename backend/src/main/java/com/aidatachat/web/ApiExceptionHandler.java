package com.aidatachat.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "La solicitud no es valida.");
        problem.setTitle("Solicitud invalida");
        problem.setType(URI.create("urn:ai-data-chat:problem:invalid-request"));
        return problem;
    }
}
