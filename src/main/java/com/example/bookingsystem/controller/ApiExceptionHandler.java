package com.example.bookingsystem.controller;

import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail rule(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(
            e.getStatusCode(),
            e.getReason() == null ? "Request failed." : e.getReason()
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
    })
    public ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Check the required fields and use valid values."
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This time is already booked or overlaps existing availability."
        );
    }

    @ExceptionHandler({
        DataAccessException.class,
        org.springframework.transaction.TransactionTimedOutException.class,
        org.springframework.transaction.CannotCreateTransactionException.class,
    })
    public ProblemDetail database(Exception e) {
        LoggerFactory.getLogger(getClass()).error("Database request failed", e);
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The database is temporarily unavailable. Please try again."
        );
    }
}
