package com.aidatachat.web;

import com.aidatachat.application.exception.ChatConflictException;
import com.aidatachat.application.exception.ConversationNotFoundException;
import com.aidatachat.application.exception.DocumentNotFoundException;
import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.exception.DocumentTooLargeException;
import com.aidatachat.application.exception.DuplicateUserException;
import com.aidatachat.application.exception.InvalidCredentialsException;
import com.aidatachat.application.exception.LastAdministratorException;
import com.aidatachat.application.exception.ProviderCommunicationException;
import com.aidatachat.application.exception.ProviderConflictException;
import com.aidatachat.application.exception.ProviderNotFoundException;
import com.aidatachat.application.exception.RegistrationClosedException;
import com.aidatachat.application.exception.UnsupportedDocumentTypeException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(DuplicateUserException.class)
    ProblemDetail duplicateUser(DuplicateUserException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "user-conflict",
                "Usuario no disponible",
                "No fue posible crear el usuario solicitado.");
    }

    @ExceptionHandler({ProviderConflictException.class, DataIntegrityViolationException.class})
    ProblemDetail resourceConflict(Exception exception) {
        return problem(
                HttpStatus.CONFLICT,
                "resource-conflict",
                "Recurso no disponible",
                "Ya existe un recurso con esos datos o fue modificado concurrentemente.");
    }

    @ExceptionHandler(ProviderNotFoundException.class)
    ProblemDetail providerNotFound(ProviderNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "provider-not-found",
                "Proveedor no encontrado",
                "La conexion solicitada no existe.");
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail conversationNotFound(ConversationNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "conversation-not-found",
                "Conversacion no encontrada",
                "La conversacion solicitada no existe.");
    }

    @ExceptionHandler(ChatConflictException.class)
    ProblemDetail chatConflict(ChatConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "chat-conflict",
                "Operacion de chat no disponible",
                "La conversacion tiene otra generacion activa o la seleccion ya no esta disponible.");
    }

    @ExceptionHandler(ProviderCommunicationException.class)
    ProblemDetail providerCommunication(ProviderCommunicationException exception) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_GATEWAY,
                        "provider-communication",
                        "Proveedor no disponible",
                        "No fue posible completar la operacion con el proveedor.");
        problem.setProperty("providerCode", exception.code());
        problem.setProperty("retryable", exception.retryable());
        if (exception.providerRequestId() != null) {
            problem.setProperty("providerRequestId", exception.providerRequestId());
        }
        return problem;
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

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail documentNotFound(DocumentNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "document-not-found",
                "Documento no encontrado",
                "El documento solicitado no existe.");
    }

    @ExceptionHandler({DocumentTooLargeException.class, MaxUploadSizeExceededException.class})
    ProblemDetail documentTooLarge(Exception exception) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "document-too-large",
                "Documento demasiado grande",
                "El archivo excede el tamano maximo permitido.");
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail unsupportedDocumentType(UnsupportedDocumentTypeException exception) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported-document-type",
                "Tipo de documento no soportado",
                "El formato del archivo no esta permitido o no coincide con su extension.");
    }

    @ExceptionHandler(DocumentStorageException.class)
    ProblemDetail documentStorageError(DocumentStorageException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "document-storage-error",
                "Error de almacenamiento",
                "No fue posible almacenar o recuperar el documento.");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:ai-data-chat:problem:" + type));
        return problem;
    }
}
