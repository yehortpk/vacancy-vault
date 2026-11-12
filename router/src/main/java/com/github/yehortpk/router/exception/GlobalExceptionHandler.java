package com.github.yehortpk.router.exception;

import com.github.yehortpk.router.models.response.APIResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        // Extract validation errors
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new APIResponse(400, ex.getMessage()));
    }

    @ExceptionHandler({ProgressNotFoundException.class, ParserProgressNotFoundException.class, ParserPageProgressNotFoundException.class})
    public ResponseEntity<Object> handleProgressNotFoundException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new APIResponse(404, ex.getMessage()));
    }

    /**
     * A 4xx from a downstream service (e.g. parsing already started, or not running) is the
     * caller's error too, so it is relayed as is instead of surfacing as a 500.
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Object> handleDownstreamClientErrorException(HttpClientErrorException ex) {
        MediaType contentType = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.TEXT_PLAIN)
                .body(ex.getResponseBodyAsString());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Object> handleEmailAlreadyRegisteredException(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(CONFLICT).body(new APIResponse(409, ex.getMessage()));
    }

    /**
     * A bad password, an unknown email and a disabled account all answer the same way, so
     * the response cannot be used to enumerate registered addresses.
     */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, AuthenticationException.class})
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(UNAUTHORIZED).body(new APIResponse(401, "Invalid email or password"));
    }

    @ExceptionHandler(DestinationAlreadyLinkedException.class)
    public ResponseEntity<Object> handleDestinationAlreadyLinkedException(DestinationAlreadyLinkedException ex) {
        return ResponseEntity.status(CONFLICT).body(new APIResponse(409, ex.getMessage()));
    }

    @ExceptionHandler(InvalidLinkTokenException.class)
    public ResponseEntity<Object> handleInvalidLinkTokenException(InvalidLinkTokenException ex) {
        return ResponseEntity.status(UNAUTHORIZED).body(new APIResponse(401, ex.getMessage()));
    }

    @ExceptionHandler(ProviderNotAvailableException.class)
    public ResponseEntity<Object> handleProviderNotAvailableException(RuntimeException ex) {
        return ResponseEntity.status(NOT_FOUND).body(new APIResponse(404, ex.getMessage()));
    }

    /**
     * 404 rather than 403, so a caller cannot tell an id that does not exist from one that
     * belongs to somebody else and therefore cannot enumerate connection or filter ids.
     */
    @ExceptionHandler({ConnectionNotFoundException.class, FilterNotFoundException.class})
    public ResponseEntity<Object> handleOwnedResourceNotFoundException(RuntimeException ex) {
        return ResponseEntity.status(NOT_FOUND).body(new APIResponse(404, ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidTokenException(InvalidTokenException ex) {
        return ResponseEntity.status(UNAUTHORIZED).body(new APIResponse(401, ex.getMessage()));
    }
}
