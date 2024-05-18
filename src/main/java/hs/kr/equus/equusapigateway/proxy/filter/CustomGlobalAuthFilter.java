package hs.kr.equus.equusapigateway.proxy.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;

@Component
@Slf4j
public class CustomGlobalAuthFilter extends AbstractGatewayFilterFactory<CustomGlobalAuthFilter.Config> {
    private final JedisPool jedisPool;
    public CustomGlobalAuthFilter() {
        super(Config.class);
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        this.jedisPool = new JedisPool(jedisPoolConfig, "equus-redis.h35um9.0001.apn2.cache.amazonaws.com", 6379);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request;
            HttpHeaders headers = exchange.getRequest().getHeaders();
            if(headers.containsKey("Request-User-Id") || headers.containsKey("Request-User-Role")) {
                return createForbiddenResponse(exchange);
            }
            String authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
            log.info("Authorization Header: {}", authorizationHeader);

            if(authorizationHeader == null){
                request = exchange.getRequest().mutate()
                        .header("Request-User-Id", "FORBIDDEN")
                        .header("Request-User-Role", "USER")
                        .build();
                exchange.mutate().request(request).build();
                return chain.filter(exchange);
            }
            else {
                try (Jedis jedis = jedisPool.getResource()) {
                    String key = "hs.kr.equus.user.domain.user.domain.UserInfo:" + authorizationHeader;
                    Map<String, String> userInfoMap = jedis.hgetAll(key);
                    log.info("userInfo :: {}", userInfoMap);

                    if (userInfoMap == null || userInfoMap.isEmpty()) {
                        request = exchange.getRequest().mutate()
                                .header("Request-User-Id", "FORBIDDEN")
                                .header("Request-User-Role", "USER")
                                .build();
                    } else {
                        String userId = userInfoMap.get("userId");
                        String userRole = userInfoMap.get("userRole");

                        request = exchange.getRequest().mutate()
                                .header("Request-User-Id", userId)
                                .header("Request-User-Role", userRole)
                                .build();
                    }
                    exchange.mutate().request(request).build();
                } catch (Exception e) {
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    System.out.println("error: " + e.getMessage());
                    return response.setComplete();
                }
            }
            return chain.filter(exchange);
        });
    }

    private Mono<Void> createForbiddenResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    public static class Config{

    }
}
