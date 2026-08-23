package com.cloudmart.common.handler;

import java.util.List;

import com.cloudmart.common.api.ApiError;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = mapBusinessCodeToStatus(ex.getCode());
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("UNAUTHORIZED", "请先登录"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of("VALIDATION_ERROR", "请求参数校验失败", violations);
        return ApiResponse.fail("VALIDATION_ERROR", "请求参数校验失败", error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations()
                .stream()
                .map(cv -> new ApiError.FieldViolation(extractPropertyPath(cv), cv.getMessage()))
                .toList();

        ApiError error = ApiError.of("VALIDATION_ERROR", "请求参数校验失败", violations);
        return ApiResponse.fail("VALIDATION_ERROR", "请求参数校验失败", error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return ApiResponse.fail("NOT_FOUND", "资源不存在");
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return ApiResponse.fail("MISSING_PARAMETER", "缺少必要参数: " + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ApiResponse.fail("INVALID_REQUEST_BODY", "请求体格式错误");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ApiResponse.fail("INVALID_PARAMETER_TYPE", "参数类型错误: " + ex.getName());
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMultipartException(MultipartException ex) {
        return ApiResponse.fail("INVALID_REQUEST", "请上传有效的文件");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiResponse.fail("METHOD_NOT_ALLOWED", "请求方法不支持: " + ex.getMethod());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ApiResponse.fail("INTERNAL_ERROR", "服务器内部错误");
    }

    private HttpStatus mapBusinessCodeToStatus(String code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (code) {
            case "UNAUTHORIZED", "TOKEN_EXPIRED", "TOKEN_REUSE_DETECTED", "INVALID_REFRESH_TOKEN",
                 "PERMISSION_FETCH_FAILED", "AUTH_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "ACCOUNT_LOCKED", "FORBIDDEN",
                 "WISH_NOT_AUTHOR", "WISH_RESTRICTED", "WISH_FORBIDDEN",
                 "WISH_CONSENT_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "USER_NOT_FOUND", "ROLE_NOT_FOUND", "MENU_NOT_FOUND",
                 "ACTIVITY_NOT_FOUND", "PRODUCT_NOT_FOUND", "TABLE_NOT_FOUND",
                 "ORDER_NOT_FOUND", "COUPON_NOT_FOUND", "TAG_NOT_FOUND",
                 "BADGE_NOT_FOUND", "REPORT_NOT_FOUND", "COMMENT_NOT_FOUND",
                 "POST_NOT_FOUND", "CATEGORY_NOT_FOUND", "BRAND_NOT_FOUND",
                 "WAREHOUSE_NOT_FOUND", "SHIPPING_NOT_FOUND",
                 "SHIPPING_ORDER_NOT_FOUND",
                 "PICK_ORDER_NOT_FOUND", "INBOUND_ORDER_NOT_FOUND",
                 "LIVE_ROOM_NOT_FOUND", "NOTIFICATION_NOT_FOUND",
                 "CONVERSATION_NOT_FOUND", "REVIEW_NOT_FOUND",
                 "SECKILL_PRODUCT_NOT_FOUND", "LEVEL_CONFIG_NOT_FOUND",
                 "SENSITIVE_WORD_NOT_FOUND", "BLACKLIST_NOT_FOUND",
                 "RISK_RULE_NOT_FOUND", "RISK_RECORD_NOT_FOUND",
                 "DICT_TYPE_NOT_FOUND", "DICT_DATA_NOT_FOUND",
                 "CONFIG_NOT_FOUND", "NOTICE_NOT_FOUND",
                 "DEPT_NOT_FOUND", "POSITION_NOT_FOUND",
                 "JOB_NOT_FOUND", "FILE_NOT_FOUND",
                 "PAYMENT_NOT_FOUND", "PROMOTION_NOT_FOUND",
                 "GROUP_ACTIVITY_NOT_FOUND", "GROUP_NOT_FOUND",
                 "MESSAGE_NOT_FOUND", "ROOM_NOT_FOUND",
                 "TEMPLATE_NOT_FOUND",
                 "WISHLIST_NOT_FOUND", "ADDRESS_NOT_FOUND",
                 "OPER_LOG_NOT_FOUND", "LOGIN_LOG_NOT_FOUND",
                 "WISH_NOT_FOUND", "WISH_CATEGORY_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "WISH_STARLIGHT_INSUFFICIENT" -> HttpStatus.PAYMENT_REQUIRED;
            case "WISH_CAPSULE_NOT_AVAILABLE" -> HttpStatus.CONFLICT;
            case "WISH_RATE_LIMITED", "WISH_AI_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "PRODUCT_SERVICE_UNAVAILABLE", "ORDER_SERVICE_UNAVAILABLE",
                 "USER_SERVICE_UNAVAILABLE", "COUPON_SERVICE_UNAVAILABLE",
                 "INVENTORY_SERVICE_UNAVAILABLE", "PAYMENT_SERVICE_UNAVAILABLE",
                 "NOTIFICATION_SERVICE_UNAVAILABLE", "SECKILL_SERVICE_UNAVAILABLE",
                 "CART_SERVICE_UNAVAILABLE", "RISK_SERVICE_UNAVAILABLE",
                 "WMS_SERVICE_UNAVAILABLE", "BRAND_SERVICE_UNAVAILABLE",
                 "MARKETING_SERVICE_UNAVAILABLE", "LIVE_SERVICE_UNAVAILABLE",
                 "AI_SERVICE_UNAVAILABLE", "REVIEW_SERVICE_UNAVAILABLE",
                 "WISH_AI_UNAVAILABLE",
                 "JWK_LOAD_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private String extractPropertyPath(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
