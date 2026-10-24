package com.example.wallet.api;
import com.example.wallet.api.ApiModels.ErrorResponse;import com.example.wallet.service.WalletException;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.ResponseEntity;import org.springframework.http.HttpStatus;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import java.util.UUID;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(WalletException.class) public ResponseEntity<ErrorResponse> wallet(WalletException e,HttpServletRequest r){return ResponseEntity.status(e.httpStatus()).body(new ErrorResponse(e.code(),e.getMessage(),requestId(r)));}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e,HttpServletRequest r){return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR","Request validation failed",requestId(r)));}
 @ExceptionHandler(Exception.class) public ResponseEntity<ErrorResponse> generic(Exception e,HttpServletRequest r){return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR","An unexpected error occurred",requestId(r)));}
 private String requestId(HttpServletRequest r){Object id=r.getAttribute("requestId");if(id==null){id=UUID.randomUUID().toString();r.setAttribute("requestId",id);}return id.toString();}
}
