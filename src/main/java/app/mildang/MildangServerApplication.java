package app.mildang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class MildangServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MildangServerApplication.class, args);
	}

}
