package com.playdata.orderingservice.common.configs;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WebConfig {

    @Bean // 타 서버로 요청 보낼 수 있게 도와주는 도구
    @LoadBalanced // 유레카에 등록된 서비스명으로 요청할 수 있게 해 주는 어노테이션
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
