package esprit.microservice1;

import esprit.microservice1.dto.AuthResponse;
import esprit.microservice1.dto.LoginRequest;
import esprit.microservice1.dto.PasswordResetRequest;
import esprit.microservice1.dto.RegisterRequest;
import esprit.microservice1.dto.UserResponse;
import esprit.microservice1.services.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private IUserService userService;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            AuthResponse response = userService.register(registerRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "Erreur lors de l'inscription.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Login user
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse response = userService.login(loginRequest);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage() != null ? e.getMessage() : "Email ou mot de passe incorrect.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    /**
     * Get current user information
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@RequestHeader("Authorization") String token) {
        try {
            // Remove "Bearer " prefix if present
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            UserResponse response = userService.getMe(cleanToken);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    /**
     * Logout user
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        try {
            // Remove "Bearer " prefix if present
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            userService.logout(cleanToken);
            return ResponseEntity.status(HttpStatus.OK).body("Logout successful");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Logout failed");
        }
    }

    /**
     * Get all users
     */
    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        try {
            List<UserResponse> users = userService.getAllUsers();
            return ResponseEntity.status(HttpStatus.OK).body(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        try {
            UserResponse response = userService.getUserById(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    /**
     * Update user profile
     */
    @PutMapping("/profile/{id}")
    public ResponseEntity<UserResponse> updateProfile(@PathVariable Long id, @RequestBody UserResponse updateRequest) {
        try {
            UserResponse response = userService.updateProfile(id, updateRequest);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "User deleted successfully");
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error deleting user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Archive / unarchive user (toggle status ACTIVE <-> ARCHIVED)
     */
    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archiveUser(@PathVariable Long id) {
        try {
            UserResponse response = userService.archiveUser(id);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error archiving user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Request password reset
     */
    @PostMapping("/password-reset")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        try {
            userService.requestPasswordReset(request.getEmail());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset email sent successfully");
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error requesting password reset: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Upload profile image
     */
    @PostMapping("/profile/{id}/image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            userService.setProfileImage(id, file);
            UserResponse user = userService.getUserById(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Profile image uploaded successfully");
            response.put("imageUrl", user.getProfileImageUrl());
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error uploading image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
}
