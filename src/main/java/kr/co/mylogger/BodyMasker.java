package kr.co.mylogger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요청/응답 본문에서 민감한 값을 가린다.
 *
 * 로그는 한번 Elasticsearch 에 들어가면 지우기 번거롭다.
 * 비밀번호나 토큰이 평문으로 쌓이는 사고를 막기 위해, 기록 직전에 값을 대체한다.
 *
 * 두 가지 표기를 처리한다.
 * - JSON  : {@code {"password":"1234"}} -> {@code {"password":"***"}}
 * - 폼/쿼리 : {@code id=kim&password=1234} -> {@code id=kim&password=***}
 *
 * 키 이름은 정확히 일치해야 한다. {@code password} 를 등록해도 {@code password_hint} 는 가리지 않는다.
 */
public class BodyMasker {

    private final List<MaskingRule> rules;

    public BodyMasker(List<String> keys, String replacement) {
        this.rules = compile(keys, null == replacement ? "***" : replacement);
    }

    public String mask(String body) {
        if (null == body || body.isBlank() || rules.isEmpty()) {
            return body;
        }

        String masked = body;

        for (MaskingRule rule : rules) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }

        return masked;
    }

    private List<MaskingRule> compile(List<String> keys, String replacement) {
        List<MaskingRule> compiled = new ArrayList<>();

        if (null == keys) {
            return compiled;
        }

        String quotedReplacement = Matcher.quoteReplacement(replacement);

        for (String key : keys) {
            if (null == key || key.isBlank()) {
                continue;
            }

            String quotedKey = Pattern.quote(key.trim());

            // JSON: "key" : "값"  또는  "key" : 숫자/true/null
            compiled.add(new MaskingRule(
                    Pattern.compile("(\"" + quotedKey + "\"\\s*:\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\]\\s]+)",
                            Pattern.CASE_INSENSITIVE),
                    "$1\"" + quotedReplacement + "\""));

            // 폼/쿼리스트링: key=값  (앞이 시작, & , ? , ; 인 경우만 - 다른 키의 꼬리에 걸리지 않도록)
            compiled.add(new MaskingRule(
                    Pattern.compile("(^|[&?;])(" + quotedKey + "\\s*=)([^&\\s]*)",
                            Pattern.CASE_INSENSITIVE),
                    "$1$2" + quotedReplacement));
        }

        return compiled;
    }

    private record MaskingRule(Pattern pattern, String replacement) {
    }
}
