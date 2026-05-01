package esprit.microservice1.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.microservice1.UserController;
import esprit.microservice1.dto.AuthResponse;
import esprit.microservice1.dto.LoginRequest;
import esprit.microservice1.dto.PasswordResetRequest;
import esprit.microservice1.dto.RegisterRequest;
import esprit.microservice1.dto.UserResponse;
import esprit.microservice1.services.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
    }
)
@DisplayName("UserController - Tests d'intégration (MockMvc)")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IUserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private AuthResponse authResponse;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        authResponse = new AuthResponse();
        authResponse.setEmail("alice@hospital.tn");
        authResponse.setRoleName("DOCTOR");
        authResponse.setToken("jwt-token");
        authResponse.setStatus("ACTIVE");

        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setFirstName("Alice");
        userResponse.setLastName("Martin");
        userResponse.setEmail("alice@hospital.tn");
        userResponse.setStatus("ACTIVE");
    }

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /register - 201 Created avec token")
    void register_success_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Alice");
        req.setLastName("Martin");
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");
        req.setRoleName("DOCTOR");

        when(userService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@hospital.tn"))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.roleName").value("DOCTOR"));

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /register - 400 Bad Request si email déjà utilisé")
    void register_emailConflict_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@hospital.tn");
        req.setRoleName("DOCTOR");

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login - 200 OK avec token")
    void login_success_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");

        when(userService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@hospital.tn"))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /login - 401 Unauthorized si credentials invalides")
    void login_badCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("unknown@hospital.tn");
        req.setPassword("wrong");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Login failed: invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Login failed: invalid credentials"));
    }

    @Test
    @DisplayName("POST /login - 401 si compte archivé")
    void login_archivedAccount_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Account is archived"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/auth/me ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me - 200 OK avec token valide")
    void getMe_validToken_returns200() throws Exception {
        when(userService.getMe("valid-token")).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@hospital.tn"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @DisplayName("GET /me - 401 si token invalide")
    void getMe_invalidToken_returns401() throws Exception {
        when(userService.getMe(anyString()))
                .thenThrow(new RuntimeException("Unauthorized"));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/auth/logout ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout - 200 OK avec token valide")
    void logout_success_returns200() throws Exception {
        doNothing().when(userService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Logout successful"));
    }

    @Test
    @DisplayName("POST /logout - 401 si erreur token")
    void logout_error_returns401() throws Exception {
        doThrow(new RuntimeException("Token expired")).when(userService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/auth/all ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /all - 200 OK avec liste d'utilisateurs")
    void getAllUsers_returns200() throws Exception {
        UserResponse user2 = new UserResponse();
        user2.setId(2L);
        user2.setEmail("bob@hospital.tn");
        user2.setFirstName("Bob");
        user2.setLastName("Dupont");

        when(userService.getAllUsers()).thenReturn(List.of(userResponse, user2));

        mockMvc.perform(get("/api/auth/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("alice@hospital.tn"))
                .andExpect(jsonPath("$[1].email").value("bob@hospital.tn"));
    }

    @Test
    @DisplayName("GET /all - 500 si erreur service")
    void getAllUsers_serviceError_returns500() throws Exception {
        when(userService.getAllUsers()).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/auth/all"))
                .andExpect(status().isInternalServerError());
    }

    // ─── GET /api/auth/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} - 200 OK si utilisateur trouvé")
    void getUserById_found_returns200() throws Exception {
        when(userService.getUserById(1L)).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@hospital.tn"));
    }

    @Test
    @DisplayName("GET /{id} - 404 si utilisateur introuvable")
    void getUserById_notFound_returns404() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/api/auth/99"))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/auth/profile/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("PUT /profile/{id} - 200 OK avec profil mis à jour")
    void updateProfile_success_returns200() throws Exception {
        UserResponse updateReq = new UserResponse();
        updateReq.setFirstName("Alicia");
        updateReq.setLastName("Martino");

        UserResponse updated = new UserResponse();
        updated.setId(1L);
        updated.setFirstName("Alicia");
        updated.setLastName("Martino");
        updated.setEmail("alice@hospital.tn");

        when(userService.updateProfile(eq(1L), any(UserResponse.class))).thenReturn(updated);

        mockMvc.perform(put("/api/auth/profile/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alicia"))
                .andExpect(jsonPath("$.lastName").value("Martino"));
    }

    @Test
    @DisplayName("PUT /profile/{id} - 400 si utilisateur introuvable")
    void updateProfile_notFound_returns400() throws Exception {
        when(userService.updateProfile(eq(99L), any(UserResponse.class)))
                .thenThrow(new RuntimeException("Error updating profile"));

        mockMvc.perform(put("/api/auth/profile/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UserResponse())))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /api/auth/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} - 200 OK après suppression")
    void deleteUser_success_returns200() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/auth/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        verify(userService).deleteUser(1L);
    }

    @Test
    @DisplayName("DELETE /{id} - 400 si erreur lors de la suppression")
    void deleteUser_error_returns400() throws Exception {
        doThrow(new RuntimeException("User not found")).when(userService).deleteUser(99L);

        mockMvc.perform(delete("/api/auth/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── PUT /api/auth/{id}/archive ───────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id}/archive - 200 OK, bascule ACTIVE → ARCHIVED")
    void archiveUser_success_returns200() throws Exception {
        UserResponse archived = new UserResponse();
        archived.setId(1L);
        archived.setEmail("alice@hospital.tn");
        archived.setStatus("ARCHIVED");

        when(userService.archiveUser(1L)).thenReturn(archived);

        mockMvc.perform(put("/api/auth/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("PUT /{id}/archive - 400 si utilisateur introuvable")
    void archiveUser_notFound_returns400() throws Exception {
        when(userService.archiveUser(99L)).thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(put("/api/auth/99/archive"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── POST /api/auth/password-reset ───────────────────────────────────────

    @Test
    @DisplayName("POST /password-reset - 200 OK si email connu")
    void requestPasswordReset_success_returns200() throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setEmail("alice@hospital.tn");

        doNothing().when(userService).requestPasswordReset("alice@hospital.tn");

        mockMvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset email sent successfully"));
    }

    @Test
    @DisplayName("POST /password-reset - 400 si email inconnu")
    void requestPasswordReset_unknownEmail_returns400() throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setEmail("ghost@hospital.tn");

        doThrow(new RuntimeException("User not found")).when(userService)
                .requestPasswordReset("ghost@hospital.tn");

        mockMvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ─── POST /api/auth/profile/{id}/image ───────────────────────────────────

    @Test
    @DisplayName("POST /profile/{id}/image - 200 OK avec URL d'image")
    void uploadProfileImage_success_returns200() throws Exception {
        userResponse.setProfileImageUrl("https://cloudinary.com/img.jpg");

        doNothing().when(userService).setProfileImage(eq(1L), any());
        when(userService.getUserById(1L)).thenReturn(userResponse);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "image-data".getBytes());

        mockMvc.perform(multipart("/api/auth/profile/1/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile image uploaded successfully"))
                .andExpect(jsonPath("$.imageUrl").value("https://cloudinary.com/img.jpg"));
    }

    @Test
    @DisplayName("POST /profile/{id}/image - 400 si erreur upload")
    void uploadProfileImage_error_returns400() throws Exception {
        doThrow(new RuntimeException("Upload failed")).when(userService).setProfileImage(eq(99L), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "image-data".getBytes());

        mockMvc.perform(multipart("/api/auth/profile/99/image").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
