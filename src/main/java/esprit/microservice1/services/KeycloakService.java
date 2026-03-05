package esprit.microservice1.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class KeycloakService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create Keycloak admin client using souhir client credentials
     */
    private Keycloak getKeycloakInstance() {
        log.info("Connecting to Keycloak at {} with client '{}' on realm '{}'",
                keycloakServerUrl, clientId, realm);
        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    /**
     * Authenticate a user against Keycloak and return the access token.
     * Uses the Resource Owner Password Credentials (ROPC) grant type.
     *
     * @param email    User email (username)
     * @param password User password
     * @return JWT access token from Keycloak
     */
    public String authenticateUser(String email, String password) {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", email);
        body.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String accessToken = jsonNode.get("access_token").asText();
                log.info("User authenticated successfully via Keycloak: {}", email);
                return accessToken;
            } else {
                throw new RuntimeException("Keycloak authentication failed with status: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("Keycloak authentication failed for user {}: {} - {}", email, e.getStatusCode(),
                    e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("Invalid email or password");
            }
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error authenticating user via Keycloak: {}", e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new user in Keycloak
     * 
     * @param email     User email
     * @param firstName User first name
     * @param lastName  User last name
     * @param password  User password
     * @return Keycloak user ID
     */
    public String createUser(String email, String firstName, String lastName, String password) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            // Create user representation
            UserRepresentation user = new UserRepresentation();
            user.setEmail(email);
            user.setEmailVerified(false);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setUsername(email); // Use email as username

            // Create user
            jakarta.ws.rs.core.Response response = usersResource.create(user);

            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user in Keycloak: " + response.getStatus());
            }

            // Extract user ID from response
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

            // Set password
            setUserPassword(userId, password, false);

            log.info("User created in Keycloak with ID: {}", userId);
            return userId;

        } catch (Exception e) {
            log.error("Error creating user in Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error creating user in Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Set or reset user password
     * 
     * @param userId      Keycloak user ID
     * @param password    New password
     * @param isTemporary Is this a temporary password
     */
    public void setUserPassword(String userId, String password, boolean isTemporary) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(isTemporary);

            usersResource.get(userId).resetPassword(credential);
            log.info("Password set for user: {}", userId);

        } catch (Exception e) {
            log.error("Error setting password in Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error setting password: " + e.getMessage(), e);
        }
    }

    /**
     * Update user information
     * 
     * @param userId    Keycloak user ID
     * @param firstName New first name
     * @param lastName  New last name
     */
    public void updateUser(String userId, String firstName, String lastName) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            UserRepresentation user = usersResource.get(userId).toRepresentation();
            user.setFirstName(firstName);
            user.setLastName(lastName);

            usersResource.get(userId).update(user);
            log.info("User updated in Keycloak: {}", userId);

        } catch (Exception e) {
            log.error("Error updating user in Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error updating user: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a user from Keycloak
     * 
     * @param userId Keycloak user ID
     */
    public void deleteUser(String userId) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            usersResource.delete(userId);
            log.info("User deleted from Keycloak: {}", userId);

        } catch (Exception e) {
            log.error("Error deleting user from Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error deleting user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user information from Keycloak
     * 
     * @param userId Keycloak user ID
     * @return User representation
     */
    public UserRepresentation getUserById(String userId) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            return usersResource.get(userId).toRepresentation();

        } catch (Exception e) {
            log.error("Error getting user from Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error getting user: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by email
     * 
     * @param email User email
     * @return User representation
     */
    public UserRepresentation getUserByEmail(String email) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            List<UserRepresentation> users = usersResource.search(email, true);

            if (users.isEmpty()) {
                return null;
            }

            return users.get(0);

        } catch (Exception e) {
            log.error("Error searching user in Keycloak: {}", e.getMessage());
            throw new RuntimeException("Error searching user: " + e.getMessage(), e);
        }
    }

    /**
     * Send password reset email
     * 
     * @param userId Keycloak user ID
     */
    public void sendPasswordResetEmail(String userId) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realm = keycloakInstance.realm(this.realm);
            UsersResource usersResource = realm.users();

            usersResource.get(userId).executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
            log.info("Password reset email sent to user: {}", userId);

        } catch (Exception e) {
            log.error("Error sending password reset email: {}", e.getMessage());
            throw new RuntimeException("Error sending password reset email: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user exists in Keycloak
     * 
     * @param email User email
     * @return True if exists, false otherwise
     */
    public boolean userExists(String email) {
        return getUserByEmail(email) != null;
    }
}
