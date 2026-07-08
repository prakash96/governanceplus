package com.governanceplus.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Allows the React dev server (Vite, typically localhost:5173) to call the
     * API directly — Vite's dev proxy forwards the original Origin header, so
     * Spring still sees these as cross-origin even though the browser only
     * ever talks to Vite. Must list every HTTP method any endpoint uses (the
     * Rules CRUD API added PUT/DELETE after this was first written) — a
     * missing method here doesn't 404, it fails with an opaque CORS 403 that
     * only shows up in the separate-dev-server workflow, never in the bundled
     * single-origin jar.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    /**
     * Serves the bundled React build from classpath:/static/, falling back to
     * index.html for any path that isn't a real static file (and isn't
     * already handled by an @RestController mapping) — needed so client-side
     * routes like /reviews/{jobId} resolve correctly on a hard refresh.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
