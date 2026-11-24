package com.converter.docxjats.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Configuración dinámica del DataSource.
 * Intenta conectar con MySQL si está disponible en localhost:3306.
 * Si MySQL no está activo, conmuta de forma automática y transparente a H2 (archivo persistente en disco ~/docx_jats_db)
 * para que el inicio de sesión, registro y almacenamiento de documentos funcionen siempre.
 */
@Configuration
public class DataSourceFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFallbackConfig.class);

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/docx_jats_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8}")
    private String mysqlUrl;

    @Value("${spring.datasource.username:root}")
    private String mysqlUser;

    @Value("${spring.datasource.password:root}")
    private String mysqlPass;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (isMySqlAvailable("localhost", 3306)) {
            try {
                log.info("✓ Servidor MySQL detectado en localhost:3306. Conectando a {}", mysqlUrl);
                return DataSourceBuilder.create()
                        .url(mysqlUrl)
                        .username(mysqlUser)
                        .password(mysqlPass)
                        .driverClassName("com.mysql.cj.jdbc.Driver")
                        .build();
            } catch (Exception e) {
                log.warn("Error inicializando DataSource MySQL. Activando fallback a base embebida H2: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️ Servidor MySQL no encontrado en localhost:3306. Activando base de datos H2 persistente en disco.");
        }

        // Fallback a base H2 en el workspace (/tmp o ./data/docx_jats_db)
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        String h2DbPath = new File(dataDir, "docx_jats_db").getAbsolutePath();
        String h2Url = "jdbc:h2:file:" + h2DbPath + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

        return DataSourceBuilder.create()
                .url(h2Url)
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }

    private boolean isMySqlAvailable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
