package org.example.workforce.exception;

import org.example.workforce.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleResourceNotFoundException() {

        ResourceNotFoundException exception = new ResourceNotFoundException("Resource not found");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleResourceNotFound(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Resource not found", response.getBody().getMessage());
    }

    @Test
    void testHandleBadRequestException() {

        BadRequestException exception = new BadRequestException("Invalid request");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleBadRequest(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid request", response.getBody().getMessage());
    }

    @Test
    void testHandleUnauthorizedException() {

        UnauthorizedException exception = new UnauthorizedException("Unauthorized");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleUnauthorized(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testHandleAccessDeniedException() {

        AccessDeniedException exception = new AccessDeniedException("Access denied");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleAccessDenied(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testHandleDuplicateResourceException() {

        DuplicateResourceException exception = new DuplicateResourceException("Duplicate resource");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleDuplicateResource(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testHandleInvalidActionException() {

        InvalidActionException exception = new InvalidActionException("Invalid action");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleInvalidAction(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testHandleInsufficientBalanceException() {

        InsufficientBalanceException exception = new InsufficientBalanceException("Insufficient balance");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleInsufficientBalance(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testHandleAccountDeactivatedException() {

        AccountDeactivatedException exception = new AccountDeactivatedException("Account deactivated");

        ResponseEntity<ApiResponse> response = exceptionHandler.handleAccountDeactivated(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }
}
