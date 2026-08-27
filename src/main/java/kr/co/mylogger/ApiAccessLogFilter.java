package kr.co.mylogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 모든 REST API 요청/응답을 가로채서 상태 코드 분류별로 로그를 남긴다.
 *
 * 요청 하나가 로그 한 줄이 되고, 그 한 줄이 곧 Elasticsearch 문서 하나가 된다.
 * MDC 에 심어둔 request_id 덕분에 같은 요청에서 나온 서비스 계층 로그까지 한 번에 묶어볼 수 있다.
 */
public class ApiAccessLogFilter extends OncePerRequestFilter {

    /** MDC 키. logback 패턴의 %X{request_id} 로 애플리케이션 로그에도 같이 찍힌다. */
    public static final String REQUEST_ID_MDC_KEY = "request_id";

    /** 응답 헤더로도 돌려줘서, 브라우저에서 본 응답과 로그를 바로 맞춰볼 수 있게 한다. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ApiAccessLogProperties properties;
    private final ApiAccessLogWriter apiAccessLogWriter;
    private final BodyMasker bodyMasker;
    private final Set<String> requestBodyMethods;

    public ApiAccessLogFilter(
            ApiAccessLogProperties properties,
            ApiAccessLogWriter apiAccessLogWriter,
            BodyMasker bodyMasker
    ) {
        this.properties = properties;
        this.apiAccessLogWriter = apiAccessLogWriter;
        this.bodyMasker = bodyMasker;
        this.requestBodyMethods = properties.getRequestBodyMethods()
                .stream()
                .map(method -> method.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return startsWithAny(request.getRequestURI(), properties.getExcludedPathPrefixes());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        long startedAt = System.nanoTime();
        String errorMessage = null;

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } catch (Exception exception) {
            errorMessage = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

            // copyBodyToResponse() 는 캐시 버퍼를 비우므로, 응답 본문은 반드시 그 전에 읽어둔다.
            String responseBody = readResponseBody(cachedRequest, cachedResponse);

            try {
                cachedResponse.copyBodyToResponse();
            } finally {
                writeAccessLog(cachedRequest, cachedResponse, requestId, durationMs, responseBody, errorMessage);
                MDC.remove(REQUEST_ID_MDC_KEY);
            }
        }
    }

    private void writeAccessLog(
            CachedBodyHttpServletRequest request,
            ContentCachingResponseWrapper response,
            String requestId,
            long durationMs,
            String responseBody,
            String errorMessage
    ) {
        int httpStatus = resolveStatus(response, errorMessage);

        ApiAccessLog accessLog = ApiAccessLog.builder()
                .requestId(requestId)
                .httpMethod(request.getMethod())
                .uri(fullUri(request))
                .httpStatus(httpStatus)
                .statusReason(reasonPhraseOf(httpStatus))
                .durationMs(durationMs)
                .clientIp(clientIpOf(request))
                .handler((String) request.getAttribute(HandlerNameInterceptor.HANDLER_ATTRIBUTE))
                .requestBody(readRequestBody(request))
                .responseBody(responseBody)
                .errorMessage(errorMessage)
                .build();

        apiAccessLogWriter.write(accessLog);
    }

    /**
     * 필터 밖으로 예외가 튀어나간 경우 컨테이너가 아직 상태 코드를 바꾸기 전이므로,
     * 응답이 성공으로 남아 있더라도 서버 오류로 기록한다.
     */
    private int resolveStatus(ContentCachingResponseWrapper response, String errorMessage) {
        int httpStatus = response.getStatus();

        if (null != errorMessage && httpStatus < 400) {
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }

        return httpStatus;
    }

    private String reasonPhraseOf(int httpStatus) {
        HttpStatus resolved = HttpStatus.resolve(httpStatus);

        return null == resolved ? null : resolved.getReasonPhrase();
    }

    private String fullUri(HttpServletRequest request) {
        String queryString = request.getQueryString();

        if (null == queryString || queryString.isBlank()) {
            return request.getRequestURI();
        }

        return request.getRequestURI() + "?" + queryString;
    }

    private String clientIpOf(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (null != forwardedFor && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String readRequestBody(CachedBodyHttpServletRequest request) {
        if (!properties.isIncludeRequestBody()) {
            return null;
        }

        if (!requestBodyMethods.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return null;
        }

        if (isBodyExcluded(request)) {
            return null;
        }

        // CachedBodyHttpServletRequest 는 읽을 때마다 새 스트림을 만들어주므로
        // 컨트롤러가 이미 읽은 뒤에도 본문을 다시 꺼낼 수 있다.
        String body = request.getReader().lines().reduce("", String::concat);

        return maskAndTruncate(body);
    }

    private String readResponseBody(HttpServletRequest request, ContentCachingResponseWrapper response) {
        if (!properties.isIncludeResponseBodyOnError()) {
            return null;
        }

        if (isBodyExcluded(request)) {
            return null;
        }

        byte[] content = response.getContentAsByteArray();

        if (0 == content.length) {
            return null;
        }

        return maskAndTruncate(new String(content, charsetOf(response.getCharacterEncoding())));
    }

    /** 로그인/결제처럼 본문을 통째로 남기면 안 되는 경로인지 판단한다. */
    private boolean isBodyExcluded(HttpServletRequest request) {
        return startsWithAny(request.getRequestURI(), properties.getMasking().getExcludedBodyPathPrefixes());
    }

    private boolean startsWithAny(String uri, List<String> prefixes) {
        if (null == prefixes || prefixes.isEmpty()) {
            return false;
        }

        return prefixes.stream().anyMatch(prefix -> null != prefix && uri.startsWith(prefix));
    }

    private String maskAndTruncate(String body) {
        if (null == body || body.isBlank()) {
            return null;
        }

        String singleLine = body.replaceAll("\\s+", " ").trim();

        return truncate(bodyMasker.mask(singleLine));
    }

    private Charset charsetOf(String characterEncoding) {
        if (null == characterEncoding || characterEncoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(characterEncoding);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private String truncate(String value) {
        if (null == value || value.isBlank()) {
            return null;
        }

        int maxBodyLength = properties.getMaxBodyLength();

        if (maxBodyLength <= 0 || value.length() <= maxBodyLength) {
            return value;
        }

        return value.substring(0, maxBodyLength) + "...(생략)";
    }
}
