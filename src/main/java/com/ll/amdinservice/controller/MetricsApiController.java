package com.ll.amdinservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsApiController {

  private final RestTemplate restTemplate;

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
        url = "http://localhost:8081/actuator/prometheus";
        break;
      case "apigateway":
        url = "http://localhost:8080/actuator/prometheus";
        break;
      default:
        throw new IllegalArgumentException("지원하지 않는 서비스: " + serviceName);
    }

    // Prometheus 엔드포인트에서 메트릭 데이터 가져오기
    String prometheusData = restTemplate.getForObject(url, String.class);

    // Prometheus 형식 데이터 파싱
    return parsePrometheusData(prometheusData, serviceName);
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

  // 최근 쿼리 로그 가져오기 (예시, 실제 구현은 로그 수집 시스템과 연동 필요)
  @GetMapping("/logs/{service}")
  public ResponseEntity<Map<String, Object>> getQueryLogs(@PathVariable String service) {
    Map<String, Object> result = new HashMap<>();

    // 여기서는 가상의 로그 데이터 반환 (실제로는 로그 수집 시스템에서 가져와야 함)
    result.put("logs", "서비스별 로그 수집은 별도의 로그 수집 시스템(ELK, Loki 등)과 연동 필요");

    return ResponseEntity.ok(result);
  }
}