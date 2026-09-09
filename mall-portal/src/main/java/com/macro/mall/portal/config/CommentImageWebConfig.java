package com.macro.mall.portal.config;

import com.macro.mall.portal.service.CommentImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CommentImageWebConfig implements WebMvcConfigurer {
    @Autowired
    private CommentImageService commentImageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = commentImageService.getStorageDirectory().toUri().toString();
        registry.addResourceHandler("/uploads/comments/**").addResourceLocations(location);
    }
}
