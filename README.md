
# mylogger-spring-boot-starter

REST API 응답을 **2XX / 3XX / 4XX / 5XX 로 분류해서 기록**하고, 그대로 **ELK 로 보내는** 스프링 부트 스타터.

의존성 한 줄과 logback include 한 줄이면, 요청 하나가 **사람이 읽는 로그 한 줄**과 **Kibana 에서 집계 가능한 문서 하나**가 된다.

```
2026-08-27 12:38:31.322 INFO  [2XX 성공           ] POST   /users               -> 200 OK              (   97ms) | handler=UserController#createUser | client=127.0.0.1 | reqId=b9378465 | 요청본문={"userId":"kim","password":"***"}
2026-08-27 12:38:31.597 WARN  [4XX 클라이언트 오류] POST   /users               -> 400 Bad Request     (   27ms) | handler=UserController#createUser | client=127.0.0.1 | reqId=115f6ff6 | 응답본문=유효성 검증 실패
2026-08-27 12:38:31.740 ERROR [5XX 서버 오류      ] GET    /orders/1            -> 500 Internal Server Error (  12ms) | handler=OrderController#find | client=127.0.0.1 | reqId=12e2b1db
```

---

## 1. 설치

빌드해서 로컬 저장소에 넣는다.

```bash
mvn install
```

쓰는 쪽 `pom.xml`:

```xml
<dependency>
    <groupId>kr.co</groupId>
    <artifactId>mylogger-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

쓰는 쪽 `src/main/resources/logback.xml` 에 한 줄 추가:

```xml
<include resource="logback-api-access.xml"/>
```

이게 전부다. 필터·인터셉터 등록은 자동 설정이 처리한다.

> 라이브러리가 로그 출력을 통째로 강제하면 쓰는 쪽이 통제권을 잃으므로, Appender 정의는 include 방식으로 제공한다.
> `root` 로거는 건드리지 않는다.

### 요구 사항

| 항목 | 버전 |
|---|---|
| Java | 17+ |
| Spring Boot | 3.0.x (서블릿 웹) |
| logstash-logback-encoder | 7.4 (스타터가 함께 가져온다) |

---

## 2. 설정

전부 기본값이 있어서 아무것도 안 써도 동작한다. 바꾸고 싶은 것만 쓰면 된다.

```yaml
api-access-log:
  enabled: true                       # 기능 전체 on/off
  logger-name: API_ACCESS             # logback 에서 Appender 를 붙일 로거 이름
  order: -2147483638                  # 필터 순서 (낮을수록 앞)
  excluded-path-prefixes:             # 아예 로그를 남기지 않을 경로
    - /actuator/
    - /favicon.ico
  max-body-length: 500                # 본문 기록 최대 길이 (넘으면 잘라냄)
  include-request-body: true
  include-response-body-on-error: true  # 성공 응답 본문은 어차피 기록하지 않는다
  request-body-methods: [POST, PUT, PATCH]
  masking:
    keys: [password, token, authorization]   # 값을 가릴 키 (대소문자 무시)
    replacement: "***"
    excluded-body-path-prefixes: [/auth/]    # 본문을 통째로 안 남길 경로
```

IDE 자동완성이 되도록 `spring-configuration-metadata.json` 을 함께 빌드한다.

### 로그 파일 위치 등 바꾸기

logback 프로퍼티를 **include 앞에서** 정의하면 덮어쓴다.

```xml
<property name="API_LOG_APP_NAME" value="my-service"/>
<property name="API_LOG_DIR" value="/var/log/my-service"/>
<property name="API_LOG_LOGSTASH_DESTINATION" value="192.168.0.10:5044"/>

<include resource="logback-api-access.xml"/>
```

---

## 3. 마스킹 — 반드시 확인할 것

이 스타터는 **요청 본문을 로그에 남긴다.** 아무 설정 없이 쓰면 로그인 API 의 비밀번호가 Elasticsearch 에 평문으로 쌓인다.
그래서 기본으로 다음 키의 값을 가린다.

```
password, passwd, pwd, token, accessToken, access_token, refreshToken, refresh_token,
authorization, secret, apiKey, api_key, creditCard, credit_card, cardNumber, card_number, cvc, ssn
```

JSON 과 폼/쿼리 두 표기를 모두 처리한다.

```
{"userId":"kim","password":"1q2w3e4r"}  ->  {"userId":"kim","password":"***"}
userId=kim&password=1q2w3e4r            ->  userId=kim&password=***
```

키 이름은 **정확히 일치**해야 한다. `password` 를 등록해도 `password_hint` 는 가리지 않는다.

**키 이름을 예측할 수 없는 API 는 경로째로 빼는 편이 안전하다.**

```yaml
api-access-log:
  masking:
    excluded-body-path-prefixes: [/auth/, /payments/]
```

---

## 4. 남는 로그

### 파일

| 파일 | 내용 |
|---|---|
| `logs/api-access.log` | 전체 요청 |
| `logs/api-error.log` | 4XX(WARN) / 5XX(ERROR) 만 |

### Logstash 로 나가는 JSON

`message` 에는 위의 사람이 읽는 한 줄이 그대로 들어가고, 집계용 값은 별도 필드로 분리된다.
숫자는 **문자열이 아니라 숫자 타입**이라 Kibana 에서 평균/최댓값 집계가 바로 된다.

```json
{
  "@timestamp": "2026-08-27T12:38:31.128+09:00",
  "app_name": "my-service",
  "log_type": "api_access",
  "level": "WARN",
  "message": "[4XX 클라이언트 오류] GET    /users/999 ...",
  "http_status": 404,
  "status_class": "4XX",
  "status_category": "CLIENT_ERROR",
  "status_label": "클라이언트 오류",
  "status_reason": "Not Found",
  "http_method": "GET",
  "uri": "/users/999",
  "handler": "UserController#find",
  "duration_ms": 2,
  "client_ip": "127.0.0.1",
  "request_id": "8d21a5a9",
  "response_body": "찾을 수 없습니다"
}
```

| 필드 | 설명 |
|---|---|
| `status_class` | `2XX`/`3XX`/`4XX`/`5XX` — 대시보드의 분류 기준 |
| `status_category` | `SUCCESS`/`REDIRECTION`/`CLIENT_ERROR`/`SERVER_ERROR` |
| `duration_ms` | 처리 시간(ms). 느린 엔드포인트 찾기용 |
| `handler` | 응답을 만든 컨트롤러 메서드 |
| `request_id` | 같은 요청의 애플리케이션 로그와 묶는 열쇠 |
| `log_type` | `api_access` / `application` — 인덱스 분기 기준 |

### request_id 로 요청 하나 통째로 추적하기

발급한 ID 를 MDC 에 넣으므로, 쓰는 쪽 로그 패턴에 `%X{request_id}` 를 넣으면 그 요청에서 나온 모든 로그에 같은 값이 찍힌다.
응답 헤더 `X-Request-Id` 로도 돌려준다.

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] [%X{request_id:-        }] %logger{36} - %msg%n</pattern>
```

---

## 5. ELK 연결

스타터 jar 의 `elk/` 안에 필요한 자산이 전부 들어 있다. 압축을 풀거나 이 저장소의 `src/main/resources/elk/` 를 그대로 쓰면 된다.

| 파일 | 용도 |
|---|---|
| `docker-compose.yml` | Elasticsearch + Logstash + Kibana |
| `logstash.conf` | `json_lines` 수신, `log_type` 별 인덱스 분기, 태깅 |
| `index-template-*.json` | Elasticsearch 매핑 (`http_status`=integer 등) |
| `kibana-dashboard.ndjson` | 데이터 뷰 · 시각화 8개 · 대시보드 |
| `setup-elk.sh` / `.ps1` | 위 두 가지를 등록 |

```bash
docker compose up -d
./setup-elk.sh
```

인덱스는 두 갈래로 나뉜다.

- `api-access-logs-YYYY.MM.dd` — 분류된 접근 로그
- `application-logs-YYYY.MM.dd` — 그 밖의 애플리케이션 로그
  (쓰는 쪽 `root` 로거에 `APPLICATION_LOGSTASH` Appender 를 걸면 여기로 간다)

대시보드: http://localhost:5601/app/dashboards#/view/api-access-dashboard

Discover 에서 바로 쓰는 KQL:

```
status_class : "5XX"
status_class : "4XX" and uri : "/users"
duration_ms > 1000
request_id : "8d21a5a9"
```

---

## 6. 구성

```
kr.co.mylogger
├── ApiAccessLogAutoConfiguration   자동 설정 진입점
├── ApiAccessLogProperties          api-access-log.* 설정
├── ApiAccessLogFilter              요청/응답 가로채기, 시간 측정, request_id 발급
├── ApiAccessLogWriter              분류별 로그 레벨 + Logstash 구조화 필드
├── ApiAccessLog                    요청 한 건의 결과 + 사람이 읽는 한 줄 포매팅
├── HttpStatusCategory              상태 코드 -> 2XX/3XX/4XX/5XX 분류
├── BodyMasker                      민감한 값 가리기
├── HandlerNameInterceptor          응답을 만든 컨트롤러 메서드 이름 기록
├── HandlerNameWebMvcConfigurer     위 인터셉터 등록
└── CachedBodyHttpServletRequest    요청 본문 재사용용 래퍼
```

모든 빈에 `@ConditionalOnMissingBean` 이 붙어 있어, 쓰는 쪽에서 같은 타입의 빈을 직접 정의하면 그쪽이 우선한다.
서블릿 웹 애플리케이션이 아니거나 `api-access-log.enabled=false` 면 아무것도 등록하지 않는다.

### 한글 정렬에 관해

한글은 터미널에서 두 칸을 차지해서 `%-19s` 같은 글자 수 기준 패딩으로는 세로줄이 맞지 않는다.
`ApiAccessLog` 가 유니코드 범위로 표시 폭을 계산해 여백을 채우므로, 콘솔에서 실제로 정렬된다.

---

## 7. 테스트

```bash
mvn test
```

| 테스트 | 확인하는 것 |
|---|---|
| `HttpStatusCategoryTest` | 상태 코드 대역 분류 경계값 |
| `BodyMaskerTest` | JSON/폼 마스킹, 대소문자, 부분 키 오탐, 특수문자 대체값 |
| `ApiAccessLogFilterTest` | 분류·레벨, 마스킹, 본문 제외 경로, 길이 제한, 예외 시 500 보정, request_id |
| `ApiAccessLogWriterTest` | 레벨 분기, `{}` 치환 사고 방지, Logstash JSON 필드/타입 |
| `ApiAccessLogAutoConfigurationTest` | 자동 등록, on/off, 프로퍼티 바인딩, 사용자 빈 우선 |
| `ApiAccessLogIntegrationTest` | 실제 부트 웹 컨텍스트에서 자동 설정만으로 동작하는지 |
