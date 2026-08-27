package kr.co.mylogger;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * 의존성만 추가하면 접근 로그가 켜지도록 하는 자동 설정.
 *
 * 끄고 싶으면 {@code api-access-log.enabled=false} 를 주면 된다.
 * 각 빈에 {@code @ConditionalOnMissingBean} 이 붙어 있어, 쓰는 쪽에서 같은 타입의 빈을 직접 정의하면
 * 그쪽이 우선한다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, net.logstash.logback.argument.StructuredArguments.class})
@ConditionalOnProperty(prefix = "api-access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiAccessLogProperties.class)
public class ApiAccessLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BodyMasker apiAccessLogBodyMasker(ApiAccessLogProperties properties) {
        return new BodyMasker(properties.getMasking().getKeys(), properties.getMasking().getReplacement());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiAccessLogWriter apiAccessLogWriter(ApiAccessLogProperties properties) {
        return new ApiAccessLogWriter(properties.getLoggerName());
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerNameWebMvcConfigurer apiAccessLogHandlerNameWebMvcConfigurer() {
        return new HandlerNameWebMvcConfigurer();
    }

    @Bean
    @ConditionalOnMissingBean(name = "apiAccessLogFilterRegistration")
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilterRegistration(
            ApiAccessLogProperties properties,
            ApiAccessLogWriter apiAccessLogWriter,
            BodyMasker bodyMasker
    ) {
        FilterRegistrationBean<ApiAccessLogFilter> registration =
                new FilterRegistrationBean<>(new ApiAccessLogFilter(properties, apiAccessLogWriter, bodyMasker));

        registration.addUrlPatterns("/*");
        registration.setOrder(properties.getOrder());

        return registration;
    }
}
