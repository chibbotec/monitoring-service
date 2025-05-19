package com.ll.amdinservice.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
public class MetricsApiController {

  private final RestTemplate restTemplate;

  // 최근 쿼리 로그를 저장하는 큐 (최대 100개 항목을 저장)
  private static final ConcurrentLinkedQueue<QueryLog> recentQueryLogs = new ConcurrentLinkedQueue<>();
  private static final int MAX_LOG_SIZE = 100;

  // 로그 정보를 저장할 내부 클래스
  private static class QueryLog {
    private final String time;
    private final String service;
    private final String type;
    private final long executionTime;
    private final String sql;
    private final String source; // 소스 추적을 위한 필드 (p6spy, jpa 등)

    public QueryLog(String time, String service, String type, long executionTime, String sql, String source) {
      this.time = time;
      this.service = service;
      this.type = type;
      this.executionTime = executionTime;
      this.sql = sql;
      this.source = source;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("time", time);
      map.put("service", service);
      map.put("type", type);
      map.put("executionTime", executionTime);
      map.put("sql", sql);
      map.put("source", source);
      return map;
    }
  }

  @PostMapping("/log")
  public ResponseEntity<Map<String, Object>> addLogEntry(@RequestBody Map<String, Object> logData) {
    try {
      log.debug("Received log data: {}", logData);

      // 필수 필드 추출 및 유효성 검사
      String time = (String) logData.get("time");
      String service = (String) logData.get("service");
      String type = (String) logData.get("type");
      Object executionTimeObj = logData.get("executionTime");
      String sql = (String) logData.get("sql");
      String source = (String) logData.getOrDefault("source", "unknown");

      if (time == null || service == null || type == null || executionTimeObj == null || sql == null) {
        log.warn("Missing required fields in log data: {}", logData);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Missing required fields");
        return ResponseEntity.badRequest().body(response);
      }

      // Number를 long으로 변환
      long executionTime;
      if (executionTimeObj instanceof Number) {
        executionTime = ((Number) executionTimeObj).longValue();
      } else {
        try {
          executionTime = Long.parseLong(executionTimeObj.toString());
        } catch (NumberFormatException e) {
          log.warn("Invalid execution time format: {}", executionTimeObj);
          Map<String, Object> response = new HashMap<>();
          response.put("success", false);
          response.put("message", "Invalid execution time format");
          return ResponseEntity.badRequest().body(response);
        }
      }

      // 로그 추가
      addQueryLog(time, service, type, executionTime, sql, source);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error processing log entry: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("message", e.getMessage());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  @GetMapping("/{service}")
  public ResponseEntity<Map<String, Object>> getMetrics(@PathVariable String service) {
    Map<String, Object> result = new HashMap<>();

    try {
      // 서비스별 메트릭 데이터 수집
      if ("all".equals(service)) {
        // 모든 서비스의 메트릭 데이터 수집
        Map<String, Object> techInterviewMetrics = fetchServiceMetrics("techinterview");
        Map<String, Object> apiGatewayMetrics = fetchServiceMetrics("apigateway");

        // 데이터 병합
        result.put("techinterview", techInterviewMetrics);
        result.put("apigateway", apiGatewayMetrics);

        // 종합 통계 계산
        calculateAggregateMetrics(result, techInterviewMetrics, apiGatewayMetrics);
      } else {
        // 특정 서비스의 메트릭 데이터만 수집
        result = fetchServiceMetrics(service);
      }

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("Error fetching metrics: {}", e.getMessage(), e);
      result.put("error", e.getMessage());
      return ResponseEntity.internalServerError().body(result);
    }
  }

  private Map<String, Object> fetchServiceMetrics(String serviceName) {
    String url = "";

    // 서비스별 Actuator 엔드포인트 URL 설정
    switch (serviceName) {
      case "techinterview":
        url = "http://techinterview:9040/actuator/prometheus";
        break;
      case "apigateway":
        url = "http://apigateway:9000/actuator/prometheus";
        break;
      default:
        throw new IllegalArgumentException("지원하지 않는 서비스: " + serviceName);
    }

    try {
      // Prometheus 엔드포인트에서 메트릭 데이터 가져오기
      String prometheusData = restTemplate.getForObject(url, String.class);

      // Prometheus 형식 데이터 파싱
      return parsePrometheusData(prometheusData, serviceName);
    } catch (Exception e) {
      log.warn("Error fetching metrics for service {}: {}", serviceName, e.getMessage());
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("service", serviceName);
      errorResult.put("error", "서비스에 연결할 수 없습니다: " + e.getMessage());
      // Double 타입으로 통일
      errorResult.put("selectQueries", 0.0);
      errorResult.put("insertQueries", 0.0);
      errorResult.put("updateQueries", 0.0);
      errorResult.put("deleteQueries", 0.0);
      errorResult.put("otherQueries", 0.0);
      errorResult.put("queryExecutionTime", 0.0);
      errorResult.put("queryExecutionCount", 0.0);
      errorResult.put("queryTimeFast", 0.0);
      errorResult.put("queryTimeMedium", 0.0);
      errorResult.put("queryTimeSlow", 0.0);
      errorResult.put("queryTimeVerySlow", 0.0);
      errorResult.put("avgQueryTime", 0.0);
      errorResult.put("totalQueries", 0.0);
      return errorResult;
    }
  }

  private Map<String, Object> parsePrometheusData(String prometheusData, String serviceName) {
    Map<String, Object> result = new HashMap<>();

    // 서비스 정보 추가
    result.put("service", serviceName);

    // 쿼리 타입별 메트릭 추출
    result.put("selectQueries", extractMetricValue(prometheusData, "jpa_query_select_total"));
    result.put("insertQueries", extractMetricValue(prometheusData, "jpa_query_insert_total"));
    result.put("updateQueries", extractMetricValue(prometheusData, "jpa_query_update_total"));
    result.put("deleteQueries", extractMetricValue(prometheusData, "jpa_query_delete_total"));
    result.put("otherQueries", extractMetricValue(prometheusData, "jpa_query_other_total"));

    // 쿼리 실행 시간 메트릭 추출
    result.put("queryExecutionTime", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_sum"));
    result.put("queryExecutionCount", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_count"));

    // 쿼리 실행 시간 히스토그램
    result.put("queryTimeFast", extractMetricValue(prometheusData, "jpa_query_time_fast_total"));
    result.put("queryTimeMedium", extractMetricValue(prometheusData, "jpa_query_time_medium_total"));
    result.put("queryTimeSlow", extractMetricValue(prometheusData, "jpa_query_time_slow_total"));
    result.put("queryTimeVerySlow", extractMetricValue(prometheusData, "jpa_query_time_very_slow_total"));

    // 평균 쿼리 실행 시간 계산
    double totalTime = (double) result.get("queryExecutionTime");
    double totalCount = (double) result.get("queryExecutionCount");
    double avgTime = totalCount > 0 ? (totalTime / totalCount) * 1000 : 0; // ms로 변환
    result.put("avgQueryTime", avgTime);

    // 총 쿼리 수 계산
    double totalQueries = (double) result.get("selectQueries") +
        (double) result.get("insertQueries") +
        (double) result.get("updateQueries") +
        (double) result.get("deleteQueries") +
        (double) result.get("otherQueries");
    result.put("totalQueries", totalQueries);

    return result;
  }

  private double extractMetricValue(String prometheusData, String metricName) {
    String[] lines = prometheusData.split("\n");

    for (String line : lines) {
      // 라벨이 있는 메트릭을 올바르게 매치 (instance, job 등의 라벨이 포함됨)
      if (line.startsWith(metricName + "{") || line.equals(metricName + " ")) {
        String[] parts = line.split(" ");
        if (parts.length >= 2) {
          try {
            return Double.parseDouble(parts[parts.length - 1]);
          } catch (NumberFormatException e) {
            log.warn("메트릭 값 변환 실패: {}", line);
            return 0;
          }
        }
      }
    }

    // 메트릭을 찾지 못한 경우 디버깅 로그 추가
    log.debug("메트릭을 찾을 수 없음: {}", metricName);
    return 0;
  }

  private void calculateAggregateMetrics(Map<String, Object> result,
      Map<String, Object> techInterviewMetrics,
      Map<String, Object> apiGatewayMetrics) {
    // 두 서비스의 쿼리 수 합산
    double totalSelectQueries = (double) techInterviewMetrics.get("selectQueries") +
        (double) apiGatewayMetrics.get("selectQueries");

    double totalInsertQueries = (double) techInterviewMetrics.get("insertQueries") +
        (double) apiGatewayMetrics.get("insertQueries");

    double totalUpdateQueries = (double) techInterviewMetrics.get("updateQueries") +
        (double) apiGatewayMetrics.get("updateQueries");

    double totalDeleteQueries = (double) techInterviewMetrics.get("deleteQueries") +
        (double) apiGatewayMetrics.get("deleteQueries");

    double totalOtherQueries = (double) techInterviewMetrics.get("otherQueries") +
        (double) apiGatewayMetrics.get("otherQueries");

    // 총 쿼리 수
    double totalQueries = totalSelectQueries + totalInsertQueries +
        totalUpdateQueries + totalDeleteQueries + totalOtherQueries;

    // 쿼리 실행 시간 관련 지표 계산
    double totalExecutionTime = (double) techInterviewMetrics.get("queryExecutionTime") +
        (double) apiGatewayMetrics.get("queryExecutionTime");

    double totalExecutionCount = (double) techInterviewMetrics.get("queryExecutionCount") +
        (double) apiGatewayMetrics.get("queryExecutionCount");

    // 평균 쿼리 실행 시간 계산
    double avgQueryTime = totalExecutionCount > 0 ? (totalExecutionTime / totalExecutionCount) * 1000 : 0;

    // 합산된 메트릭을 result에 추가
    result.put("totalQueries", totalQueries);
    result.put("totalSelectQueries", totalSelectQueries);
    result.put("totalInsertQueries", totalInsertQueries);
    result.put("totalUpdateQueries", totalUpdateQueries);
    result.put("totalDeleteQueries", totalDeleteQueries);
    result.put("totalOtherQueries", totalOtherQueries);
    result.put("totalExecutionTime", totalExecutionTime);
    result.put("totalExecutionCount", totalExecutionCount);
    result.put("avgQueryTime", avgQueryTime);

    // 쿼리 시간 분포 합산
    result.put("totalQueryTimeFast", (double) techInterviewMetrics.get("queryTimeFast") +
        (double) apiGatewayMetrics.get("queryTimeFast"));
    result.put("totalQueryTimeMedium", (double) techInterviewMetrics.get("queryTimeMedium") +
        (double) apiGatewayMetrics.get("queryTimeMedium"));
    result.put("totalQueryTimeSlow", (double) techInterviewMetrics.get("queryTimeSlow") +
        (double) apiGatewayMetrics.get("queryTimeSlow"));
    result.put("totalQueryTimeVerySlow", (double) techInterviewMetrics.get("queryTimeVerySlow") +
        (double) apiGatewayMetrics.get("queryTimeVerySlow"));
  }

  // P6Spy 로그 추가 메서드
  public static void addQueryLog(String time, String service, String type, long executionTime, String sql, String source) {
    recentQueryLogs.add(new QueryLog(time, service, type, executionTime, sql, source));

    // 큐 크기 제한
    while (recentQueryLogs.size() > MAX_LOG_SIZE) {
      recentQueryLogs.poll();
    }
  }

  // 이전 버전과의 호환성을 위한 메서드
  public static void addQueryLog(String time, String service, String type, long executionTime, String sql) {
    addQueryLog(time, service, type, executionTime, sql, "unknown");
  }

  // 최근 쿼리 로그 가져오기
  @GetMapping("/logs/{service}")
  public ResponseEntity<Map<String, Object>> getQueryLogs(@PathVariable("service") String service) {
    log.info("================================================Get query logs for {}", service);
    Map<String, Object> result = new HashMap<>();
    List<Map<String, Object>> logs = new ArrayList<>();



    // 선택된 서비스에 해당하는 로그만 필터링
    for (QueryLog logquery : recentQueryLogs) {
      log.info("================================================: {}", logquery);
      if ("all".equals(service) || logquery.service.equals(service)) {
        logs.add(logquery.toMap());
      }
    }

    result.put("logs", logs);
    return ResponseEntity.ok(result);
  }
}