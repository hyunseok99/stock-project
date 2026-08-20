package org.team4.sol_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaConsumerConfig {

    // 카프카에 수집되는 Json String을 DTO 객체로 변환
    @Bean
    public RecordMessageConverter jsonMessageConverter(){
        return new JsonMessageConverter();
    }

}
