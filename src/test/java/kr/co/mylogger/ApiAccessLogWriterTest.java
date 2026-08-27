package kr.co.mylogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAccessLogWriterTest {

    private final ApiAccessLogWriter apiAccessLogWriter = new ApiAccessLogWriter();

    private Logger accessLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        accessLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogWriter.DEFAULT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        accessLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        accessLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("2XX 는 INFO, 4XX 는 WARN, 5XX 는 ERROR 레벨로 기록되어야 한다.")
    void logLevelTest() {
        apiAccessLogWriter.write(accessLogOf(200));
        apiAccessLogWriter.write(accessLogOf(301));
        apiAccessLogWriter.write(accessLogOf(404));
        apiAccessLogWriter.write(accessLogOf(500));

        assertThat(listAppender.list)
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.INFO, Level.INFO, Level.WARN, Level.ERROR);
    }

    @Test
    @DisplayName("로거 이름을 바꿔 지정할 수 있어야 한다.")
    void customLoggerNameTest() {
        Logger customLogger = (Logger) LoggerFactory.getLogger("MY_ACCESS");
        ListAppender<ILoggingEvent> customAppender = new ListAppender<>();
        customAppender.start();
        customLogger.addAppender(customAppender);

        try {
            new ApiAccessLogWriter("MY_ACCESS").write(accessLogOf(200));

            assertThat(customAppender.list).hasSize(1);
            assertThat(listAppender.list).isEmpty();
        } finally {
            customLogger.detachAppender(customAppender);
            customAppender.stop();
        }
    }

    @Test
    @DisplayName("메시지 안의 중괄호가 자리표시자로 오인되어 치환되지 않아야 한다.")
    void messagePlaceholderTest() {
        ApiAccessLog accessLog = ApiAccessLog.builder()
                .requestId("abcd1234")
                .httpMethod("POST")
                .uri("/shortenUrl")
                .httpStatus(200)
                .statusReason("OK")
                .durationMs(23)
                .clientIp("127.0.0.1")
                .handler("ShortenUrlRestController#createShortenUrl")
                .requestBody("{} {\"originalUrl\":\"https://www.hanbit.co.kr/\"}")
                .build();

        apiAccessLogWriter.write(accessLog);

        String message = listAppender.list.get(0).getFormattedMessage();

        assertThat(message)
                .contains("[2XX 성공")
                .contains("POST")
                .contains("-> 200 OK")
                .contains("(   23ms)")
                .contains("handler=ShortenUrlRestController#createShortenUrl")
                .contains("reqId=abcd1234")
                .contains("요청본문={} {\"originalUrl\":\"https://www.hanbit.co.kr/\"}");
    }

    @Test
    @DisplayName("Logstash 로 나가는 JSON 에 분류 필드가 올바른 타입으로 담겨야 한다.")
    void logstashJsonTest() throws Exception {
        apiAccessLogWriter.write(ApiAccessLog.builder()
                .requestId("abcd1234")
                .httpMethod("GET")
                .uri("/shortenUrl/none")
                .httpStatus(404)
                .statusReason("Not Found")
                .durationMs(7)
                .clientIp("127.0.0.1")
                .handler("ShortenUrlRestController#getShortenUrlInformation")
                .responseBody("단축 URL을 찾지 못했습니다.")
                .build());

        JsonNode document = encodeToJson(listAppender.list.get(0));

        assertThat(document.get("http_status").isNumber()).isTrue();
        assertThat(document.get("http_status").asInt()).isEqualTo(404);
        assertThat(document.get("duration_ms").isNumber()).isTrue();
        assertThat(document.get("duration_ms").asLong()).isEqualTo(7L);
        assertThat(document.get("status_category").asText()).isEqualTo("CLIENT_ERROR");
        assertThat(document.get("status_class").asText()).isEqualTo("4XX");
        assertThat(document.get("status_label").asText()).isEqualTo("클라이언트 오류");
        assertThat(document.get("http_method").asText()).isEqualTo("GET");
        assertThat(document.get("uri").asText()).isEqualTo("/shortenUrl/none");
        assertThat(document.get("request_id").asText()).isEqualTo("abcd1234");
        assertThat(document.get("response_body").asText()).isEqualTo("단축 URL을 찾지 못했습니다.");
        assertThat(document.get("level").asText()).isEqualTo("WARN");
        assertThat(document.get("logger_name").asText()).isEqualTo(ApiAccessLogWriter.DEFAULT_LOGGER_NAME);
    }

    @Test
    @DisplayName("2XX 응답에는 응답 본문을 싣지 않아야 한다.")
    void successResponseBodyTest() throws Exception {
        apiAccessLogWriter.write(ApiAccessLog.builder()
                .httpStatus(200)
                .httpMethod("GET")
                .uri("/shortenUrls")
                .responseBody("[{\"originalUrl\":\"https://www.hanbit.co.kr/\"}]")
                .build());

        assertThat(encodeToJson(listAppender.list.get(0)).has("response_body")).isFalse();
    }

    private JsonNode encodeToJson(ILoggingEvent event) throws Exception {
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.start();

        try {
            return new ObjectMapper().readTree(encoder.encode(event));
        } finally {
            encoder.stop();
        }
    }

    private ApiAccessLog accessLogOf(int httpStatus) {
        return ApiAccessLog.builder()
                .httpStatus(httpStatus)
                .httpMethod("GET")
                .uri("/shortenUrls")
                .build();
    }
}
