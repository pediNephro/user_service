package esprit.microservice1.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // @Override
    // public void addCorsMappings(CorsRegistry registry) {
    // registry.addMapping("/**") // Allow CORS on all API endpoints
    // .allowedOrigins("**")// Your Angular app's URL
    // .allowedMethods("GET", "POST", "PUT", "DELETE") // Allowed HTTP methods
    // .allowedHeaders("*");
    // }
}
