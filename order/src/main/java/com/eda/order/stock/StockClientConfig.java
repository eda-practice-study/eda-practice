package com.eda.order.stock;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(StockClientProperties.class)
public class StockClientConfig {

    @Bean
    public RestClient stockRestClient(StockClientProperties properties) {
        return restClient(properties);
    }

    // 요청 팩토리를 JDK HttpClient 로 고정한다. 타임아웃 예외의 모양이 팩토리마다 달라 StockClient 의 분류가 여기에 기대기 때문이다.
    static RestClient restClient(StockClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }
}
