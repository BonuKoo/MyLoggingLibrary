package kr.co.mylogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BodyMaskerTest {

    private final BodyMasker bodyMasker = new BodyMasker(List.of("password", "token", "cvc"), "***");

    @Test
    @DisplayName("JSON 본문의 민감한 값이 가려져야 한다.")
    void jsonTest() {
        String masked = bodyMasker.mask("{\"userId\":\"kim\",\"password\":\"1q2w3e4r!\"}");

        assertThat(masked).isEqualTo("{\"userId\":\"kim\",\"password\":\"***\"}");
    }

    @Test
    @DisplayName("가려야 할 키가 아닌 값은 그대로 남아야 한다.")
    void untouchedTest() {
        String masked = bodyMasker.mask("{\"originalUrl\":\"https://www.hanbit.co.kr/\"}");

        assertThat(masked).isEqualTo("{\"originalUrl\":\"https://www.hanbit.co.kr/\"}");
    }

    @Test
    @DisplayName("키 이름의 대소문자가 달라도 가려져야 한다.")
    void caseInsensitiveTest() {
        assertThat(bodyMasker.mask("{\"Password\":\"secret\"}")).isEqualTo("{\"Password\":\"***\"}");
        assertThat(bodyMasker.mask("{\"TOKEN\":\"abc.def\"}")).isEqualTo("{\"TOKEN\":\"***\"}");
    }

    @Test
    @DisplayName("문자열이 아닌 값도 가려져야 한다.")
    void nonStringValueTest() {
        assertThat(bodyMasker.mask("{\"cvc\":123}")).isEqualTo("{\"cvc\":\"***\"}");
    }

    @Test
    @DisplayName("등록한 키를 부분 문자열로 포함하는 다른 키는 건드리지 않아야 한다.")
    void partialKeyTest() {
        String masked = bodyMasker.mask("{\"password_hint\":\"강아지 이름\",\"password\":\"1234\"}");

        assertThat(masked).isEqualTo("{\"password_hint\":\"강아지 이름\",\"password\":\"***\"}");
    }

    @Test
    @DisplayName("폼/쿼리스트링 형식의 값도 가려져야 한다.")
    void formTest() {
        String masked = bodyMasker.mask("userId=kim&password=1q2w3e4r&remember=true");

        assertThat(masked).isEqualTo("userId=kim&password=***&remember=true");
    }

    @Test
    @DisplayName("여러 키가 섞여 있어도 모두 가려져야 한다.")
    void multipleKeysTest() {
        String masked = bodyMasker.mask("{\"password\":\"a\",\"token\":\"b\",\"userId\":\"kim\"}");

        assertThat(masked).isEqualTo("{\"password\":\"***\",\"token\":\"***\",\"userId\":\"kim\"}");
    }

    @Test
    @DisplayName("가릴 키를 지정하지 않으면 본문을 그대로 둔다.")
    void emptyKeysTest() {
        BodyMasker emptyMasker = new BodyMasker(List.of(), "***");

        assertThat(emptyMasker.mask("{\"password\":\"1234\"}")).isEqualTo("{\"password\":\"1234\"}");
    }

    @Test
    @DisplayName("null 이나 빈 본문에도 안전해야 한다.")
    void nullSafeTest() {
        assertThat(bodyMasker.mask(null)).isNull();
        assertThat(bodyMasker.mask("")).isEmpty();
    }

    @Test
    @DisplayName("대체 문자열에 정규식 특수문자가 있어도 그대로 들어가야 한다.")
    void replacementWithSpecialCharacterTest() {
        BodyMasker dollarMasker = new BodyMasker(List.of("password"), "$[가림]");

        assertThat(dollarMasker.mask("{\"password\":\"1234\"}")).isEqualTo("{\"password\":\"$[가림]\"}");
    }
}
