package esprit.microservice1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String roleName;
    private Long roleId;
    private String token;
    private String status;
}
