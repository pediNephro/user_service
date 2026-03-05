package esprit.microservice1.config;

import esprit.microservice1.entities.Role;
import esprit.microservice1.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        // Check if roles already exist
        if (roleRepository.count() == 0) {
            // Create ADMIN role
            Role adminRole = new Role();
            adminRole.setName("ADMIN");
            adminRole.setDescription("Administrator role with full access");
            roleRepository.save(adminRole);

            // Create NURSE role
            Role nurseRole = new Role();
            nurseRole.setName("NURSE");
            nurseRole.setDescription("Nurse role for healthcare professionals");
            roleRepository.save(nurseRole);

            // Create DOCTOR role
            Role doctorRole = new Role();
            doctorRole.setName("DOCTOR");
            doctorRole.setDescription("Doctor role for medical practitioners");
            roleRepository.save(doctorRole);

            System.out.println("Roles created successfully: ADMIN, NURSE, DOCTOR");
        } else {
            System.out.println("Roles already exist in the database");
        }
    }
}
