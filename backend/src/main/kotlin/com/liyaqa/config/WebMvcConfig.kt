package com.liyaqa.config

import com.liyaqa.subscription.interceptor.SubscriptionEnforcementInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val subscriptionEnforcementInterceptor: SubscriptionEnforcementInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(subscriptionEnforcementInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/v1/auth/**",
                "/api/v1/arena/auth/**",
                "/api/v1/nexus/**",
            )
    }
}
