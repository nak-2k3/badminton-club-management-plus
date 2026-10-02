package com.badmintonclub.clubmanagement.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi @Valid: trả về lỗi theo từng trường để frontend hiển thị dưới ô nhập
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", errors);
    }

    // JSON sai cú pháp hoặc sai kiểu (vd ngày không đúng dd/MM/yyyy, enum không tồn tại)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof JacksonException jacksonEx && !jacksonEx.getPath().isEmpty()) {
            List<JacksonException.Reference> path = jacksonEx.getPath();
            String field = path.get(path.size() - 1).getPropertyName();
            if (field != null) {
                // Nhận biết trường ngày theo kiểu đích (LocalDate, LocalDateTime...), không theo tên trường
                Class<?> targetType = jacksonEx instanceof MismatchedInputException mismatch
                        ? mismatch.getTargetType() : null;
                String message;
                if (targetType != null && LocalTime.class.isAssignableFrom(targetType)) {
                    message = "Giờ không hợp lệ (định dạng HH:mm)";
                } else if (targetType != null && Temporal.class.isAssignableFrom(targetType)) {
                    message = "Ngày không hợp lệ (định dạng dd/MM/yyyy)";
                } else {
                    message = "Giá trị không hợp lệ";
                }
                return build(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", Map.of(field, message));
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng", null);
    }

    // Tham số trên URL sai kiểu (vd ?status=abc, /api/members/abc)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Tham số '" + ex.getName() + "' không hợp lệ", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức " + ex.getMethod() + " không được hỗ trợ", null);
    }

    // Vi phạm ràng buộc DB (UNIQUE, FK, CHECK) lọt qua kiểm tra ở service, vd 2 request tạo cùng email đồng thời
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc vi phạm ràng buộc, vui lòng kiểm tra lại", null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrors());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng", null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, "Tài khoản đã ngừng hoạt động", null);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex) {
        return build(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa", null);
    }

    // @PreAuthorize không đủ quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện chức năng này", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Lỗi không xác định", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau", null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), message, errors));
    }
}
