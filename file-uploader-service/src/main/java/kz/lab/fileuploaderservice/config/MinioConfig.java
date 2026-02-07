package kz.lab.fileuploaderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class MinioConfig {

    @Bean
    public S3AsyncClient s3AsyncClient(MinioProperties properties){

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        );

        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccessEnabled())
                .build();

        return S3AsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(Duration.ofMillis(properties.getRequestTimeout()))
                        .apiCallAttemptTimeout(Duration.ofMillis(properties.getRequestTimeout())))
                .build();

    }
}


@ConfigurationProperties(prefix = "application.minio")
@Configuration
@Getter
@Setter
class MinioProperties{

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucketName;
    private boolean pathStyleAccessEnabled;
    private long maxFileSize;
    private long requestTimeout;

}
