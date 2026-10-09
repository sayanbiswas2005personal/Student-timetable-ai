package com.college.timetable.exception;

/** Raised when uploaded files are not an acceptable PDF. */
public class PdfImportException extends ApiException {

    public PdfImportException(String code, String message, String... details) {
        super(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }
}