package kr.co.mylogger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

/**
 * application.yml 의 {@code api-access-log.*} 설정.
 *
 * <pre>
 * api-access-log:
 *   enabled: true
 *   logger-name: API_ACCESS
 *   excluded-path-prefixes: [/actuator/, /favicon.ico]
 *   max-body-length: 500
 *   masking:
 *     keys: [password, token]
 *     excluded-body-path-prefixes: [/auth/]
 * </pre>
 */
@ConfigurationProperties(prefix = "api-access-log")
public class ApiAccessLogProperties {

    /** 접근 로그 기능 자체를 켜고 끈다. */
    private boolean enabled = true;

    /** 로그를 남길 로거 이름. logback 설정에서 이 이름으로 Appender 를 붙인다. */
    private String loggerName = "API_ACCESS";

    /** 필터 순서. 낮을수록 앞에 놓여 더 많은 구간을 측정한다. */
    private int order = Ordered.HIGHEST_PRECEDENCE + 10;

    /** 아예 로그를 남기지 않을 경로 접두사. 정적 리소스나 헬스 체크용. */
    private List<String> excludedPathPrefixes = new ArrayList<>(List.of("/actuator/", "/favicon.ico"));

    /** 요청/응답 본문을 기록할 최대 길이. 넘으면 잘라낸다. */
    private int maxBodyLength = 500;

    /** 요청 본문 기록 여부. */
    private boolean includeRequestBody = true;

    /** 오류(4XX/5XX) 응답의 본문 기록 여부. 성공 응답 본문은 어차피 기록하지 않는다. */
    private boolean includeResponseBodyOnError = true;

    /** 요청 본문을 기록할 HTTP 메서드. */
    private List<String> requestBodyMethods = new ArrayList<>(List.of("POST", "PUT", "PATCH"));

    private final Masking masking = new Masking();

    public static class Masking {

        /**
         * 값을 가려야 할 키 이름. 대소문자를 가리지 않는다.
         * JSON 본문의 {@code "password":"..."} 과 폼/쿼리의 {@code password=...} 를 모두 처리한다.
         */
        private List<String> keys = new ArrayList<>(List.of(
                "password", "passwd", "pwd",
                "token", "accessToken", "access_token", "refreshToken", "refresh_token",
                "authorization", "secret", "apiKey", "api_key",
                "creditCard", "credit_card", "cardNumber", "card_number", "cvc", "ssn"
        ));

        /** 가려진 자리에 넣을 문자열. */
        private String replacement = "***";

        /**
         * 본문을 아예 기록하지 않을 경로 접두사.
         * 키 이름을 예측할 수 없는 로그인/결제 API 는 여기에 넣는 편이 안전하다.
         */
        private List<String> excludedBodyPathPrefixes = new ArrayList<>();

        public List<String> getKeys() {
            return keys;
        }

        public void setKeys(List<String> keys) {
            this.keys = keys;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public List<String> getExcludedBodyPathPrefixes() {
            return excludedBodyPathPrefixes;
        }

        public void setExcludedBodyPathPrefixes(List<String> excludedBodyPathPrefixes) {
            this.excludedBodyPathPrefixes = excludedBodyPathPrefixes;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    public boolean isIncludeResponseBodyOnError() {
        return includeResponseBodyOnError;
    }

    public void setIncludeResponseBodyOnError(boolean includeResponseBodyOnError) {
        this.includeResponseBodyOnError = includeResponseBodyOnError;
    }

    public List<String> getRequestBodyMethods() {
        return requestBodyMethods;
    }

    public void setRequestBodyMethods(List<String> requestBodyMethods) {
        this.requestBodyMethods = requestBodyMethods;
    }

    public Masking getMasking() {
        return masking;
    }
}
