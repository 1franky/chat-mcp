package com.aidatachat.web;

import com.aidatachat.application.exception.DuplicateUserException;
import com.aidatachat.application.exception.InvalidCredentialsException;
import com.aidatachat.application.exception.LastAdministratorException;
import com.aidatachat.application.exception.RegistrationClosedException;
import com.aidatachat.application.exception.UserNotFoundException;
import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Solicitud invalida",
                "La solicitud no es valida.");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail invalidBody(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Solicitud invalida",
                "Revisa los campos enviados.");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "No fue posible autenticar",
                "El correo o la contrasena no son validos.");
    }

    @ExceptionHandler(RegistrationClosedException.class)
    ProblemDetail registrationClosed(RegistrationClosedException exception) {
        return problem(
                HttpStatus.FORBIDDEN,
                "registration-closed",
                "Registro cerrado",
                "El registro publico no esta disponible.");
    }

    @ExceptionHandler({DuplicateUserException.class, DataIntegrityViolationException.class})
    ProblemDetail duplicateUser(Exception exception) {
        return problem(
                HttpStatus.CONFLICT,
                "user-conflict",
                "Usuario no disponible",
                "No fue posible crear el usuario solicitado.");
    }

    @ExceptionHandler(LastAdministratorException.class)
    ProblemDetail lastAdministrator(LastAdministratorException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "last-administrator",
                "Operacion no permitida",
                "Debe existir al menos un administrador activo.");
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail userNotFound(UserNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "user-not-found",
                "Usuario no encontrado",
                "El usuario solicitado no existe o no esta activo.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "concurrent-update",
                "Conflicto de actualizacion",
                "El usuario cambio mientras se procesaba la solicitud.");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:ai-data-chat:problem:" + type));
        return problem;
    }
}
