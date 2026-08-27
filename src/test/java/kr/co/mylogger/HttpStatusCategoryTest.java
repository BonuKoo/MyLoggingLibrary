package kr.co.mylogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpStatusCategoryTest {

    @ParameterizedTest
    @DisplayName("상태 코드는 대역별로 분류되어야 한다.")
    @CsvSource({
            "100, INFORMATIONAL",
            "200, SUCCESS",
            "201, SUCCESS",
            "299, SUCCESS",
            "301, REDIRECTION",
            "400, CLIENT_ERROR",
            "404, CLIENT_ERROR",
            "500, SERVER_ERROR",
            "503, SERVER_ERROR",
            "600, UNKNOWN",
            "0, UNKNOWN"
    })
    void categoryTest(int statusCode, HttpStatusCategory expected) {
        assertThat(HttpStatusCategory.from(statusCode)).isEqualTo(expected);
    }

    @Test
    @DisplayName("4XX, 5XX 만 오류로 취급되어야 한다.")
    void errorTest() {
        assertThat(HttpStatusCategory.SUCCESS.isError()).isFalse();
        assertThat(HttpStatusCategory.REDIRECTION.isError()).isFalse();
        assertThat(HttpStatusCategory.CLIENT_ERROR.isError()).isTrue();
        assertThat(HttpStatusCategory.SERVER_ERROR.isError()).isTrue();
    }

    @Test
    @DisplayName("표시 이름은 대역과 한글 설명을 함께 보여줘야 한다.")
    void displayNameTest() {
        assertThat(HttpStatusCategory.from(404).getDisplayName()).isEqualTo("4XX 클라이언트 오류");
        assertThat(HttpStatusCategory.from(200).getStatusClass()).isEqualTo("2XX");
    }
}
