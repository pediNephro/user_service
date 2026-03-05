package esprit.microservice1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phoneNumber;
    private String roleName; // ADMIN, DOCTOR, NURSE
}
