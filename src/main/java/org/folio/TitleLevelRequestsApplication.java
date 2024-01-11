package org.folio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TitleLevelRequestsApplication {

  public static void main(String[] args) {
    SpringApplication.run(TitleLevelRequestsApplication.class, args);
  }

}
