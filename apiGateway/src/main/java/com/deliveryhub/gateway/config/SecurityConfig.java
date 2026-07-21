package com.deliveryhub.gateway.config;

import com.deliveryhub.gateway.security.IdentityHeadersFilter;
import com.deliveryhub.gateway.security.ProblemResponses;
import com.deliveryhub.gateway.security.RoleFromTokenConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String AUTH_API = "/api/auth/";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveJwtDecoder jwtDecoder,
                                                            JsonMapper jsonMapper) {
        ProblemResponses problems = new ProblemResponses(jsonMapper);
        ServerAuthenticationEntryPoint unauthorized = (exchange, ex) -> problems.write(
                exchange, HttpStatus.UNAUTHORIZED, "Access token is missing, expired or invalid");
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**")
                        .permitAll()
                        .pathMatchers(HttpMethod.GET, "/docs/*/v3/api-docs", "/docs/*/v3/api-docs/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")

                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/cabinet/**").hasRole("RESTAURANT_OWNER")
                        .pathMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
                        .pathMatchers("/api/restaurants/**").hasRole("ADMIN")

                        .pathMatchers("/api/orders/all").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasAnyRole("ADMIN", "COURIER")
                        .pathMatchers(HttpMethod.GET, "/api/deliveries").hasAnyRole("ADMIN", "COURIER")
                        .pathMatchers(HttpMethod.POST, "/api/deliveries/**").hasAnyRole("ADMIN", "COURIER")
                        .pathMatchers(HttpMethod.PATCH, "/api/deliveries/**").hasAnyRole("ADMIN", "COURIER")

                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(bearerTokenOutsideAuthApi())
                        .authenticationEntryPoint(unauthorized)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(new RoleFromTokenConverter())))
                .addFilterAfter(new IdentityHeadersFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((exchange, ex) -> problems.write(
                                exchange, HttpStatus.FORBIDDEN, "Your role is not allowed to do this")))
                .build();
    }

    private static ServerAuthenticationConverter bearerTokenOutsideAuthApi() {
        ServerBearerTokenAuthenticationConverter bearerToken = new ServerBearerTokenAuthenticationConverter();
        return exchange -> exchange.getRequest().getPath().value().startsWith(AUTH_API)
                ? Mono.empty()
                : bearerToken.convert(exchange);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
