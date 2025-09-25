package hs.kr.equus.equusapigateway.proxy.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Slf4j
public class CustomGlobalAuthFilter extends AbstractGatewayFilterFactory<CustomGlobalAuthFilter.Config> {

    private final RedisTemplate<String, String> redisTemplate;

    public CustomGlobalAuthFilter(RedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
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
            System.out.println("Authorization Header: " + authorizationHeader);

            if (authorizationHeader == null) {
                return chain.filter(exchange.mutate().build());
            }

            String token = authorizationHeader.replaceFirst("^Bearer ", "");
            String key = "hs.kr.entrydsm.user.domain.user.adapter.out.domain.UserInfo:" + token;

            Map<Object, Object> userInfoMap = redisTemplate.opsForHash().entries(key);

            System.out.println("userInfo :: " + userInfoMap);

            if (userInfoMap.isEmpty()) {
                return chain.filter(exchange.mutate().build());
            } else {
                String userId = (String) userInfoMap.get("userId");
                String userRole = (String) userInfoMap.get("userRole");

                if (userId.isEmpty() || userId.isBlank() || userRole.isBlank() || userRole.isEmpty()) {
                    return chain.filter(exchange.mutate().build());
                }

                System.out.println("Extracted userId: " + userId);
                System.out.println("Extracted userRole: " + userRole);

                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("Request-User-Id", userId)
                        .header("Request-User-Role", userRole)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        };
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
