package hs.kr.equus.equussidecarproxy.proxy;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

    private final CustomAuthFilter customAuthFilter = new CustomAuthFilter();

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route("client-portal", r -> r.path("/")
                        .and().method("POST",  "PUT", "DELETE", "GET")
                        .uri("http://localhost:8080"))
                .route("client-portal", r -> r.path("/user")
                        .and().method("POST")
                        .uri("http://localhost:8080"))
                .route("client-portal", r -> r.path("/user/verify/popup")
                        .and().method("GET")
                        .uri("http://localhost:8080"))
                .route("client-portal", r -> r.path("/user/auth")
                        .and().method("POST", "PUT")
                        .uri("http://localhost:8080"))
                .route("client-portal", r -> r.path("/admin/auth")
                        .and().method("POST")
                        .uri("http://localhost:8080"))
                .route("client-portal", r -> r.path("/**")
                        .and().method("POST",  "PUT", "DELETE", "GET")
                        .filters(f -> f.filter(customAuthFilter))
                        .uri("http://localhost:8080"))
                .build();

    }
}
