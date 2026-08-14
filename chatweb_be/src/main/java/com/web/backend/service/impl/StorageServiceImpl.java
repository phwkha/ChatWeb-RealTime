package com.web.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "STORAGE-SERVICE")
public class StorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    @Value("${app.upload.avatar.max-size}")
    private Long maxAvatarSize;

    @Value("${app.upload.max-size-video}")
    private Long maxVideoSize;

    @Value("${app.upload.max-size-image}")
    private Long maxImageSize;

    private static final String FOLDER_STRING = "folder";
    private static final String IMAGES_STRING = "images";
    private static final String PUBLIC_ID_STRING = "public_id";
    private static final String RESOURCE_TYPE_STRING = "resource_type";
    private static final String SECURE_URL_STRING = "secure_url";
    private static final String VIDEOS_STRING = "videos";

    private static final String IMAGE_STRING = "image";
    private static final String RAW_STRING = "raw";

    private static final String AVATARS_STRING = "avatars";

    private static final String ERROR_STORAGE_INVALID_FORMAT_STRING = "error.storage.invalid_format";
    private static final String ERROR_STORAGE_FILE_TOO_LARGE_STRING = "error.storage.file_too_large";
    private static final String ERROR_STORAGE_UPLOAD_FAILED_STRING = "error.storage.upload_failed";
    private static final String ERROR_STORAGE_EMPTY_FILE_STRING = "error.storage.empty_file";

    @Override
    public String uploadAvatar(MultipartFile avatar) {
        return uploadFile(avatar, AVATARS_STRING, maxAvatarSize, IMAGE_STRING);
    }

    @Override
    public String upLoadImage(MultipartFile image) {
        return uploadFile(image, IMAGES_STRING, maxImageSize, RAW_STRING);
    }

    @Override
    public String uploadVideo(MultipartFile video) {
        return uploadFile(video, VIDEOS_STRING, maxVideoSize, RAW_STRING);
    }

    private String uploadFile(MultipartFile file, String folder, Long maxSize, String resourceType) {
        try {
            if (file.isEmpty()) {
                throw new InvalidDataException(Translator.tolocale(ERROR_STORAGE_EMPTY_FILE_STRING));
            }

            if (!resourceType.equals(RAW_STRING)) {
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith(resourceType + "/")) {
                    throw new InvalidDataException(
                            Translator.tolocale(ERROR_STORAGE_INVALID_FORMAT_STRING, resourceType));
                }
            }

            if (file.getSize() > maxSize) {
                long sizeInMb = maxSize / (1024 * 1024);
                throw new InvalidDataException(Translator.tolocale(ERROR_STORAGE_FILE_TOO_LARGE_STRING, sizeInMb));
            }

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            FOLDER_STRING, folder,
                            PUBLIC_ID_STRING, UUID.randomUUID().toString(),
                            RESOURCE_TYPE_STRING, resourceType));

            String url = (String) uploadResult.get(SECURE_URL_STRING);
            log.debug("Uploaded file successfully [type='{}', folder='{}', url='{}']", resourceType, folder, url);
            return url;

        } catch (IOException e) {
            log.error("Failed to upload file [type='{}', folder='{}']", resourceType, folder, e);
            throw new InvalidDataException(Translator.tolocale(ERROR_STORAGE_UPLOAD_FAILED_STRING, e.getMessage()));
        }
    }

    @Override
    @Async
    public void delete(String url, String folder) {
        String publicId = null;
        try {
            if (url == null || url.isEmpty())
                return;

            String resourceType = folder.equals(AVATARS_STRING) ? IMAGE_STRING : RAW_STRING;
            publicId = extractPublicId(url, folder);

            if (publicId != null) {
                cloudinary.uploader().destroy(publicId,
                        ObjectUtils.asMap(RESOURCE_TYPE_STRING, resourceType));
                log.debug("Deleted file from Cloudinary [publicId='{}', type='{}']", publicId, resourceType);
            }
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary [publicId='{}']", publicId, e);
        }
    }

    private String extractPublicId(String url, String folder) {
        try {
            int startIndex = url.lastIndexOf(folder + "/");
            int endIndex = url.lastIndexOf(".");
            if (startIndex != -1 && endIndex != -1) {
                return url.substring(startIndex, endIndex);
            }
        } catch (Exception e) {
            log.warn("Failed to extract publicId from url: '{}'", url);
        }
        return null;
    }
}