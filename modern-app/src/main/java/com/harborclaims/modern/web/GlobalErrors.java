package com.harborclaims.modern.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Renders 404 and 500 directly (no sendError, no error dispatch), with no stack trace or internal message. */
@ControllerAdvice
public class GlobalErrors {

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound() {
        return page(HttpStatus.NOT_FOUND, "Not found");
    }

    /**
     * Anything unexpected is a 500 page with no detail. Exceptions Spring already knows how to answer (an unknown path is a 404, a wrong
     * method a 405, a bad parameter a 400) and security exceptions are passed on untouched, so they keep their own status.
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView failure(Exception e) throws Exception {
        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException
                || e instanceof jakarta.servlet.ServletException
                || e instanceof org.springframework.web.ErrorResponse
                || e instanceof org.springframework.beans.TypeMismatchException) {
            throw e;
        }
        org.slf4j.LoggerFactory.getLogger(GlobalErrors.class).error("Unhandled error", e);
        return page(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    private static ModelAndView page(HttpStatus status, String title) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", title);
        return mav;
    }
}
