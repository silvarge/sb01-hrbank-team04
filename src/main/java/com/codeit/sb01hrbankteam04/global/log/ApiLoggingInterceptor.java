package com.codeit.sb01hrbankteam04.global.log;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ApiLoggingInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    long startTime = System.currentTimeMillis();
    log.info("[API 호출 시작] URI: {}", request.getURI());
    ClientHttpResponse response = execution.execute(request, body);

    long duration = System.currentTimeMillis() - startTime;
    log.info("[API 호출 완료] URI: {}, status: {}, duration: {}ms", request.getURI(),
        response.getStatusCode(), duration);
    return response;
  }
}
