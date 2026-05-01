package esprit.microservice1.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    private Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret));
    }

    /**
     * Upload a profile image to Cloudinary
     * 
     * @param file   The image file to upload
     * @param userId The user ID for organizing the image folder
     * @return The public URL of the uploaded image
     */
    public String uploadProfileImage(MultipartFile file, Long userId) {
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }

            // Upload to Cloudinary with folder structure
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "users/" + userId + "/profile",
                            "resource_type", "auto",
                            "public_id", "profile_image",
                            "overwrite", true,
                            "quality", "auto",
                            "fetch_format", "auto"));

            String url = (String) uploadResult.get("secure_url");
            log.info("Image uploaded successfully for user: {}, URL: {}", userId, url);
            return url;

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error uploading file: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a profile image from Cloudinary
     * 
     * @param userId The user ID
     */
    public void deleteProfileImage(Long userId) {
        try {
            cloudinary.uploader().destroy(
                    "users/" + userId + "/profile/profile_image",
                    ObjectUtils.asMap("resource_type", "image"));
            log.info("Image deleted successfully for user: {}", userId);
        } catch (IOException e) {
            log.error("Error deleting image from Cloudinary: {}", e.getMessage());
            throw new RuntimeException("Error deleting file: " + e.getMessage(), e);
        }
    }
}
