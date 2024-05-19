package hs.kr.equus.equusapigateway.proxy.filter;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CustomGlobalAuthFilter extends AbstractGatewayFilterFactory<CustomGlobalAuthFilter.Config> {
    private final RedisReactiveCommands<String, String> redisReactiveCommands;

    public CustomGlobalAuthFilter(RedisReactiveCommands<String, String> redisReactiveCommands) {
        super(Config.class);
        this.redisReactiveCommands = redisReactiveCommands;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            HttpHeaders headers = request.getHeaders();

            if (headers.containsKey("Request-User-Id") || headers.containsKey("Request-User-Role")) {
                return createForbiddenResponse(exchange);
            }

            String authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
            log.debug("Authorization Header: {}", authorizationHeader);

            if (authorizationHeader == null) {
                return mutateRequestWithForbiddenHeaders(exchange, chain);
            }

            String token = authorizationHeader.replaceFirst("^Bearer ", "");
            String key = "hs.kr.equus.user.domain.user.domain.UserInfo:" + token;

            return redisReactiveCommands.hgetall(key)
                    .collectMap(KeyValue::getKey, KeyValue::getValue)
                    .flatMap(userInfoMap -> {
                        log.debug("userInfo :: {}", userInfoMap);

                        if (userInfoMap.isEmpty()) {
                            return mutateRequestWithForbiddenHeaders(exchange, chain);
                        } else {
                            String userId = userInfoMap.getOrDefault("userId", "FORBIDDEN");
                            String userRole = userInfoMap.getOrDefault("userRole", "USER");

                            ServerHttpRequest modifiedRequest = request.mutate()
                                    .header("Request-User-Id", userId)
                                    .header("Request-User-Role", userRole)
                                    .build();

                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error during Redis access", e);
                        return buildErrorResponse(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        };
    }

    private Mono<Void> mutateRequestWithForbiddenHeaders(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("Request-User-Id", "FORBIDDEN")
                .header("Request-User-Role", "USER")
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    private Mono<Void> createForbiddenResponse(ServerWebExchange exchange) {
        return buildErrorResponse(exchange, HttpStatus.FORBIDDEN);
    }

    public static class Config {
    }
}

@Configuration
class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Bean
    public RedisClient redisClient() {
        return RedisClient.create("redis://" + redisHost + ":" + redisPort);
    }

    @Bean
    public RedisReactiveCommands<String, String> redisReactiveCommands(RedisClient redisClient) {
        return redisClient.connect().reactive();
    }
}
