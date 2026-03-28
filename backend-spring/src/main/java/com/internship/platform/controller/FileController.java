package com.internship.platform.controller;

import com.internship.platform.common.ApiResponse;
import com.internship.platform.service.LocalFileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final LocalFileStorageService localFileStorageService;

    public FileController(LocalFileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(localFileStorageService.store(file));
    }

    @GetMapping("/{storedName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String storedName) {
        Resource resource = localFileStorageService.loadAsResource(storedName);
        return ResponseEntity.ok()
                .contentType(localFileStorageService.resolveMediaType(storedName))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(storedName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }
}
