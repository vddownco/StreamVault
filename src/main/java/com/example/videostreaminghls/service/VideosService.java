package com.example.videostreaminghls.service;

import com.example.videostreaminghls.entity.Videos;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface VideosService {

    // Method to save a video entity
    List<Videos> listAllVideos();

    Optional<Videos> getVideoById(Long id);

    // Method to save a video entity
    Videos saveVideo(MultipartFile file,String title) throws IOException;

    void deleteVideoById(Long id);
}
