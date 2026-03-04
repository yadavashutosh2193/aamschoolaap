package aamscool.backend.aamschoolbackend.service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Service
public class GoogleIdTokenService {

    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");

    @Value("${app.security.google.client-id:}")
    private String googleClientId;

    @Value("${app.security.google.discovery-url:https://accounts.google.com/.well-known/openid-configuration}")
    private String discoveryUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String jwksUri;
    private volatile Instant discoveryExpiresAt = Instant.EPOCH;
    private volatile Map<String, RSAPublicKey> signingKeys = Map.of();
    private volatile Instant signingKeysExpiresAt = Instant.EPOCH;

    public GoogleIdTokenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public GoogleProfile verify(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalArgumentException("Google Sign-In is not configured");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required");
        }

        try {
            JsonNode header = readJwtSection(idToken, 0);
            String kid = header.path("kid").asText(null);
            String algorithm = header.path("alg").asText(null);

            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("Invalid Google ID token header");
            }
            if (!"RS256".equals(algorithm)) {
                throw new IllegalArgumentException("Unsupported Google ID token algorithm");
            }

            RSAPublicKey publicKey = resolveSigningKey(kid);
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(idToken)
                    .getBody();

            String audience = claims.getAudience();
            if (!googleClientId.equals(audience)) {
                throw new IllegalArgumentException("Google ID token audience is invalid");
            }

            String issuer = claims.getIssuer();
            if (!"accounts.google.com".equals(issuer) && !"https://accounts.google.com".equals(issuer)) {
                throw new IllegalArgumentException("Google ID token issuer is invalid");
            }

            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Google account email is missing");
            }
            if (!isEmailVerified(claims.get("email_verified"))) {
                throw new IllegalArgumentException("Google account email is not verified");
            }

            String googleSubject = claims.getSubject();
            if (googleSubject == null || googleSubject.isBlank()) {
                throw new IllegalArgumentException("Google account id is missing");
            }

            String name = claims.get("name", String.class);
            return new GoogleProfile(googleSubject, email, name);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Google ID token");
        }
    }

    private JsonNode readJwtSection(String jwt, int index) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2 || index >= parts.length) {
            throw new IllegalArgumentException("Invalid Google ID token");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(parts[index]);
        return objectMapper.readTree(decoded);
    }

    private RSAPublicKey resolveSigningKey(String kid) throws Exception {
        Instant now = Instant.now();
        Map<String, RSAPublicKey> currentKeys = signingKeys;
        if (now.isBefore(signingKeysExpiresAt) && currentKeys.containsKey(kid)) {
            return currentKeys.get(kid);
        }

        synchronized (this) {
            now = Instant.now();
            currentKeys = signingKeys;
            if (now.isBefore(signingKeysExpiresAt) && currentKeys.containsKey(kid)) {
                return currentKeys.get(kid);
            }

            refreshSigningKeys();
            RSAPublicKey key = signingKeys.get(kid);
            if (key == null) {
                throw new IllegalArgumentException("Unable to verify Google ID token");
            }
            return key;
        }
    }

    private void refreshSigningKeys() throws Exception {
        String currentJwksUri = resolveJwksUri();
        HttpResponse<String> response = sendGet(currentJwksUri);
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode keysNode = json.path("keys");
        if (!keysNode.isArray()) {
            throw new IllegalArgumentException("Invalid Google signing key response");
        }

        Map<String, RSAPublicKey> keys = new HashMap<>();
        for (JsonNode keyNode : keysNode) {
            String keyType = keyNode.path("kty").asText();
            String use = keyNode.path("use").asText();
            String kid = keyNode.path("kid").asText(null);
            String modulus = keyNode.path("n").asText(null);
            String exponent = keyNode.path("e").asText(null);

            if (!"RSA".equals(keyType) || !"sig".equals(use) || kid == null || modulus == null || exponent == null) {
                continue;
            }
            keys.put(kid, buildRsaKey(modulus, exponent));
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException("No Google signing keys available");
        }

        signingKeys = Map.copyOf(keys);
        signingKeysExpiresAt = Instant.now().plusSeconds(resolveMaxAgeSeconds(response.headers().firstValue("Cache-Control").orElse(null)));
    }

    private String resolveJwksUri() throws Exception {
        Instant now = Instant.now();
        if (jwksUri != null && now.isBefore(discoveryExpiresAt)) {
            return jwksUri;
        }

        synchronized (this) {
            now = Instant.now();
            if (jwksUri != null && now.isBefore(discoveryExpiresAt)) {
                return jwksUri;
            }

            HttpResponse<String> response = sendGet(discoveryUrl);
            JsonNode json = objectMapper.readTree(response.body());
            String resolvedJwksUri = json.path("jwks_uri").asText(null);
            if (resolvedJwksUri == null || resolvedJwksUri.isBlank()) {
                throw new IllegalArgumentException("Google Sign-In discovery failed");
            }

            jwksUri = resolvedJwksUri;
            discoveryExpiresAt = Instant.now()
                    .plusSeconds(resolveMaxAgeSeconds(response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null)));
            return jwksUri;
        }
    }

    private HttpResponse<String> sendGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException("Google Sign-In verification failed");
        }
        return response;
    }

    private long resolveMaxAgeSeconds(String cacheControl) {
        if (cacheControl == null || cacheControl.isBlank()) {
            return 3600;
        }
        Matcher matcher = MAX_AGE_PATTERN.matcher(cacheControl);
        if (!matcher.find()) {
            return 3600;
        }
        return Long.parseLong(matcher.group(1));
    }

    private RSAPublicKey buildRsaKey(String modulus, String exponent) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, modulusBytes),
                new BigInteger(1, exponentBytes));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private boolean isEmailVerified(Object emailVerifiedClaim) {
        if (emailVerifiedClaim instanceof Boolean value) {
            return value;
        }
        if (emailVerifiedClaim instanceof String value) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    public record GoogleProfile(String googleSubject, String email, String name) {
    }
}
