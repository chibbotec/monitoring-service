package com.ll.amdinservice.monitoring;

import com.ll.amdinservice.controller.MetricsApiController;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 데이터 소스 프록시를 통해 SQL 쿼리를 모니터링하고 로그를 수집하는 리스너
 */
@Slf4j
@Component
public class P6SpyEventListener implements QueryExecutionListener {

  private static final Pattern SELECT_PATTERN = Pattern.compile("^SELECT", Pattern.CASE_INSENSITIVE);
  private static final Pattern INSERT_PATTERN = Pattern.compile("^INSERT", Pattern.CASE_INSENSITIVE);
  private static final Pattern UPDATE_PATTERN = Pattern.compile("^UPDATE", Pattern.CASE_INSENSITIVE);
  private static final Pattern DELETE_PATTERN = Pattern.compile("^DELETE", Pattern.CASE_INSENSITIVE);

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  @Override
  public void beforeQuery(ExecutionInfo executionInfo, java.util.List<QueryInfo> queryInfoList) {
    // 쿼리 실행 전 처리 (필요시 구현)
  }

  @Override
  public void afterQuery(ExecutionInfo executionInfo, java.util.List<QueryInfo> queryInfoList) {
    // 쿼리 실행 시간을 밀리초 단위로 변환
    long executionTimeMs = executionInfo.getElapsedTime();

    if (queryInfoList.isEmpty()) {
      return;
    }

    QueryInfo queryInfo = queryInfoList.get(0);
    String query = queryInfo.getQuery().trim();

    // 쿼리 타입 감지
    String queryType = determineQueryType(query);

    // 현재 시간을 포맷팅
    String formattedTime = LocalDateTime.now().format(TIME_FORMATTER);

    // 간단한 로깅
    log.debug("SQL: {}, Type: {}, Time: {}ms",
        query.substring(0, Math.min(query.length(), 100)),
        queryType,
        executionTimeMs);

    // 메트릭 컨트롤러에 로그 전송
    MetricsApiController.addQueryLog(
        formattedTime,
        "techinterview", // 서비스 이름 - 실제 환경에서는 동적으로 결정해야 함
        queryType,
        executionTimeMs,
        formatSqlForDisplay(query)
    );
  }

  /**
   * SQL 쿼리의 타입을 결정합니다 (SELECT, INSERT, UPDATE, DELETE 등)
   */
  private String determineQueryType(String query) {
    if (SELECT_PATTERN.matcher(query).find()) {
      return "SELECT";
    } else if (INSERT_PATTERN.matcher(query).find()) {
      return "INSERT";
    } else if (UPDATE_PATTERN.matcher(query).find()) {
      return "UPDATE";
    } else if (DELETE_PATTERN.matcher(query).find()) {
      return "DELETE";
    } else {
      return "OTHER";
    }
  }

  /**
   * 표시를 위해 SQL 쿼리를 포맷팅합니다 (길이 제한, 민감 정보 처리 등)
   */
  private String formatSqlForDisplay(String sql) {
    // SQL 길이가 긴 경우 잘라냄
    if (sql.length() > 200) {
      return sql.substring(0, 200) + "...";
    }
    return sql;
  }
}