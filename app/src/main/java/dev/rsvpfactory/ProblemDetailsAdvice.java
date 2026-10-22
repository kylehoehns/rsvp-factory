package dev.rsvpfactory;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every refusal is an RFC 9457 problem detail whose {@code type} names the rule broken. */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "/problems/";

    @ExceptionHandler(RuleViolation.class)
    ResponseEntity<Object> ruleViolation(RuleViolation violation) {
        return problem(violation.status(), violation.rule(), violation.detail());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthenticated(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in with a member's username and password");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> problems = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", String.join("; ", problems));
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String rule, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + rule));
        problem.setTitle(rule);
        return ResponseEntity.status(status).body(problem);
    }
}
