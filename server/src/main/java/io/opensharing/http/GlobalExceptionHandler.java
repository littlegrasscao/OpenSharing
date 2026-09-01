package io.opensharing.http;

import io.opensharing.catalog.AssetNotFoundException;
import io.opensharing.catalog.CatalogAuthorizationException;
import io.opensharing.catalog.CatalogException;
import io.opensharing.catalog.UnsupportedAssetTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Renders every failure as the protocol's {@code {errorCode, message}} body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    return body(e.getStatus(), e.getErrorCode(), e.getMessage());
  }

  @ExceptionHandler(AssetNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAssetNotFound(AssetNotFoundException e) {
    return body(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_DOES_NOT_EXIST, e.getMessage());
  }

  @ExceptionHandler(UnsupportedAssetTypeException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedAsset(UnsupportedAssetTypeException e) {
    return body(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PARAMETER_VALUE, e.getMessage());
  }

  @ExceptionHandler(CatalogAuthorizationException.class)
  public ResponseEntity<ErrorResponse> handleCatalogAuthorization(
      CatalogAuthorizationException e) {
    if (e.reason() == CatalogAuthorizationException.Reason.ACCESS_DENIED) {
      return body(HttpStatus.FORBIDDEN, ErrorCodes.PERMISSION_DENIED, e.getMessage());
    }
    log.error("The sharing server could not authenticate to the catalog", e);
    return body(
        HttpStatus.BAD_GATEWAY,
        ErrorCodes.CATALOG_ERROR,
        "the sharing server could not authenticate to the catalog");
  }

  @ExceptionHandler(CatalogException.class)
  public ResponseEntity<ErrorResponse> handleCatalog(CatalogException e) {
    log.error("Catalog request failed", e);
    return body(HttpStatus.BAD_GATEWAY, ErrorCodes.CATALOG_ERROR, e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
    return body(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PARAMETER_VALUE, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleValidation(Exception e) {
    return body(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PARAMETER_VALUE, describeValidation(e));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
    return body(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, "request body is malformed");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException e) {
    log.debug("Rejected request that violated a uniqueness constraint", e);
    return body(
        HttpStatus.CONFLICT,
        ErrorCodes.RESOURCE_ALREADY_EXISTS,
        "the object already exists or conflicts with an existing object");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
    return body(
        HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_DOES_NOT_EXIST, "the endpoint does not exist");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("Unhandled server error", e);
    return body(
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "internal server error");
  }

  private static String describeValidation(Exception e) {
    if (e instanceof MethodArgumentNotValidException invalid) {
      return invalid.getBindingResult().getFieldErrors().stream()
          .findFirst()
          .map(error -> error.getField() + " " + error.getDefaultMessage())
          .orElse("request validation failed");
    }
    return "request validation failed";
  }

  private static ResponseEntity<ErrorResponse> body(
      HttpStatus status, String errorCode, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(errorCode, message));
  }
}
