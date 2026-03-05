package esprit.microservice1.services;

import esprit.microservice1.dto.AuthResponse;
import esprit.microservice1.dto.LoginRequest;
import esprit.microservice1.dto.RegisterRequest;
import esprit.microservice1.dto.UserResponse;
import esprit.microservice1.entities.Role;
import esprit.microservice1.entities.User;
import esprit.microservice1.repositories.RoleRepository;
import esprit.microservice1.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserService implements IUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Override
    public AuthResponse register(RegisterRequest registerRequest) {
        log.info("Registering new user with email: {}", registerRequest.getEmail());

        // Check if user already exists in database
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            log.warn("Email already exists in database: {}", registerRequest.getEmail());
            throw new RuntimeException("Email already exists");
        }

        // Check if user already exists in Keycloak
        if (keycloakService.userExists(registerRequest.getEmail())) {
            log.warn("User already exists in Keycloak: {}", registerRequest.getEmail());
            throw new RuntimeException("User already registered in authentication system");
        }

        try {
            // Get role
            Role role = roleRepository.findByName(registerRequest.getRoleName())
                    .orElseThrow(() -> new RuntimeException("Role not found: " + registerRequest.getRoleName()));

            // Step 1: Create user in Keycloak first
            String keycloakId = keycloakService.createUser(
                    registerRequest.getEmail(),
                    registerRequest.getFirstName(),
                    registerRequest.getLastName(),
                    registerRequest.getPassword());

            // Step 2: Create user in database
            User user = new User();
            user.setFirstName(registerRequest.getFirstName());
            user.setLastName(registerRequest.getLastName());
            user.setEmail(registerRequest.getEmail());
            user.setKeycloakId(keycloakId);
            user.setPhoneNumber(registerRequest.getPhoneNumber());
            user.setRole(role);
            user.setStatus("ACTIVE");

            User savedUser = userRepository.save(user);
            log.info("User registered successfully with ID: {} (DB) and {} (Keycloak)", savedUser.getId(), keycloakId);

            // Step 3: Authenticate with Keycloak to get a real JWT token
            String token = "";
            try {
                token = keycloakService.authenticateUser(
                        registerRequest.getEmail(),
                        registerRequest.getPassword());
            } catch (Exception authEx) {
                log.warn("User created but could not get token after registration: {}", authEx.getMessage());
            }

            // Return response with real token
            AuthResponse response = new AuthResponse();
            response.setId(savedUser.getId());
            response.setFirstName(savedUser.getFirstName());
            response.setLastName(savedUser.getLastName());
            response.setEmail(savedUser.getEmail());
            response.setRoleName(savedUser.getRole().getName());
            response.setRoleId(savedUser.getRole().getId());
            response.setStatus(savedUser.getStatus());
            response.setToken(token);

            return response;

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during registration: {}", e.getMessage());
            throw new RuntimeException("Email already registered", e);
        } catch (Exception e) {
            log.error("Error during registration: {}", e.getMessage(), e);
            throw new RuntimeException("Registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        log.info("Logging in user with email: {}", loginRequest.getEmail());

        try {
            // Step 1: Check if user exists in DATABASE
            User user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found in database"));
            log.info("User found in database: {} (ID: {})", user.getEmail(), user.getId());

            // Step 2: Check if user is active
            if (!"ACTIVE".equals(user.getStatus())) {
                throw new RuntimeException("Your account has been archived. Please contact an administrator.");
            }

            // Step 3: Verify user exists in KEYCLOAK
            UserRepresentation keycloakUser = keycloakService.getUserByEmail(loginRequest.getEmail());
            if (keycloakUser == null) {
                throw new RuntimeException("User not found in Keycloak authentication system");
            }
            log.info("User found in Keycloak: {} (KC ID: {})", keycloakUser.getEmail(), keycloakUser.getId());

            // Step 4: Authenticate with Keycloak (validates password and returns JWT token)
            String token = keycloakService.authenticateUser(
                    loginRequest.getEmail(),
                    loginRequest.getPassword());
            log.info("User authenticated successfully via Keycloak, token obtained");

            // Build response with real Keycloak JWT token
            AuthResponse response = new AuthResponse();
            response.setId(user.getId());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setEmail(user.getEmail());
            response.setRoleName(user.getRole().getName());
            response.setRoleId(user.getRole().getId());
            response.setStatus(user.getStatus());
            response.setToken(token); // Real JWT token from Keycloak

            log.info("Login successful for user: {} | DB ID: {} | Keycloak ID: {}",
                    user.getEmail(), user.getId(), user.getKeycloakId());
            return response;

        } catch (Exception e) {
            log.error("Login error for {}: {}", loginRequest.getEmail(), e.getMessage());
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }

    @Override
    public UserResponse getMe(String token) {
        log.info("Getting current user info from token");

        try {
            // Decode JWT token
            Jwt jwt = jwtDecoder.decode(token);
            String email = jwt.getClaimAsString("email");

            if (email == null || email.isEmpty()) {
                throw new RuntimeException("Email not found in token");
            }

            // Find user in database
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found in database"));

            return mapUserToResponse(user);

        } catch (JwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid or expired token", e);
        } catch (Exception e) {
            log.error("Error getting current user: {}", e.getMessage());
            throw new RuntimeException("Unauthorized: " + e.getMessage(), e);
        }
    }

    @Override
    public void logout(String token) {
        // Token validation is handled by Spring Security
        log.info("User logged out successfully");
    }

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");

        return userRepository.findAll().stream()
                .map(this::mapUserToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse updateProfile(Long id, UserResponse updateRequest) {
        log.info("Updating profile for user: {}", id);

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Update in database
            user.setFirstName(updateRequest.getFirstName());
            user.setLastName(updateRequest.getLastName());
            if (updateRequest.getPhoneNumber() != null) {
                user.setPhoneNumber(updateRequest.getPhoneNumber());
            }

            // Update in Keycloak
            keycloakService.updateUser(
                    user.getKeycloakId(),
                    updateRequest.getFirstName(),
                    updateRequest.getLastName());

            User savedUser = userRepository.save(user);
            log.info("Profile updated successfully for user: {}", id);

            return mapUserToResponse(savedUser);

        } catch (Exception e) {
            log.error("Error updating profile: {}", e.getMessage());
            throw new RuntimeException("Error updating profile: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(Long id) {
        log.info("Deleting user: {}", id);

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Delete profile image if exists
            if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                try {
                    cloudinaryService.deleteProfileImage(user.getId());
                } catch (Exception e) {
                    log.warn("Error deleting profile image: {}", e.getMessage());
                }
            }

            // Delete from Keycloak
            keycloakService.deleteUser(user.getKeycloakId());

            // Delete from database
            userRepository.delete(user);
            log.info("User deleted successfully: {}", id);

        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage());
            throw new RuntimeException("Error deleting user: " + e.getMessage(), e);
        }
    }

    @Override
    public void requestPasswordReset(String email) {
        log.info("Requesting password reset for: {}", email);

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Send reset email via Keycloak
            keycloakService.sendPasswordResetEmail(user.getKeycloakId());
            log.info("Password reset email sent successfully");

        } catch (Exception e) {
            log.error("Error requesting password reset: {}", e.getMessage());
            throw new RuntimeException("Error requesting password reset: " + e.getMessage(), e);
        }
    }

    @Override
    public void setProfileImage(Long userId, MultipartFile imageFile) {
        log.info("Setting profile image for user: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Upload to Cloudinary
            String imageUrl = cloudinaryService.uploadProfileImage(imageFile, userId);

            // Update user with image URL
            user.setProfileImageUrl(imageUrl);
            userRepository.save(user);

            log.info("Profile image updated successfully for user: {}", userId);

        } catch (Exception e) {
            log.error("Error setting profile image: {}", e.getMessage());
            throw new RuntimeException("Error uploading image: " + e.getMessage(), e);
        }
    }

    @Override
    public UserResponse getUserById(Long id) {
        log.info("Getting user by ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return mapUserToResponse(user);
    }

    @Override
    public UserResponse archiveUser(Long id) {
        log.info("Toggling archive status for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("ACTIVE".equals(user.getStatus())) {
            user.setStatus("ARCHIVED");
        } else {
            user.setStatus("ACTIVE");
        }

        User saved = userRepository.save(user);
        log.info("User {} status changed to {}", id, saved.getStatus());
        return mapUserToResponse(saved);
    }

    /**
     * Helper method to map User entity to UserResponse DTO
     */
    private UserResponse mapUserToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setProfileImageUrl(user.getProfileImageUrl());
        response.setRoleName(user.getRole().getName());
        response.setRoleId(user.getRole().getId());
        response.setStatus(user.getStatus());
        return response;
    }
}
