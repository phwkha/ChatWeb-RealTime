package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.exception.custom.InvalidDataException;
import com.web.backend.service.impl.StorageServiceImpl;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    @Mock
    private MultipartFile file;

    @InjectMocks
    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Error Message");
        Translator.setStaticMessageSource(messageSource);

        ReflectionTestUtils.setField(storageService, "maxAvatarSize", 5000000L); // 5MB
        ReflectionTestUtils.setField(storageService, "maxVideoSize", 50000000L); // 50MB
        ReflectionTestUtils.setField(storageService, "maxImageSize", 10000000L); // 10MB

        lenient().when(cloudinary.uploader()).thenReturn(uploader);
    }

    @Test
    void testUploadAvatar_Success() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1000L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getBytes()).thenReturn(new byte[] { 1, 2, 3 });

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "http://image.jpg"));

        String result = storageService.uploadAvatar(file);
        assertEquals("http://image.jpg", result);
    }

    @Test
    void testUploadAvatar_EmptyFile() {
        when(file.isEmpty()).thenReturn(true);
        assertThrows(InvalidDataException.class, () -> storageService.uploadAvatar(file));
    }

    @Test
    void testUploadAvatar_InvalidFormat() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");
        assertThrows(InvalidDataException.class, () -> storageService.uploadAvatar(file));
    }

    @Test
    void testUploadAvatar_TooLarge() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(6000000L); // 6MB > 5MB max
        assertThrows(InvalidDataException.class, () -> storageService.uploadAvatar(file));
    }

    @Test
    void testUploadAvatar_IOException() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1000L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getBytes()).thenThrow(new IOException("test io exception"));

        assertThrows(RuntimeException.class, () -> storageService.uploadAvatar(file));
    }

    @Test
    void testUploadImage_Success() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1000L);
        // raw upload does not check content type
        when(file.getBytes()).thenReturn(new byte[] { 1 });
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "http://raw-image.jpg"));

        String result = storageService.upLoadImage(file);
        assertEquals("http://raw-image.jpg", result);
    }

    @Test
    void testUploadVideo_Success() throws Exception {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1000L);
        when(file.getBytes()).thenReturn(new byte[] { 1 });
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "http://video.mp4"));

        String result = storageService.uploadVideo(file);
        assertEquals("http://video.mp4", result);
    }

    @Test
    void testDelete_Success() throws Exception {
        String url = "http://res.cloudinary.com/demo/image/upload/v1234/avatars/sample.jpg";
        when(uploader.destroy(eq("avatars/sample"), anyMap())).thenReturn(Map.of("result", "ok"));

        storageService.delete(url, "avatars");
        verify(uploader).destroy(eq("avatars/sample"), anyMap());
    }

    @Test
    void testDelete_RawFolder_Success() throws Exception {
        String url = "http://res.cloudinary.com/demo/raw/upload/v1234/images/sample.jpg";
        when(uploader.destroy(eq("images/sample"), anyMap())).thenReturn(Map.of("result", "ok"));

        storageService.delete(url, "images");
        verify(uploader).destroy(eq("images/sample"), anyMap());
    }

    @Test
    void testDelete_NullOrEmpty() throws Exception {
        storageService.delete(null, "avatars");
        storageService.delete("", "avatars");
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @Test
    void testDelete_ExtractFails_Ignores() throws Exception {
        String url = "invalid_url";
        storageService.delete(url, "avatars");
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @Test
    void testDelete_IOException() throws Exception {
        String url = "http://res.cloudinary.com/demo/image/upload/v1234/avatars/sample.jpg";
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IOException("delete error"));

        assertDoesNotThrow(() -> storageService.delete(url, "avatars"));
    }
}
