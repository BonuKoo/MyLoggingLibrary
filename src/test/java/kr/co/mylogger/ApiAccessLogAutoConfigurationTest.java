package kr.co.mylogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAccessLogAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApiAccessLogAutoConfiguration.class));

    @Test
    @DisplayName("의존성만 추가하면 별도 설정 없이 켜져야 한다.")
    void defaultOnTest() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ApiAccessLogProperties.class);
            assertThat(context).hasSingleBean(ApiAccessLogWriter.class);
            assertThat(context).hasSingleBean(BodyMasker.class);
            assertThat(context).hasSingleBean(HandlerNameWebMvcConfigurer.class);
            assertThat(context).hasBean("apiAccessLogFilterRegistration");
        });
    }

    @Test
    @DisplayName("enabled=false 면 아무 빈도 등록되지 않아야 한다.")
    void disabledTest() {
        contextRunner.withPropertyValues("api-access-log.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ApiAccessLogWriter.class);
            assertThat(context).doesNotHaveBean("apiAccessLogFilterRegistration");
        });
    }

    @Test
    @DisplayName("application.yml 의 설정이 바인딩되어야 한다.")
    void propertyBindingTest() {
        contextRunner.withPropertyValues(
                "api-access-log.logger-name=MY_ACCESS",
                "api-access-log.max-body-length=100",
                "api-access-log.include-request-body=false",
                "api-access-log.order=-50",
                "api-access-log.excluded-path-prefixes=/skip/,/health",
                "api-access-log.request-body-methods=POST",
                "api-access-log.masking.keys=secretCode",
                "api-access-log.masking.replacement=[가림]",
                "api-access-log.masking.excluded-body-path-prefixes=/auth/"
        ).run(context -> {
            ApiAccessLogProperties properties = context.getBean(ApiAccessLogProperties.class);

            assertThat(properties.getLoggerName()).isEqualTo("MY_ACCESS");
            assertThat(properties.getMaxBodyLength()).isEqualTo(100);
            assertThat(properties.isIncludeRequestBody()).isFalse();
            assertThat(properties.getOrder()).isEqualTo(-50);
            assertThat(properties.getExcludedPathPrefixes()).containsExactly("/skip/", "/health");
            assertThat(properties.getRequestBodyMethods()).containsExactly("POST");
            assertThat(properties.getMasking().getKeys()).containsExactly("secretCode");
            assertThat(properties.getMasking().getReplacement()).isEqualTo("[가림]");
            assertThat(properties.getMasking().getExcludedBodyPathPrefixes()).containsExactly("/auth/");
        });
    }

    @Test
    @DisplayName("설정한 order 가 필터 등록에 반영되어야 한다.")
    void filterOrderTest() {
        contextRunner.withPropertyValues("api-access-log.order=-50").run(context ->
                assertThat(context.getBean("apiAccessLogFilterRegistration",
                        org.springframework.boot.web.servlet.FilterRegistrationBean.class).getOrder())
                        .isEqualTo(-50));
    }

    @Test
    @DisplayName("쓰는 쪽이 같은 타입의 빈을 직접 정의하면 그쪽이 우선해야 한다.")
    void userBeanWinsTest() {
        contextRunner.withUserConfiguration(CustomWriterConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ApiAccessLogWriter.class);
            assertThat(context.getBean(ApiAccessLogWriter.class))
                    .isSameAs(context.getBean(CustomWriterConfiguration.class).customWriter);
        });
    }

    @Test
    @DisplayName("마스킹 설정이 BodyMasker 에 실제로 전달되어야 한다.")
    void maskerWiringTest() {
        contextRunner.withPropertyValues(
                "api-access-log.masking.keys=secretCode",
                "api-access-log.masking.replacement=[가림]"
        ).run(context -> {
            BodyMasker bodyMasker = context.getBean(BodyMasker.class);

            assertThat(bodyMasker.mask("{\"secretCode\":\"1234\"}")).isEqualTo("{\"secretCode\":\"[가림]\"}");
            // 기본 키 목록을 덮어썼으므로 password 는 더 이상 가리지 않는다
            assertThat(bodyMasker.mask("{\"password\":\"1234\"}")).isEqualTo("{\"password\":\"1234\"}");
        });
    }

    @Test
    @DisplayName("기본 제외 경로에 액추에이터가 들어 있어야 한다.")
    void defaultExcludedPathTest() {
        contextRunner.run(context -> assertThat(context.getBean(ApiAccessLogProperties.class)
                .getExcludedPathPrefixes())
                .contains("/actuator/", "/favicon.ico"));
    }

    @Test
    @DisplayName("기본 마스킹 키에 비밀번호와 토큰이 들어 있어야 한다.")
    void defaultMaskingKeyTest() {
        contextRunner.run(context -> {
            List<String> keys = context.getBean(ApiAccessLogProperties.class).getMasking().getKeys();

            assertThat(keys).contains("password", "token", "authorization");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomWriterConfiguration {

        private final ApiAccessLogWriter customWriter = new ApiAccessLogWriter("CUSTOM");

        @Bean
        ApiAccessLogWriter apiAccessLogWriter() {
            return customWriter;
        }
    }
}
