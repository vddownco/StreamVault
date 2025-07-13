package com.example.videostreaminghls.repository;

import com.example.videostreaminghls.entity.Videos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepo extends JpaRepository<Videos, Long> {

    // Custom query methods can be defined here if needed
    // For example, to find videos by title:
    // List<Vidoes> findByTitleContaining(String title);
    List<Videos> findByProcessingStatus(Videos.ProcessingStatus status);

    @Query("SELECT v FROM Videos v WHERE v.title LIKE %:title%")
    List<Videos> findByTitleContaining(String title);

    List<Videos> findByOrderByCreatedAtDesc();
}
