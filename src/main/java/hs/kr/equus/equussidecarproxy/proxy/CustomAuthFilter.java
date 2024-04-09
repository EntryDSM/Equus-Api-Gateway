package hs.kr.equus.equussidecarproxy.proxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class CustomAuthFilter implements GatewayFilter {

    private final JedisPool jedisPool;

    public CustomAuthFilter() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        this.jedisPool = new JedisPool(jedisPoolConfig, "equus-redis.h35um9.0001.apn2.cache.amazonaws.com", 6379);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            log.info("Missing Authorization header");
            return createForbiddenResponse(exchange);
        }

        log.info("Authorization Header: {}", authorizationHeader);

        try (Jedis jedis = jedisPool.getResource()) {
            String key = "hs.kr.equus.user.domain.user.domain.UserInfo:" + authorizationHeader;
            Map<String, String> userInfoMap = jedis.hgetAll(key);
            log.info("userInfo :: {}", userInfoMap);
            if (userInfoMap == null || userInfoMap.isEmpty()) {
                return createForbiddenResponse(exchange);
            }

            String userId = userInfoMap.get("userId");
            String userRole = userInfoMap.get("userRole");

            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("Request-User-Id", userId)
                    .header("Request-User-Role", userRole)
                    .build();

            exchange.mutate().request(request).build();
        } catch (Exception e) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            System.out.println("error: " + e.getMessage());
            return response.setComplete();
        }

        return chain.filter(exchange);
    }

    private Mono<Void> createForbiddenResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

}
