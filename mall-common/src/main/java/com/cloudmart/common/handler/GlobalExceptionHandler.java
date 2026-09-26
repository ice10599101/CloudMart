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
        // 跨服务 Feign 降级码统一 503：fallback 工厂使用 {SERVICE}_SERVICE_UNAVAILABLE 命名，
        // 用后缀通配避免逐个登记（历史上 WISH/CHAT/CAPSULE 等曾遗漏而被误映射为 400）
        if (code.endsWith("_SERVICE_UNAVAILABLE")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return switch (code) {
            case "UNAUTHORIZED", "TOKEN_EXPIRED", "TOKEN_REUSE_DETECTED", "INVALID_REFRESH_TOKEN",
                 "PERMISSION_FETCH_FAILED", "AUTH_FAILED" -> HttpStatus.UNAUTHORIZED;
            case "ACCOUNT_LOCKED", "FORBIDDEN",
                 "WISH_NOT_AUTHOR", "WISH_RESTRICTED", "WISH_FORBIDDEN",
                 "WISH_CONSENT_REQUIRED",
                 "WISH_KICKED_COOLDOWN", "WISH_GROUP_LEADER_REQUIRED",
                 "PET_NOT_OWNER", "PET_NOT_PUBLIC", "PET_FORBIDDEN",
                 // 三期：家园未公开 / 无权操作留言
                 "PET_ROOM_PRIVATE", "PET_WALL_FORBIDDEN" -> HttpStatus.FORBIDDEN;
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
                 "WISH_NOT_FOUND", "WISH_CATEGORY_NOT_FOUND",
                 "WISH_FULFILLMENT_NOT_FOUND", "WISH_AI_GOAL_NOT_FOUND",
                 "WISH_AI_PROMPT_NOT_FOUND", "BGM_SONG_NOT_FOUND",
                 "GIFT_NOT_FOUND", "GIFT_TARGET_NOT_FOUND",
                 "POLL_NOT_FOUND", "SURVEY_NOT_FOUND",
                 "TREE_SPECIAL_EVENT_NOT_FOUND", "TREE_ENV_CONFIG_NOT_FOUND",
                 "WISH_GROUP_NOT_FOUND",
                 "PET_NOT_FOUND", "PET_ACTIVITY_NOT_FOUND", "PET_BATTLE_NOT_FOUND",
                 "PET_JOB_NOT_FOUND", "PET_STUDY_NOT_FOUND", "PET_ACHIEVEMENT_NOT_FOUND",
                 "PET_ITEM_NOT_FOUND", "PET_SKILL_NOT_FOUND", "PET_EVOLUTION_NOT_FOUND",
                 "PET_EVENT_NOT_FOUND",
                 // 三期：职业/关系/好友/家园/家具/留言/每日任务
                 "PET_CAREER_NOT_FOUND", "PET_RELATION_NOT_FOUND", "PET_FRIEND_NOT_FOUND",
                 "PET_ROOM_NOT_FOUND", "PET_FURNITURE_NOT_FOUND", "PET_WALL_MESSAGE_NOT_FOUND",
                 "PET_QUEST_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "WISH_STARLIGHT_INSUFFICIENT" -> HttpStatus.PAYMENT_REQUIRED;
            case "WISH_CAPSULE_NOT_AVAILABLE", "WISH_STATUS_CONFLICT",
                 "WISH_ALREADY_INTERACTED", "WISH_ALREADY_CHECKIN_TODAY",
                 "WISH_ALREADY_SIGNED_IN",
                 "GIFT_OFF_SHELF",
                 "WISH_MILESTONE_NOT_REACHED", "WISH_MILESTONE_ALREADY_CLAIMED",
                 "WISH_VERSION_CONFLICT", "WISH_OPERATION_CONFLICT", "WISH_NOT_FULFILLABLE",
                 "WISH_AI_GOAL_STATUS_INVALID",
                 "WISH_GROUP_FULL", "WISH_ALREADY_MEMBER",
                 "WISH_GROUP_KEYWORD_DUPLICATED",
                 "WISH_ASSET_IN_USE",
                 "POLL_ALREADY_VOTED",
                 "PET_ALREADY_EXISTS", "PET_ACTIVITY_CONFLICT",
                 "PET_ACTIVITY_NOT_FINISHED", "PET_ACTIVITY_ALREADY_CLAIMED",
                 "PET_BOTTLE_COOLDOWN", "PET_ENERGY_INSUFFICIENT", "PET_HUNGER_TOO_LOW",
                 "PET_STATE_FULL", "PET_LEVEL_REQUIRED",
                 "PET_BATTLE_CONFLICT", "PET_BATTLE_ALREADY_HANDLED",
                 "PET_BATTLE_SELF_CHALLENGE", "PET_BATTLE_OPPONENT_INVALID",
                 "PET_RENAME_COOLDOWN",
                 // 二期：多宠物/商城/背包/技能/进化/串门/活动（§1.1/§89）
                 "PET_PET_LIMIT_REACHED", "PET_ITEM_ALREADY_OWNED", "PET_ITEM_NOT_OWNED",
                 "PET_SKILL_ALREADY_LEARNED", "PET_SKILL_BOOK_REQUIRED",
                 "PET_EVOLUTION_REQUIRED", "PET_EVOLUTION_MAX",
                 "PET_VISIT_SELF", "PET_VISIT_COOLDOWN", "PET_VISIT_ENERGY_INSUFFICIENT",
                 "PET_EVENT_NOT_FINISHED", "PET_EVENT_ALREADY_CLAIMED", "PET_EVENT_ENDED",
                 // 三期：职业/关系/好友/家园/留言/每日任务
                 "PET_CAREER_LOCKED", "PET_CAREER_REQUIRED", "PET_CAREER_PROMOTE_REQUIRED", "PET_CAREER_MAX_TIER",
                 "PET_RELATION_SELF", "PET_RELATION_EXISTS", "PET_RELATION_LIMIT", "PET_RELATION_EXCLUSIVE",
                 "PET_RELATION_NOT_PENDING",
                 "PET_FRIEND_SELF", "PET_FRIEND_EXISTS", "PET_FRIEND_LIMIT",
                 "PET_ROOM_POS_OCCUPIED", "PET_FURNITURE_NOT_OWNED",
                 "PET_QUEST_NOT_FINISHED", "PET_QUEST_ALREADY_CLAIMED",
                 "PET_QUEST_CHEST_NOT_READY", "PET_QUEST_CHEST_CLAIMED",
                 "PET_STATE_CONFLICT", "PET_OPERATION_CONFLICT", "PET_USER_BUSY",
                 "PET_FURNITURE_ALREADY_PLACED" -> HttpStatus.CONFLICT;
            case "WISH_RATE_LIMITED", "WISH_AI_RATE_LIMITED", "UPLOAD_DAILY_LIMIT_EXCEEDED",
                 "PET_AI_RATE_LIMITED", "PET_INTERACTION_RATE_LIMITED", "PET_WALL_RATE_LIMITED",
                 "PET_QUOTA_EXHAUSTED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AI_SERVICE_UNAVAILABLE",
                 "WISH_AI_UNAVAILABLE", "PET_AI_UNAVAILABLE",
                 "PET_SETTLEMENT_PENDING",
                 "JWK_LOAD_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
            // 内部错误：下游服务经 Feign 回传的 INTERNAL_ERROR 必须保持 500，
            // 否则会被 default 分支误映射成 400，掩盖真实的服务端异常
            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private String extractPropertyPath(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
