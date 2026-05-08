package org.example.expert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Sping MVC 관련 설정 클래스
 *
 * AuthUserArqumentResolver를 Spring MVC에 등록해서
 * 컨트롤러에서 @Auth AuthUser를 사용할 수 있게 해준다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }
}
