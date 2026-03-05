package esprit.microservice1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
@EnableDiscoveryClient
public class Microservice1Application {

	public static void main(String[] args) {
		SpringApplication.run(Microservice1Application.class, args);
	}


}
