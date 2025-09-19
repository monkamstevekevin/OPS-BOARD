package org.caureq.caureqopsboard;

import org.caureq.caureqopsboard.config.ProxmoxProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProxmoxProps.class)
public class CaureqOpsBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaureqOpsBoardApplication.class, args);
    }

}
