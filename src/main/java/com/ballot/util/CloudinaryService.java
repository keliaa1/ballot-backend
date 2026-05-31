package com.ballot.util;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Abstracts all Cloudinary upload/delete operations.
 * Stores public IDs for later deletion and returns secure HTTPS URLs.
 */
@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public record UploadResult(String secureUrl, String publicId) {}

    /**
     * Uploads an image to the given Cloudinary folder.
     */
    public UploadResult uploadImage(MultipartFile file, String folder) throws IOException {
        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image",
                        "overwrite", true,
                        "quality", "auto",
                        "fetch_format", "auto"
                ));
        String secureUrl = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");
        log.info("Uploaded image to Cloudinary: publicId={}", publicId);
        return new UploadResult(secureUrl, publicId);
    }

    /**
     * Deletes an image by its Cloudinary public ID.
     */
    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted Cloudinary image: publicId={}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete Cloudinary image: publicId={}, error={}", publicId, e.getMessage());
        }
    }
}
