package com.ll.amdinservice.config;

import com.ll.amdinservice.monitoring.P6SpyEventListener;
import lombok.RequiredArgsConstructor;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 데이터소스 프록시 설정
 * 모니터링을 위해 기본 데이터소스를 프록시로 래핑합니다.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitoring.sql.enabled", havingValue = "true", matchIfMissing = false)
public class DataSourceProxyConfig {

  private final P6SpyEventListener p6SpyEventListener;

  @Bean
  @Primary
  public DataSource dataSource(DataSourceProperties properties) {
    // 원본 데이터소스 생성
    DataSource originalDataSource = properties.initializeDataSourceBuilder().build();

    // 리스너 체인 생성
    ChainListener chainListener = new ChainListener();
    chainListener.addListener(new DataSourceQueryCountListener());
    chainListener.addListener(p6SpyEventListener);

    // 프록시 데이터소스 생성 및 반환
    return ProxyDataSourceBuilder
        .create(originalDataSource)
        .name("MonitoringDataSource")
        .listener(chainListener)
        .countQuery()
        .multiline()
        .build();
  }
}