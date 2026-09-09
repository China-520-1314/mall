package com.macro.mall.portal.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/** 评价图片本地存储服务。 */
public interface CommentImageService {
    String store(MultipartFile file);

    Path getStorageDirectory();
}
