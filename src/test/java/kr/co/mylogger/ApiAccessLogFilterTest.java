package kr.co.mylogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiAccessLogFilterTest {

    private ApiAccessLogProperties properties;
    private ApiAccessLogFilter filter;

    private Logger accessLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        properties = new ApiAccessLogProperties();

        accessLogger = (Logger) LoggerFactory.getLogger(properties.getLoggerName());
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
    @DisplayName("응답 상태 코드에 따라 분류와 로그 레벨이 정해져야 한다.")
    void classificationTest() throws Exception {
        callWithStatus("GET", "/ok", 200, null);
        callWithStatus("GET", "/moved", 301, null);
        callWithStatus("GET", "/missing", 404, "없습니다");
        callWithStatus("GET", "/broken", 500, "서버 오류");

        assertThat(listAppender.list)
                .extracting(ILoggingEvent::getLevel)
                .containsExactly(Level.INFO, Level.INFO, Level.WARN, Level.ERROR);

        assertThat(listAppender.list.get(0).getFormattedMessage()).contains("[2XX 성공").contains("-> 200 OK");
        assertThat(listAppender.list.get(1).getFormattedMessage()).contains("[3XX 리다이렉션");
        assertThat(listAppender.list.get(2).getFormattedMessage())
                .contains("[4XX 클라이언트 오류")
                .contains("응답본문=없습니다");
        assertThat(listAppender.list.get(3).getFormattedMessage()).contains("[5XX 서버 오류");
    }

    @Test
    @DisplayName("요청 본문의 민감한 값은 가려진 채로 기록되어야 한다.")
    void maskingTest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        request.setContentType("application/json");
        request.setContent("{\"userId\":\"kim\",\"password\":\"1q2w3e4r\"}".getBytes(StandardCharsets.UTF_8));

        newFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String message = listAppender.list.get(0).getFormattedMessage();

        assertThat(message).contains("요청본문={\"userId\":\"kim\",\"password\":\"***\"}");
        assertThat(message).doesNotContain("1q2w3e4r");
    }

    @Test
    @DisplayName("본문 제외 경로는 본문을 아예 기록하지 않아야 한다.")
    void bodyExcludedPathTest() throws Exception {
        properties.getMasking().setExcludedBodyPathPrefixes(List.of("/auth/"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContent("{\"credential\":\"어떤키인지모름\"}".getBytes(StandardCharsets.UTF_8));

        newFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        String message = listAppender.list.get(0).getFormattedMessage();

        assertThat(message).contains("/auth/login");
        assertThat(message).doesNotContain("요청본문");
        assertThat(message).doesNotContain("어떤키인지모름");
    }

    @Test
    @DisplayName("제외 경로는 로그를 남기지 않아야 한다.")
    void excludedPathTest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        newFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    @DisplayName("설정한 길이를 넘는 본문은 잘려야 한다.")
    void truncateTest() throws Exception {
        properties.setMaxBodyLength(10);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        request.setContent("0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8));

        newFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(listAppender.list.get(0).getFormattedMessage()).contains("요청본문=0123456789...(생략)");
    }

    @Test
    @DisplayName("필터 밖으로 예외가 나가면 500 서버 오류로 기록하고 예외는 그대로 던져야 한다.")
    void exceptionTest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain explodingChain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("의도적으로 터뜨림");
        };

        assertThatThrownBy(() -> newFilter().doFilter(request, response, explodingChain))
                .isInstanceOf(IllegalStateException.class);

        ILoggingEvent event = listAppender.list.get(0);

        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("[5XX 서버 오류")
                .contains("-> 500 Internal Server Error")
                .contains("예외=IllegalStateException: 의도적으로 터뜨림");
    }

    @Test
    @DisplayName("추적용 request_id 를 응답 헤더로 돌려주고 같은 값을 로그에 남겨야 한다.")
    void requestIdTest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(new MockHttpServletRequest("GET", "/ok"), response, new MockFilterChain());

        String requestId = response.getHeader(ApiAccessLogFilter.REQUEST_ID_HEADER);

        assertThat(requestId).isNotBlank();
        assertThat(listAppender.list.get(0).getFormattedMessage()).contains("reqId=" + requestId);
    }

    @Test
    @DisplayName("쿼리스트링이 있으면 경로와 함께 기록되어야 한다.")
    void queryStringTest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.setQueryString("keyword=logback&page=2");

        newFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(listAppender.list.get(0).getFormattedMessage()).contains("/search?keyword=logback&page=2");
    }

    private void callWithStatus(String method, String uri, int status, String responseBody) throws Exception {
        FilterChain chain = (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(status);

            if (null != responseBody) {
                httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
                httpResponse.getWriter().write(responseBody);
            }
        };

        newFilter().doFilter(new MockHttpServletRequest(method, uri), new MockHttpServletResponse(), chain);
    }

    /** OncePerRequestFilter 는 요청당 한 번만 도므로, 호출마다 새로 만든다. */
    private ApiAccessLogFilter newFilter() {
        filter = new ApiAccessLogFilter(
                properties,
                new ApiAccessLogWriter(properties.getLoggerName()),
                new BodyMasker(properties.getMasking().getKeys(), properties.getMasking().getReplacement()));

        return filter;
    }
}
