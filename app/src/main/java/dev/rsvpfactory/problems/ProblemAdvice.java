package dev.rsvpfactory.problems;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every refusal into an RFC 9457 problem detail with a specific {@code type}. */
@RestControllerAdvice
class ProblemAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RuleBrokenException.class)
    ProblemDetail ruleBroken(RuleBrokenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setType(ProblemTypes.of(e.rule()));
        return problem;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request is invalid.");
        problem.setType(ProblemTypes.of("validation-failed"));
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && isUntyped(problem)) {
            HttpStatus status = HttpStatus.valueOf(statusCode.value());
            problem.setType(ProblemTypes.of(
                    status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-')));
        }
        return response;
    }

    private static boolean isUntyped(ProblemDetail problem) {
        URI type = problem.getType();
        return type == null || "about:blank".equals(type.toString());
    }
}
