package com.ll.amdinservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorizeRequests ->
            authorizeRequests
                .requestMatchers(
                    new AntPathRequestMatcher("/assets/**"),
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/actuator/**"),
                    new AntPathRequestMatcher("/instances/**")
                ).permitAll()
                .anyRequest().authenticated()
        )
        .formLogin(formLogin ->
            formLogin
                .loginPage("/login")
                .permitAll()
        )
        .logout(logout ->
            logout
                .logoutUrl("/logout")
                .permitAll()
        )
        .csrf(csrf ->
            csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/instances"),
                    new AntPathRequestMatcher("/actuator/**")
                )
        );

    return http.build();
  }
}
