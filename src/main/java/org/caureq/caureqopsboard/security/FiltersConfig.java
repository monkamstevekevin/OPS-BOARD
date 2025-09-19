package org.caureq.caureqopsboard.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FiltersConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAdminFilter> adminFilterRegistration(ApiKeyAdminFilter f) {
        var reg = new FilterRegistrationBean<>(f);
        reg.setOrder(10); // après ton filtre X-API-KEY classique si besoin
        reg.addUrlPatterns("/api/admin/*");
        return reg;
    }
}

