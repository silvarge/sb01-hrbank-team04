package com.codeit.sb01hrbankteam04.global.exception;

import com.codeit.sb01hrbankteam04.global.response.CustomApiResponse;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 전역 예외 핸들러 - 전역 예외 처리를 위함.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 존재하지 않는 요청에 대한 예외.
   *
   * @param e
   * @return 500 INTERNAL SERVER ERROR 응답
   */
  @ExceptionHandler(value = {NoHandlerFoundException.class,
      HttpRequestMethodNotSupportedException.class})
  public CustomApiResponse<?> handleNoPageFoundException(Exception e, WebRequest request) {
    log.error("NoHandlerFoundException 발생 시각: {}, 요청 정보: {}, 예외: {}",
        LocalDateTime.now(),
        request.getDescription(false),
        e.getMessage(),
        e); // 마지막 ex가 StackTrace까지 함께 로깅
    return CustomApiResponse.fail(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
  }

  /**
   * 커스텀 예외에 대한 처리.
   *
   * @param e
   * @return 해당 Error 코드에 대응하는 에러 응답
   */
  @ExceptionHandler(value = {CustomException.class})
  public CustomApiResponse<?> handleCustomException(CustomException e, WebRequest request) {
    log.error("CustomException 발생 시각: {}, 요청 정보: {}, 예외: {}",
        LocalDateTime.now(),
        request.getDescription(false),
        e.getMessage(),
        e);
    return CustomApiResponse.fail(e);
  }

  /**
   * 기본적인 예외에 대한 처리
   *
   * @param e
   * @return 500 INTERNAL SERVER ERROR 응답
   */
  @ExceptionHandler(value = {Exception.class})
  public CustomApiResponse<?> handleException(Exception e, WebRequest request) {
    log.error("Exception 발생 시각: {}, 요청 정보: {}, 예외: {}",
        LocalDateTime.now(),
        request.getDescription(false),
        e.getMessage(),
        e);
    return CustomApiResponse.fail(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
  }

}
