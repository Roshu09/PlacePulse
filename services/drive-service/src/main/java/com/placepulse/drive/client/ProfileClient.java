package com.placepulse.drive.client;

import com.placepulse.drive.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks auth-service who the signed-in student is. The student's own token is passed along, so a
 * student can only ever read their own profile this way.
 */
@Component
public class ProfileClient {

    private final RestClient client;

    public ProfileClient(@Value("${app.auth-service.url}") String baseUrl) {
        // short timeouts: if auth-service is slow or down, fail fast instead of hanging the apply request
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public StudentProfile fetch(String authorizationHeader) {
        try {
            StudentProfile profile = client.get()
                    .uri("/api/users/me")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(StudentProfile.class);
            if (profile == null) {
                throw unavailable();
            }
            return profile;
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Sign in again.");
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "We couldn't load your profile right now. Try again in a moment.");
    }
}
