package com.fitmatch.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class GcsConfig {

    @Value("${app.gcs.credentials-path:#{null}}")
    private String credentialsPath;

    @Value("${app.gcs.project-id:#{null}}")
    private String projectId;

    @Bean
    @ConditionalOnProperty(name = "app.gcs.enabled", havingValue = "true")
    public Storage gcsStorage() throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();

        if (credentialsPath != null && !credentialsPath.isBlank()) {
            builder.setCredentials(
                    GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                            .createScoped("https://www.googleapis.com/auth/cloud-platform")
            );
        } else {
            // Application Default Credentials (ADC) — tự động trên GCloud
            builder.setCredentials(GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform"));
        }

        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }

        return builder.build().getService();
    }
}
