package dev.rsvpfactory.problems;

import java.net.URI;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every refusal, validation error and framework error into a problem detail whose type names the rule. */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Refusal.class)
    ResponseEntity<ProblemDetail> refused(Refusal refusal) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(refusal.status(), refusal.getMessage());
        problem.setType(ProblemTypes.of(refusal.rule()));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(refusal.status());
        if (refusal.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"rsvp-factory\"");
        }
        return response.body(problem);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && URI.create("about:blank").equals(problem.getType())) {
            problem.setType(ProblemTypes.of(slug(statusCode)));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private @Nullable ResponseEntity<Object> validationFailed(
            Exception ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request is invalid.");
        problem.setType(ProblemTypes.of("validation-failed"));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static String slug(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null
                ? "error"
                : known.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
