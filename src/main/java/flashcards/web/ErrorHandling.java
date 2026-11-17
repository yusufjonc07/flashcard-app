package flashcards.web;

import flashcards.repository.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Map;

/**
 * Turns exceptions into messages for the user. Nothing is half-saved: the repository writes
 * each change in one statement or transaction, so after an error the data is unchanged.
 */
public final class ErrorHandling {

    private ErrorHandling() {
    }

    /** The reader's JSON API answers errors with {"error": "..."}. */
    @RestControllerAdvice(assignableTypes = ReaderApiController.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class Api {

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, String>> handle(Exception exception) {
            return ResponseEntity.status(status(exception)).body(Map.of("error", message(exception)));
        }
    }

    /**
     * HTML pages: a failed form post goes back to the page it came from with the message
     * shown at the top; anything else shows the error page.
     */
    @ControllerAdvice
    public static class Pages {

        @ExceptionHandler(Exception.class)
        public ModelAndView handle(Exception exception, HttpServletRequest request,
                                   HttpServletResponse response) {
            String referer = request.getHeader("Referer");
            HttpStatus status = status(exception);
            if ("POST".equals(request.getMethod()) && referer != null && status != HttpStatus.INTERNAL_SERVER_ERROR) {
                RequestContextUtils.getOutputFlashMap(request).put("error", message(exception));
                return new ModelAndView("redirect:" + referer);
            }
            response.setStatus(status.value());
            ModelAndView page = new ModelAndView("error");
            page.addObject("status", status.value());
            page.addObject("message", message(exception));
            return page;
        }
    }

    private static HttpStatus status(Exception exception) {
        if (exception instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException
                || exception instanceof MaxUploadSizeExceededException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof java.io.IOException) {
            return HttpStatus.BAD_GATEWAY;   // file or translation service problem
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String message(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
