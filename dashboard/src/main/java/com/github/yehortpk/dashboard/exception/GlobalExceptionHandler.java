package com.github.yehortpk.dashboard.exception;

import com.github.yehortpk.dashboard.models.response.APIResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new APIResponse(400, ex.getMessage()));
    }

    @ExceptionHandler(VacancyNotFoundException.class)
    public ResponseEntity<Object> handleVacancyNotFoundException(VacancyNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(new APIResponse(404, ex.getMessage()));
    }
}
