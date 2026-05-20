package com.nanzzang.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
public class FileUploadController {

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping("/images")
    public ResponseEntity<?> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        if (files.size() > 3) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미지는 최대 3장까지 업로드할 수 있습니다."));
        }

        List<String> urls = new ArrayList<>();
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                String contentType = file.getContentType();
                if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "JPG, PNG, GIF, WEBP 형식만 업로드 가능합니다."));
                }
                if (file.getSize() > MAX_FILE_SIZE) {
                    return ResponseEntity.badRequest().body(Map.of("message", "파일 크기는 10MB를 초과할 수 없습니다."));
                }

                String ext = getExtension(file.getOriginalFilename());
                String filename = UUID.randomUUID() + ext;
                Path target = uploadPath.resolve(filename);
                file.transferTo(target);

                urls.add("/uploads/" + filename);
                log.info("이미지 업로드: {}", filename);
            }
        } catch (IOException e) {
            log.error("이미지 업로드 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "파일 저장에 실패했습니다."));
        }

        return ResponseEntity.ok(Map.of("urls", urls));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}
