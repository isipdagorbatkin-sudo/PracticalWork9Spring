package com.example.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI ordersOpenApi(@Value("${app.gateway-url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI().info(new Info().title("Food Delivery — заказы").version("1.0")
                        .description("Оформление и просмотр заказов. Все денежные суммы — в рублях. "
                                + "Цены сохраняются на момент оформления. Вызовы выполняются через API Gateway."))
                .addServersItem(new Server().url(gatewayUrl).description("API Gateway"));
    }
}
