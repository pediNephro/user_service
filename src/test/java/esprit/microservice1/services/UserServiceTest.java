package esprit.microservice1.services;

import esprit.microservice1.dto.AuthResponse;
import esprit.microservice1.dto.LoginRequest;
import esprit.microservice1.dto.RegisterRequest;
import esprit.microservice1.dto.UserResponse;
import esprit.microservice1.entities.Role;
import esprit.microservice1.entities.User;
import esprit.microservice1.repositories.RoleRepository;
import esprit.microservice1.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - Tests unitaires")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private KeycloakService keycloakService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private JwtDecoder jwtDecoder;

    @InjectMocks private UserService userService;

    private Role role;
    private User user;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(1L);
        role.setName("DOCTOR");

        user = new User();
        user.setId(1L);
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setEmail("alice@hospital.tn");
        user.setKeycloakId("kc-uuid-001");
        user.setRole(role);
        user.setStatus("ACTIVE");
    }

    // ─── register ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register - succès : nouvel utilisateur créé")
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Alice");
        req.setLastName("Martin");
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");
        req.setRoleName("DOCTOR");

        when(userRepository.existsByEmail("alice@hospital.tn")).thenReturn(false);
        when(keycloakService.userExists("alice@hospital.tn")).thenReturn(false);
        when(roleRepository.findByName("DOCTOR")).thenReturn(Optional.of(role));
        when(keycloakService.createUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-uuid-001");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(keycloakService.authenticateUser("alice@hospital.tn", "secret")).thenReturn("jwt-token");

        AuthResponse response = userService.register(req);

        assertThat(response.getEmail()).isEqualTo("alice@hospital.tn");
        assertThat(response.getRoleName()).isEqualTo("DOCTOR");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(userRepository).save(any(User.class));
        verify(keycloakService).createUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("register - échec : email déjà utilisé en base")
    void register_emailAlreadyInDatabase_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@hospital.tn");
        req.setRoleName("DOCTOR");

        when(userRepository.existsByEmail("alice@hospital.tn")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already exists");

        verify(keycloakService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("register - échec : utilisateur déjà dans Keycloak")
    void register_userAlreadyInKeycloak_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@hospital.tn");
        req.setRoleName("DOCTOR");

        when(userRepository.existsByEmail("alice@hospital.tn")).thenReturn(false);
        when(keycloakService.userExists("alice@hospital.tn")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("register - échec : rôle introuvable")
    void register_roleNotFound_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("alice@hospital.tn");
        req.setRoleName("UNKNOWN_ROLE");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(keycloakService.userExists(any())).thenReturn(false);
        when(roleRepository.findByName("UNKNOWN_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration failed");
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login - succès : credentials valides")
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");

        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setEmail("alice@hospital.tn");
        kcUser.setId("kc-uuid-001");

        when(userRepository.findByEmail("alice@hospital.tn")).thenReturn(Optional.of(user));
        when(keycloakService.getUserByEmail("alice@hospital.tn")).thenReturn(kcUser);
        when(keycloakService.authenticateUser("alice@hospital.tn", "secret")).thenReturn("jwt-token");

        AuthResponse response = userService.login(req);

        assertThat(response.getEmail()).isEqualTo("alice@hospital.tn");
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("login - échec : utilisateur inexistant en base")
    void login_userNotInDatabase_throwsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("unknown@hospital.tn");
        req.setPassword("secret");

        when(userRepository.findByEmail("unknown@hospital.tn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Login failed");
    }

    @Test
    @DisplayName("login - échec : compte archivé")
    void login_archivedAccount_throwsException() {
        user.setStatus("ARCHIVED");
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");

        when(userRepository.findByEmail("alice@hospital.tn")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("archived");
    }

    @Test
    @DisplayName("login - échec : utilisateur absent de Keycloak")
    void login_userNotInKeycloak_throwsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@hospital.tn");
        req.setPassword("secret");

        when(userRepository.findByEmail("alice@hospital.tn")).thenReturn(Optional.of(user));
        when(keycloakService.getUserByEmail("alice@hospital.tn")).thenReturn(null);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Login failed");
    }

    // ─── getMe ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMe - succès : token valide retourne l'utilisateur")
    void getMe_validToken_returnsUser() {
        Jwt jwt = mock(Jwt.class);
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("alice@hospital.tn");
        when(userRepository.findByEmail("alice@hospital.tn")).thenReturn(Optional.of(user));

        UserResponse response = userService.getMe("valid-token");

        assertThat(response.getEmail()).isEqualTo("alice@hospital.tn");
        assertThat(response.getFirstName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("getMe - échec : token sans claim email")
    void getMe_tokenWithoutEmail_throwsException() {
        Jwt jwt = mock(Jwt.class);
        when(jwtDecoder.decode("bad-token")).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn(null);

        assertThatThrownBy(() -> userService.getMe("bad-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unauthorized");
    }

    // ─── getAllUsers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers - retourne la liste complète")
    void getAllUsers_returnsList() {
        User user2 = new User();
        user2.setId(2L);
        user2.setEmail("bob@hospital.tn");
        user2.setFirstName("Bob");
        user2.setLastName("Dupont");
        user2.setRole(role);
        user2.setStatus("ACTIVE");

        when(userRepository.findAll()).thenReturn(List.of(user, user2));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("alice@hospital.tn", "bob@hospital.tn");
    }

    // ─── updateProfile ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile - met à jour prénom, nom et Keycloak")
    void updateProfile_success() {
        UserResponse req = new UserResponse();
        req.setFirstName("Alicia");
        req.setLastName("Martino");
        req.setPhoneNumber("+21612345678");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateProfile(1L, req);

        assertThat(result.getFirstName()).isEqualTo("Alicia");
        assertThat(result.getLastName()).isEqualTo("Martino");
        verify(keycloakService).updateUser("kc-uuid-001", "Alicia", "Martino");
    }

    @Test
    @DisplayName("updateProfile - échec : utilisateur introuvable")
    void updateProfile_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(99L, new UserResponse()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error updating profile");
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser - supprime en base et dans Keycloak")
    void deleteUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(keycloakService).deleteUser("kc-uuid-001");
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("deleteUser - supprime l'image Cloudinary si présente")
    void deleteUser_withProfileImage_deletesImageFirst() {
        user.setProfileImageUrl("https://cloudinary.com/img.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(cloudinaryService).deleteProfileImage(1L);
        verify(keycloakService).deleteUser("kc-uuid-001");
        verify(userRepository).delete(user);
    }

    // ─── archiveUser ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("archiveUser - ACTIVE → ARCHIVED")
    void archiveUser_activeToArchived() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.archiveUser(1L);

        assertThat(result.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("archiveUser - ARCHIVED → ACTIVE (réactivation)")
    void archiveUser_archivedToActive() {
        user.setStatus("ARCHIVED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.archiveUser(1L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    // ─── requestPasswordReset ─────────────────────────────────────────────────

    @Test
    @DisplayName("requestPasswordReset - envoie l'email via Keycloak")
    void requestPasswordReset_sendsEmail() {
        when(userRepository.findByEmail("alice@hospital.tn")).thenReturn(Optional.of(user));

        userService.requestPasswordReset("alice@hospital.tn");

        verify(keycloakService).sendPasswordResetEmail("kc-uuid-001");
    }

    @Test
    @DisplayName("requestPasswordReset - échec : email inconnu")
    void requestPasswordReset_unknownEmail_throwsException() {
        when(userRepository.findByEmail("ghost@hospital.tn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requestPasswordReset("ghost@hospital.tn"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error requesting password reset");
    }

    // ─── getUserById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById - retourne l'utilisateur existant")
    void getUserById_exists_returnsUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@hospital.tn");
    }

    @Test
    @DisplayName("getUserById - lève une exception si introuvable")
    void getUserById_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }
}
