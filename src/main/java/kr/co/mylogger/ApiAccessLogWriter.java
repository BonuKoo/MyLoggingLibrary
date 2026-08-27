package kr.co.mylogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

/**
 * 분류된 API 접근 로그를 실제로 기록하는 곳.
 *
 * 1) 메시지 본문에는 사람이 읽는 한 줄을 넣고,
 * 2) StructuredArguments 로 붙인 값들은 LogstashEncoder 가 JSON 최상위 필드로 승격시킨다.
 *    (MDC 와 달리 숫자는 숫자 타입 그대로 나가므로 Kibana 에서 평균/합계 집계가 가능하다.)
 *
 * 로그 레벨도 상태 코드 분류에 맞춰 나눈다.
 * - 2XX / 3XX : INFO
 * - 4XX       : WARN
 * - 5XX       : ERROR
 */
public class ApiAccessLogWriter {

    /** 로거 이름 기본값. logback 설정에서 이 이름으로 전용 Appender 를 붙인다. */
    public static final String DEFAULT_LOGGER_NAME = "API_ACCESS";

    private final Logger log;

    public ApiAccessLogWriter() {
        this(DEFAULT_LOGGER_NAME);
    }

    public ApiAccessLogWriter(String loggerName) {
        this.log = LoggerFactory.getLogger(
                null == loggerName || loggerName.isBlank() ? DEFAULT_LOGGER_NAME : loggerName);
    }

    public void write(ApiAccessLog accessLog) {
        Object[] arguments = toLogArguments(accessLog);

        switch (accessLog.category()) {
            case CLIENT_ERROR -> log.warn("{}", arguments);
            case SERVER_ERROR, UNKNOWN -> log.error("{}", arguments);
            default -> log.info("{}", arguments);
        }
    }

    private Object[] toLogArguments(ApiAccessLog accessLog) {
        List<Object> arguments = new ArrayList<>();

        // 첫 번째 인자는 "{}" 자리에 들어가는 사람이 읽는 메시지다.
        // 요청 본문에 중괄호가 섞여 있어도 치환 사고가 나지 않도록 메시지 전체를 인자로 넘긴다.
        arguments.add(accessLog.toReadableMessage());

        arguments.add(keyValue("event", "api_access"));
        arguments.add(keyValue("http_status", accessLog.httpStatus()));
        arguments.add(keyValue("status_category", accessLog.category().name()));
        arguments.add(keyValue("status_class", accessLog.category().getStatusClass()));
        arguments.add(keyValue("status_label", accessLog.category().getLabel()));
        arguments.add(keyValue("duration_ms", accessLog.durationMs()));

        addIfPresent(arguments, "request_id", accessLog.requestId());
        addIfPresent(arguments, "http_method", accessLog.httpMethod());
        addIfPresent(arguments, "uri", accessLog.uri());
        addIfPresent(arguments, "status_reason", accessLog.statusReason());
        addIfPresent(arguments, "client_ip", accessLog.clientIp());
        addIfPresent(arguments, "handler", accessLog.handler());
        addIfPresent(arguments, "request_body", accessLog.requestBody());
        addIfPresent(arguments, "error_message", accessLog.errorMessage());

        if (accessLog.category().isError()) {
            addIfPresent(arguments, "response_body", accessLog.responseBody());
        }

        return arguments.toArray();
    }

    private void addIfPresent(List<Object> arguments, String fieldName, String value) {
        if (null != value && !value.isBlank()) {
            arguments.add(keyValue(fieldName, value));
        }
    }
}
