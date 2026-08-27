package kr.co.mylogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * "어떤 컨트롤러 메서드가 이 응답을 만들었는가" 를 요청 속성에 기록한다.
 * ApiAccessLogFilter 가 응답을 기록할 때 이 값을 handler 필드로 함께 내보낸다.
 */
public class HandlerNameInterceptor implements HandlerInterceptor {

    static final String HANDLER_ATTRIBUTE = HandlerNameInterceptor.class.getName() + ".HANDLER";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // /error 로 포워딩되면 BasicErrorController 로 덮어써지므로, 최초 핸들러만 남긴다.
        if (handler instanceof HandlerMethod handlerMethod && null == request.getAttribute(HANDLER_ATTRIBUTE)) {
            String handlerName = handlerMethod.getBeanType().getSimpleName()
                    + "#"
                    + handlerMethod.getMethod().getName();

            request.setAttribute(HANDLER_ATTRIBUTE, handlerName);
        }

        return true;
    }
}
