# 🎥 Video Streaming Platform with HLS (Spring Boot + FFmpeg)
#StreamVault
A Spring Boot application that handles video uploads, converts them to HLS format for streaming, and provides a REST API for video management.

---

## 🚀 Features

- 📁 Upload videos and convert them to **HLS format**
- 🧵 **Asynchronous video processing** using `CompletableFuture`
- 🎬 Serves `.m3u8` playlists and `.ts` segments
- 🔗 RESTful API for video management
- ⚙️ FFmpeg-based video processing
- ♻️ Automatic cleanup on deletion
- 🌐 CORS support
- 💾 Metadata storage (duration, size, status)

---

## 🧱 Project Structure
```markdown
src/main/
├── java/
│   └── com/example/videostreaminghls/
│       ├── controller/
│       │   └── VideosController.java       # REST endpoints
│       ├── service/
│       │   └── implementation/
│       │       └── VideoServiceImpl.java   # Business logic
│       ├── entity/
│       │   └── Videos.java                 # JPA entity
│       └── repository/
│           └── VideoRepo.java             # Data access layer
└── resources/
    └── application.yml                    # Configuration file
```

## Features

- Video upload and storage
- Asynchronous video processing
- HLS (HTTP Live Streaming) conversion using FFmpeg
- RESTful API endpoints
- Video metadata management
- File cleanup on deletion

## Requirements

- Java 17+
- Maven
- FFmpeg and FFprobe binaries
- MySQL/PostgreSQL database
- Spring Boot 3.x

## 📥 Video Upload & Processing Flow

### 1. Upload
- Endpoint: `POST /api/videos/upload`
- Upload videos with a title
- Stored with a unique UUID
- Status set to `PENDING`

### 2. Conversion
- Async processing via `CompletableFuture`
- Converts video to **HLS** format using **FFmpeg**
- Generates `.m3u8` playlist and `.ts` segments
- Status updated: `PROCESSING → COMPLETED` or `FAILED`

### 3. Streaming
- HLS Playlist: `GET /api/videos/{id}/playlist.m3u8`
- Segment Access: `GET /api/videos/{id}/segments/{name}`
- Returns correct **MIME types** for HLS

---

## 📂 Configuration (application.yml)

```yaml
app:
  video:
    upload-dir: uploads/videos     # Directory for original video files
    hls-dir: uploads/hls          # Directory for HLS segments and playlists
  ffmpeg:
    path: [path-to-ffmpeg.exe]     # FFmpeg executable path 
    ffprobe: [path-to-ffprobe.exe] # FFprobe executable path

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: your_username
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update

## API Endpoints

- `GET    /api/videos`  
  List all videos

- `GET    /api/videos/{id}`  
  Get video details

- `POST   /api/videos/upload`  
  Upload new video

- `DELETE /api/videos/{id}`  
  Delete video

- `GET    /api/videos/{id}/playlist.m3u8`  
  Get HLS playlist

- `GET    /api/videos/{id}/segments/{name}`  
  Get video segments


## 📸 SS

<img width="1284" height="660" alt="image" src="https://github.com/user-attachments/assets/77853583-b274-435d-81c2-636ef280e301" />
