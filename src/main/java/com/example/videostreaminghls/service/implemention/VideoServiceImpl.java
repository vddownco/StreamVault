package com.example.videostreaminghls.service.implemention;

import com.example.videostreaminghls.entity.Videos;
import com.example.videostreaminghls.repository.VideoRepo;
import com.example.videostreaminghls.service.VideosService;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 */
@Service
public class VideoServiceImpl implements VideosService {

    private final VideoRepo videoRepo;

    @Value("${app.video.upload-dir}")
    private String uploadDir; // Directory where uploaded videos are stored

    @Value("${app.video.hls-dir}")
    private String hlsDir; // Directory where HLS files are stored

    @Value("${app.ffmpeg.path}")
    private String ffmpegPath; // Path to the FFmpeg executable

    /**
     * Constructor for VideoServiceImpl.
     *
     * @param videoRepo Repository for managing video entities.
     */
    public VideoServiceImpl(VideoRepo videoRepo) {
        this.videoRepo = videoRepo;
    }

    /**
     * Retrieves a list of all videos, ordered by creation date in descending order.
     *
     * @return List of videos.
     */
    @Override
    public List<Videos> listAllVideos() {
        return videoRepo.findByOrderByCreatedAtDesc();
    }

    /**
     * Retrieves a video by its ID.
     *
     * @param id The ID of the video.
     * @return An Optional containing the video if found, or empty if not.
     */
    @Override
    public Optional<Videos> getVideoById(Long id) {
        return videoRepo.findById(id);
    }

    /**
     * Extracts the file extension from a given filename.
     *
     * @param filename The name of the file.
     * @return The file extension.
     */
    private String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf('.'));
    }

    /**
     * Saves a video file to the upload directory and initiates asynchronous processing.
     *
     * @param file  The video file to save.
     * @param title The title of the video.
     * @return The saved video entity.
     * @throws IOException If an error occurs during file saving.
     */
    @Override
    public Videos saveVideo(MultipartFile file, String title) throws IOException {
        // Ensure the upload directory exists
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        Path uploadPath = Path.of(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), uploadPath, StandardCopyOption.REPLACE_EXISTING);
        Videos video = new Videos(
                title,
                originalFileName,
                uploadPath.toString(),
                file.getContentType(),
                String.valueOf(file.getSize())
        );
        video = videoRepo.save(video);

        processVideoAsync(video);
        return video;
    }

    /**
     * Asynchronously processes a video by converting it to HLS format.
     *
     * @param video The video to process.
     */
    private void processVideoAsync(Videos video) {
        CompletableFuture.runAsync(() -> {
            try {
                video.setProcessingStatus(Videos.ProcessingStatus.PROCESSING);
                videoRepo.save(video);

                convertToHLS(video);
                video.setProcessingStatus(Videos.ProcessingStatus.COMPLETED);
                videoRepo.save(video);
            } catch (Exception e) {
                video.setProcessingStatus(Videos.ProcessingStatus.FAILED);
                videoRepo.save(video);
                e.printStackTrace();
            }
        });
    }

    /**
     * Converts a video to HLS format using FFmpeg.
     *
     * @param video The video to convert.
     * @throws IOException If an error occurs during conversion.
     */
    private void convertToHLS(Videos video) throws IOException {
        // Use the correct paths for both FFmpeg and FFprobe
        FFmpeg ffmpeg = new FFmpeg("C:/Users/Isthifa/OneDrive/Desktop/flutterprojects/ffmpeg-master-latest-win64-gpl-shared/bin/ffmpeg.exe");
        FFprobe ffprobe = new FFprobe("C:/Users/Isthifa/OneDrive/Desktop/flutterprojects/ffmpeg-master-latest-win64-gpl-shared/bin/ffprobe.exe");

        // Get video information
        FFmpegProbeResult probeResult = ffprobe.probe(video.getFilePath());
        double duration = probeResult.getFormat().duration;
        video.setDuration(String.valueOf(duration));

        // Create HLS directory for this video
        String videoId = video.getId().toString();
        Path hlsVideoDir = Paths.get(hlsDir, videoId);
        Files.createDirectories(hlsVideoDir);

        // HLS output path
        String hlsPlaylistPath = hlsVideoDir.resolve("playlist.m3u8").toString();

        // Build FFmpeg command for HLS conversion
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(video.getFilePath())
                .overrideOutputFiles(true)
                .addOutput(hlsPlaylistPath)
                .setFormat("hls")
                .addExtraArgs("-hls_time", "10")
                .addExtraArgs("-hls_list_size", "0")
                .addExtraArgs("-hls_segment_filename", hlsVideoDir.resolve("segment_%03d.ts").toString())
                .done();

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();

        // Update video with HLS path
        video.setHstFilePath(hlsPlaylistPath);
        videoRepo.save(video);
    }

    /**
     * Deletes a video by its ID, including its associated files.
     *
     * @param id The ID of the video to delete.
     */
    @Override
    public void deleteVideoById(Long id) {
        Optional<Videos> videoOpt = videoRepo.findById(id);
        if (videoOpt.isPresent()) {
            Videos video = videoOpt.get();

            // Delete files
            try {
                Files.deleteIfExists(Paths.get(video.getFilePath()));
                if (video.getHstFilePath() != null) {
                    Path hlsDir = Paths.get(video.getHstFilePath()).getParent();
                    Files.walk(hlsDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Delete from database
            videoRepo.deleteById(id);
        }
    }
}