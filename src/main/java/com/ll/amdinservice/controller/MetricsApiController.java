package com.ll.amdinservice.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
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

    public QueryLog(String time, String service, String type, long executionTime, String sql) {
      this.time = time;
      this.service = service;
      this.type = type;
      this.executionTime = executionTime;
      this.sql = sql;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("time", time);
      map.put("service", service);
      map.put("type", type);
      map.put("executionTime", executionTime);
      map.put("sql", sql);
      return map;
    }
  }

  @PostMapping("/log")
  public ResponseEntity<Void> addLogEntry(@RequestBody Map<String, Object> logData) {
    String time = (String) logData.get("time");
    String service = (String) logData.get("service");
    String type = (String) logData.get("type");
    long executionTime = ((Number) logData.get("executionTime")).longValue();
    String sql = (String) logData.get("sql");

    addQueryLog(time, service, type, executionTime, sql);

    return ResponseEntity.ok().build();
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
    result.put("queryTimeFast", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.01\"}"));
    result.put("queryTimeMedium", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.1\"}") -
        extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.01\"}"));
    result.put("queryTimeSlow", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.5\"}") -
        extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.1\"}"));
    result.put("queryTimeVerySlow", extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"+Inf\"}") -
        extractMetricValue(prometheusData, "jpa_query_execution_time_seconds_bucket{le=\"0.5\"}"));

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
    // 간단한 정규식을 사용한 메트릭 값 추출
    String[] lines = prometheusData.split("\n");

    for (String line : lines) {
      if (line.startsWith(metricName) && !line.startsWith(metricName + "_")) {
        String[] parts = line.split(" ");
        if (parts.length >= 2) {
          try {
            return Double.parseDouble(parts[parts.length - 1]);
          } catch (NumberFormatException e) {
            return 0;
          }
        }
      }
    }

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

  public static void addQueryLog(String time, String service, String type, long executionTime, String sql) {
    recentQueryLogs.add(new QueryLog(time, service, type, executionTime, sql));

    // 큐 크기 제한
    while (recentQueryLogs.size() > MAX_LOG_SIZE) {
      recentQueryLogs.poll();
    }
  }

  // 최근 쿼리 로그 가져오기
  @GetMapping("/logs/{service}")
  public ResponseEntity<Map<String, Object>> getQueryLogs(@PathVariable String service) {
    Map<String, Object> result = new HashMap<>();
    List<Map<String, Object>> logs = new ArrayList<>();

    // 선택된 서비스에 해당하는 로그만 필터링
    for (QueryLog log : recentQueryLogs) {
      if ("all".equals(service) || log.service.equals(service)) {
        logs.add(log.toMap());
      }
    }

    result.put("logs", logs);
    return ResponseEntity.ok(result);
  }
}