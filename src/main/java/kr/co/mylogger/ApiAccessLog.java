package kr.co.mylogger;

/**
 * REST API 요청 한 건의 처리 결과를 담는 값 객체.
 *
 * 같은 데이터를 두 가지 형태로 내보낸다.
 * - toReadableMessage() : 콘솔/파일에 남는, 사람이 읽는 한 줄
 * - ApiAccessLogWriter  : Logstash 로 보내는, 기계가 집계하는 JSON 필드
 */
public record ApiAccessLog(
        String requestId,
        String httpMethod,
        String uri,
        int httpStatus,
        String statusReason,
        HttpStatusCategory category,
        long durationMs,
        String clientIp,
        String handler,
        String requestBody,
        String responseBody,
        String errorMessage
) {

    // 가장 긴 표기인 "4XX 클라이언트 오류"(표시 폭 19)에 맞춰 세로줄을 정렬한다.
    private static final int CATEGORY_WIDTH = 19;
    private static final int URI_WIDTH = 34;
    private static final int REASON_WIDTH = 22;

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 사람이 읽기 좋도록 폭을 맞춘 한 줄을 만든다.
     *
     * [2XX 성공       ] POST   /shortenUrl                        -> 200 OK                 (   23ms) | handler=... | client=... | reqId=...
     */
    public String toReadableMessage() {
        StringBuilder message = new StringBuilder();

        message.append('[')
                .append(padRight(category.getDisplayName(), CATEGORY_WIDTH))
                .append("] ")
                .append(padRight(httpMethod, 6))
                .append(' ')
                .append(padRight(uri, URI_WIDTH))
                .append(" -> ")
                .append(httpStatus)
                .append(' ')
                .append(padRight(nullToDash(statusReason), REASON_WIDTH))
                .append(String.format(" (%5dms)", durationMs))
                .append(" | handler=").append(nullToDash(handler))
                .append(" | client=").append(nullToDash(clientIp))
                .append(" | reqId=").append(nullToDash(requestId));

        if (hasText(requestBody)) {
            message.append(" | 요청본문=").append(requestBody);
        }
        if (hasText(errorMessage)) {
            message.append(" | 예외=").append(errorMessage);
        }
        if (category.isError() && hasText(responseBody)) {
            message.append(" | 응답본문=").append(responseBody);
        }

        return message.toString();
    }

    private static boolean hasText(String value) {
        return null != value && !value.isBlank();
    }

    private static String nullToDash(String value) {
        return hasText(value) ? value : "-";
    }

    /**
     * 한글은 터미널에서 두 칸을 차지하므로, 글자 수가 아니라 표시 폭을 기준으로 여백을 채운다.
     * 이렇게 해야 콘솔에서 세로줄이 실제로 맞는다.
     */
    private static String padRight(String value, int width) {
        String text = nullToDash(value);
        int padding = width - displayWidth(text);

        if (padding <= 0) {
            return text;
        }

        return text + " ".repeat(padding);
    }

    private static int displayWidth(String text) {
        int width = 0;

        for (int index = 0; index < text.length(); index++) {
            width += isWideCharacter(text.charAt(index)) ? 2 : 1;
        }

        return width;
    }

    private static boolean isWideCharacter(char character) {
        return (character >= 0x1100 && character <= 0x115F)     // 한글 자모
                || (character >= 0x2E80 && character <= 0xA4CF)  // CJK 부수, 한중일 통합 한자
                || (character >= 0xAC00 && character <= 0xD7A3)  // 한글 음절
                || (character >= 0xF900 && character <= 0xFAFF)  // CJK 호환 한자
                || (character >= 0xFF00 && character <= 0xFF60); // 전각 문자
    }

    public static class Builder {
        private String requestId;
        private String httpMethod;
        private String uri;
        private int httpStatus;
        private String statusReason;
        private long durationMs;
        private String clientIp;
        private String handler;
        private String requestBody;
        private String responseBody;
        private String errorMessage;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder statusReason(String statusReason) {
            this.statusReason = statusReason;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder handler(String handler) {
            this.handler = handler;
            return this;
        }

        public Builder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ApiAccessLog build() {
            return new ApiAccessLog(
                    requestId,
                    httpMethod,
                    uri,
                    httpStatus,
                    statusReason,
                    HttpStatusCategory.from(httpStatus),
                    durationMs,
                    clientIp,
                    handler,
                    requestBody,
                    responseBody,
                    errorMessage
            );
        }
    }
}
