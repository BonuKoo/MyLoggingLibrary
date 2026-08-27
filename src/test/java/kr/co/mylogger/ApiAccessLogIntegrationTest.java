package kr.co.mylogger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스타터를 실제 스프링 부트 웹 애플리케이션에 얹었을 때의 동작을 확인한다.
 *
 * 컴포넌트 스캔 없이 @EnableAutoConfiguration 만 켜므로,
 * META-INF/spring/...AutoConfiguration.imports 등록이 제대로 되어 있어야만 통과한다.
 */
@SpringBootTest(classes = ApiAccessLogIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class ApiAccessLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("자동 설정만으로 접근 로그가 남고 응답은 그대로 전달되어야 한다.")
    void autoConfiguredTest() throws Exception {
        mockMvc.perform(get("/ok"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        ILoggingEvent event = lastEvent();

        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("[2XX 성공")
                .contains("GET")
                .contains("/ok")
                .contains("-> 200 OK");
    }

    @Test
    @DisplayName("응답을 만든 컨트롤러 메서드 이름이 기록되어야 한다.")
    void handlerNameTest() throws Exception {
        mockMvc.perform(get("/ok")).andExpect(status().isOk());

        assertThat(lastEvent().getFormattedMessage()).contains("handler=TestController#ok");
    }

    @Test
    @DisplayName("4XX 응답은 WARN 으로 분류되고 응답 본문이 함께 남아야 한다.")
    void clientErrorTest() throws Exception {
        mockMvc.perform(get("/missing")).andExpect(status().isNotFound());

        ILoggingEvent event = lastEvent();

        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("[4XX 클라이언트 오류")
                .contains("-> 404 Not Found")
                .contains("응답본문=찾을 수 없습니다");
    }

    @Test
    @DisplayName("요청 본문의 비밀번호가 가려진 채로 기록되어야 한다.")
    void maskingTest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"kim\",\"password\":\"1q2w3e4r\"}"))
                .andExpect(status().isOk());

        String message = lastEvent().getFormattedMessage();

        assertThat(message).contains("\"password\":\"***\"");
        assertThat(message).doesNotContain("1q2w3e4r");
    }

    @Test
    @DisplayName("기본 제외 경로는 로그를 남기지 않아야 한다.")
    void excludedPathTest() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    @DisplayName("응답 헤더로 돌려준 request_id 가 로그의 값과 같아야 한다.")
    void requestIdTest() throws Exception {
        MvcResult result = mockMvc.perform(get("/ok")).andExpect(status().isOk()).andReturn();

        String requestId = result.getResponse().getHeader(ApiAccessLogFilter.REQUEST_ID_HEADER);

        assertThat(requestId).isNotBlank();
        assertThat(lastEvent().getFormattedMessage()).contains("reqId=" + requestId);
    }

    private ILoggingEvent lastEvent() {
        assertThat(listAppender.list).isNotEmpty();

        return listAppender.list.get(listAppender.list.size() - 1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/missing")
        ResponseEntity<String> missing() {
            return ResponseEntity.status(404).body("찾을 수 없습니다");
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @PostMapping("/users")
        String createUser(@RequestBody String body) {
            return "created";
        }
    }
}
