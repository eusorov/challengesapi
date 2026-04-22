package com.challenges.api.web;

import com.authspring.api.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.findFirst()
				.orElse("Validation error");
		return problem(HttpStatus.BAD_REQUEST, msg);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> notReadable(HttpMessageNotReadableException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Malformed JSON or incompatible body: " + ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex) {
		String name = ex.getName();
		Object value = ex.getValue();
		String msg = "Invalid value for '%s': '%s'".formatted(name, value);
		return problem(HttpStatus.BAD_REQUEST, msg);
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	public ResponseEntity<ProblemDetail> badRequest(RuntimeException ex) {
		return problem(HttpStatus.BAD_REQUEST, ex.getMessage() != null ? ex.getMessage() : "Bad request");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
		return problem(
				HttpStatus.CONFLICT, "Data integrity violation: " + ex.getMostSpecificCause().getMessage());
	}

	/** MVC "not found" / method-not-allowed as {@link ErrorResponse} + {@link ServletException}, not {@link ResponseStatusException}. */
	@ExceptionHandler({
		NoResourceFoundException.class,
		NoHandlerFoundException.class,
		HttpRequestMethodNotSupportedException.class,
	})
	public ResponseEntity<ProblemDetail> mvcErrorResponse(ServletException ex, HttpServletRequest request) {
		ErrorResponse er = (ErrorResponse) ex;
		HttpStatusCode statusCode = er.getStatusCode();
		ProblemDetail pd = er.getBody();
		if (pd == null) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			if (status == null) {
				status = HttpStatus.INTERNAL_SERVER_ERROR;
			}
			pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		}
		pd.setInstance(URI.create(request.getRequestURI()));
		return ResponseEntity.status(statusCode).body(pd);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		String detail = ex.getReason();
		if (detail == null || detail.isBlank()) {
			detail = status.getReasonPhrase();
		}
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setInstance(URI.create(request.getRequestURI()));
		return ResponseEntity.status(status).body(pd);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
			ProblemDetail pd =
					ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
			pd.setTitle("Unauthorized");
			pd.setInstance(URI.create(request.getRequestURI()));
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
		}
		String detail = ex.getMessage();
		if (detail == null || detail.isBlank()) {
			detail = "Access is denied.";
		}
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
		pd.setTitle("Forbidden");
		pd.setInstance(URI.create(request.getRequestURI()));
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> fallback(Exception ex) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		return ResponseEntity.status(status).body(pd);
	}
}
