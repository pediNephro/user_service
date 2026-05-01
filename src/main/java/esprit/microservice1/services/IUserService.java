package esprit.microservice1.services;

import esprit.microservice1.dto.AuthResponse;
import esprit.microservice1.dto.LoginRequest;
import esprit.microservice1.dto.RegisterRequest;
import esprit.microservice1.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface IUserService {
    AuthResponse register(RegisterRequest registerRequest);

    AuthResponse login(LoginRequest loginRequest);

    UserResponse getMe(String token);

    void logout(String token);

    List<UserResponse> getAllUsers();

    UserResponse updateProfile(Long id, UserResponse updateRequest);

    void deleteUser(Long id);

    void requestPasswordReset(String email);

    void setProfileImage(Long userId, MultipartFile imageFile);

    UserResponse getUserById(Long id);

    UserResponse archiveUser(Long id);
}
