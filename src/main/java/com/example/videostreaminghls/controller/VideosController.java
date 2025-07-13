package com.example.videostreaminghls.controller;

import com.example.videostreaminghls.entity.Videos;
import com.example.videostreaminghls.service.VideosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "*")
public class VideosController {

    private static final Logger log = LoggerFactory.getLogger(VideosController.class);

    private final VideosService videosService;

    public VideosController(VideosService videosService) {
        this.videosService = videosService;
        log.info("VideosController initialized");
    }

    @Value("${app.video.hls-dir}")
    private String hlsDir;

    @GetMapping
    public ResponseEntity<List<Videos>> getAllVideoss() {
        log.info("Fetching all videos");
        List<Videos> videos = videosService.listAllVideos();
        log.info("Found {} videos", videos.size());
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Videos> getVideosById(@PathVariable Long id) {
        log.info("Fetching video with id: {}", id);
        Optional<Videos> video = videosService.getVideoById(id);
        if (video.isPresent()) {
            log.info("Found video: {}", video.get().getId());
            return ResponseEntity.ok(video.get());
        }
        log.warn("Video not found with id: {}", id);
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideos(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title) {
        log.info("Uploading video with title: {}, file size: {}", title, file.getSize());

        if (file.isEmpty()) {
            log.warn("Empty file received for upload");
            return ResponseEntity.badRequest()
                    .body("Please select a video file to upload");
        }

        try {
            Videos video = videosService.saveVideo(file, title);
            log.info("Successfully uploaded video with id: {}", video.getId());
            return ResponseEntity.ok(video);
        } catch (IOException e) {
            log.error("Failed to upload video: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload video: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVideos(@PathVariable Long id) {
        log.info("Attempting to delete video with id: {}", id);
        videosService.deleteVideoById(id);
        log.info("Successfully deleted video with id: {}", id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/playlist.m3u8")
    public ResponseEntity<Resource> getHLSPlaylist(@PathVariable Long id) {
        log.info("Fetching HLS playlist for video id: {}", id);
        try {
            Optional<Videos> videoOpt = videosService.getVideoById(id);
            if (videoOpt.isEmpty()) {
                log.warn("Video not found for playlist request, id: {}", id);
                return ResponseEntity.notFound().build();
            }

            Videos video = videoOpt.get();
            if (video.getHstFilePath() == null) {
                log.warn("HLS file path is null for video id: {}", id);
                return ResponseEntity.notFound().build();
            }

            Path playlistPath = Paths.get(video.getHstFilePath());
            log.debug("Playlist path: {}", playlistPath);
            Resource resource = new UrlResource(playlistPath.toUri());

            if (resource.exists()) {
                log.info("Serving playlist for video id: {}", id);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .body(resource);
            } else {
                log.warn("Playlist file not found at path: {}", playlistPath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("Error accessing playlist file for video id: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Original segment endpoint (with /segments/ path)
    @GetMapping("/{id}/segments/{segmentName}")
    public ResponseEntity<Resource> getHLSSegment(
            @PathVariable Long id,
            @PathVariable String segmentName) {
        log.info("Fetching segment {} for video id: {} via /segments/ path", segmentName, id);
        return serveSegment(id, segmentName);
    }

    // Additional segment endpoint (direct path - matching your log errors)
    @GetMapping("/{id}/segment_{segmentNumber}.ts")
    public ResponseEntity<Resource> getHLSSegmentDirect(
            @PathVariable Long id,
            @PathVariable String segmentNumber) {
        String segmentName = "segment_" + segmentNumber + ".ts";
        log.info("Fetching segment {} for video id: {} via direct path", segmentName, id);
        return serveSegment(id, segmentName);
    }

    // Generic segment endpoint for any file in the video directory
    @GetMapping("/{id}/{fileName}")
    public ResponseEntity<Resource> getHLSFile(
            @PathVariable Long id,
            @PathVariable String fileName) {
        log.info("Fetching file {} for video id: {} via generic path", fileName, id);

        // Security check - prevent directory traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            log.warn("Invalid file name requested: {}", fileName);
            return ResponseEntity.badRequest().build();
        }

        return serveSegment(id, fileName);
    }

    private ResponseEntity<Resource> serveSegment(Long id, String fileName) {
        try {
            // Try multiple possible locations for the segment file
            Path segmentPath = null;
            Resource resource = null;

            // Option 1: Using hlsDir + video id + filename
            segmentPath = Paths.get(hlsDir, id.toString(), fileName);
            log.debug("Trying segment path 1: {}", segmentPath);
            resource = new UrlResource(segmentPath.toUri());

            if (!resource.exists()) {
                // Option 2: Get the video's HLS directory from database
                Optional<Videos> videoOpt = videosService.getVideoById(id);
                if (videoOpt.isPresent()) {
                    Videos video = videoOpt.get();
                    if (video.getHstFilePath() != null) {
                        Path playlistPath = Paths.get(video.getHstFilePath());
                        Path videoDir = playlistPath.getParent();
                        segmentPath = videoDir.resolve(fileName);
                        log.debug("Trying segment path 2: {}", segmentPath);
                        resource = new UrlResource(segmentPath.toUri());
                    }
                }
            }

            if (resource != null && resource.exists()) {
                log.info("Serving segment {} for video {} from path: {}", fileName, id, segmentPath);

                // Determine content type
                String contentType = determineContentType(fileName);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                        .body(resource);
            } else {
                log.warn("Segment not found: {} for video: {}, tried path: {}", fileName, id, segmentPath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("Error accessing segment file: {} for video: {}", fileName, id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String determineContentType(String fileName) {
        if (fileName.endsWith(".ts")) {
            return "video/MP2T";
        } else if (fileName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        } else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else {
            return "application/octet-stream";
        }
    }


}