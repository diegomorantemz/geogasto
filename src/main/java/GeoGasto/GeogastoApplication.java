package GeoGasto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GeogastoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeogastoApplication.class, args);
	}

}
