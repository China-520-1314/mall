package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.service.CommentImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentImageServiceImpl implements CommentImageService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8000;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png"
    );

    private final Path storageDirectory;

    public CommentImageServiceImpl(@Value("${file.upload.comment-dir}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            Asserts.fail("请选择评价图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            Asserts.fail("单张图片不能超过5MB");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            Asserts.fail("仅支持JPG和PNG图片");
        }

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                Asserts.fail("图片文件无效或尺寸过大");
            }
        } catch (IOException exception) {
            Asserts.fail("无法读取图片文件");
        }

        try {
            Files.createDirectories(storageDirectory);
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path destination = storageDirectory.resolve(filename).normalize();
            if (!destination.startsWith(storageDirectory)) {
                Asserts.fail("图片存储路径无效");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (IOException exception) {
            Asserts.fail("评价图片保存失败");
            return null;
        }
    }

    @Override
    public Path getStorageDirectory() {
        return storageDirectory;
    }
}
