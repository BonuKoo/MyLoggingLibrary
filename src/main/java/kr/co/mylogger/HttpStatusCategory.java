package kr.co.mylogger;

/**
 * REST API 응답 상태 코드를 사람이 이해하기 쉬운 등급으로 분류한다.
 *
 * 이 분류값이 그대로 로그의 status_category 필드로 전송되며,
 * Kibana 에서 2XX / 3XX / 4XX / 5XX 를 나누어 보는 기준이 된다.
 */
public enum HttpStatusCategory {

    INFORMATIONAL("1XX", "정보"),
    SUCCESS("2XX", "성공"),
    REDIRECTION("3XX", "리다이렉션"),
    CLIENT_ERROR("4XX", "클라이언트 오류"),
    SERVER_ERROR("5XX", "서버 오류"),
    UNKNOWN("UNKNOWN", "알 수 없음");

    private final String statusClass;
    private final String label;

    HttpStatusCategory(String statusClass, String label) {
        this.statusClass = statusClass;
        this.label = label;
    }

    public static HttpStatusCategory from(int statusCode) {
        return switch (statusCode / 100) {
            case 1 -> INFORMATIONAL;
            case 2 -> SUCCESS;
            case 3 -> REDIRECTION;
            case 4 -> CLIENT_ERROR;
            case 5 -> SERVER_ERROR;
            default -> UNKNOWN;
        };
    }

    /** Kibana 에서 집계 기준으로 사용하는 상태 코드 대역. 예) "2XX" */
    public String getStatusClass() {
        return statusClass;
    }

    /** 사람이 읽는 한글 설명. 예) "클라이언트 오류" */
    public String getLabel() {
        return label;
    }

    /** 로그 한 줄의 맨 앞에 붙는 표기. 예) "4XX 클라이언트 오류" */
    public String getDisplayName() {
        return statusClass + " " + label;
    }

    public boolean isError() {
        return this == CLIENT_ERROR || this == SERVER_ERROR || this == UNKNOWN;
    }
}
