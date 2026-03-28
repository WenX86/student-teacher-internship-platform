package com.internship.platform.service;

import com.internship.platform.common.BizException;
import com.internship.platform.util.IdGenerator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LocalFileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "txt"
    );

    private final Path storageRoot;
    private final long maxSizeBytes;

    public LocalFileStorageService(
            @Value("${app.file-storage.root:./storage/uploads}") String storageRoot,
            @Value("${app.file-storage.max-size-bytes:10485760}") long maxSizeBytes
    ) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("初始化附件目录失败", exception);
        }
    }

    public Map<String, Object> store(MultipartFile file) {
        validate(file);

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String storedName = IdGenerator.nextId("file") + (extension.isBlank() ? "" : "." + extension);
        Path target = storageRoot.resolve(storedName).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new BizException("附件路径不合法");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BizException("保存附件失败");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", storedName);
        payload.put("name", originalName);
        payload.put("storedName", storedName);
        payload.put("size", file.getSize());
        payload.put("contentType", file.getContentType());
        payload.put("uploadedAt", LocalDateTime.now());
        payload.put("downloadUrl", "/files/" + storedName);
        return payload;
    }

    public Resource loadAsResource(String storedName) {
        Path target = resolveStoredPath(storedName);
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BizException("附件不存在或无法读取");
            }
            return resource;
        } catch (IOException exception) {
            throw new BizException("附件不存在或无法读取");
        }
    }

    public MediaType resolveMediaType(String storedName) {
        return MediaTypeFactory.getMediaType(storedName).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的附件");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BizException("单个附件不能超过 10MB");
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException("当前仅支持 pdf、doc、docx、xls、xlsx、jpg、jpeg、png、txt 格式附件");
        }
    }

    private Path resolveStoredPath(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
            throw new BizException("附件标识不合法");
        }
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BizException("附件路径不合法");
        }
        return target;
    }

    private String sanitizeOriginalName(String originalFilename) {
        String fileName = StringUtils.getFilename(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
        if (fileName == null || fileName.isBlank()) {
            return "附件.txt";
        }
        return fileName.replaceAll("[^0-9A-Za-z._\u4e00-\u9fa5-]", "_");
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
