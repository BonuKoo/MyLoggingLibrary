package kr.co.mylogger;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HandlerNameInterceptor 를 스프링 MVC 에 등록한다.
 *
 * 자동 설정 클래스가 직접 WebMvcConfigurer 를 구현하면 빈 초기화가 너무 이른 시점에 일어날 수 있어서,
 * 별도 클래스로 분리해 빈으로 등록한다.
 */
public class HandlerNameWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerNameInterceptor());
    }
}
