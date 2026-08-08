package com.anand.instagram.downloader.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.anand.instagram.downloader.app.dto.DownloadRequest;
import com.anand.instagram.downloader.app.dto.DownloadResponse;
import com.anand.instagram.downloader.app.service.ReelExtractorService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/api")
@Slf4j
@CrossOrigin(origins = "https://insta-scrapper-app.vercel.app")
public class DownloadController {

	private final ReelExtractorService extractorService;

	public DownloadController(ReelExtractorService extractorService) {
		this.extractorService = extractorService;
	}

	@PostMapping("/download")
	@RateLimiter(name = "backendRateLimiter", fallbackMethod = "rateLimiterFallback")
	public ResponseEntity<?> downloadReel(@RequestBody DownloadRequest request) {
		if (request.getUrl() == null || request.getUrl().isBlank()) {
			return ResponseEntity.badRequest().body(new DownloadResponse(null, "URL query string is empty"));
		}

		try {
			log.info("Request received for URL [" + request.getUrl() + "]");

			// Extract video URL from Instagram
			String extractedMp4Url = extractorService.extractVideoUrl(request.getUrl());

			if (extractedMp4Url == null) {
				log.error("Failed to capture MP4 stream from page network events for URL : [" + request.getUrl() + "]");
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new DownloadResponse(null, "Failed to capture MP4 stream from page network events"));
			}

			// Fetch the actual video file
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest httpRequest = HttpRequest.newBuilder().uri(new URI(extractedMp4Url))
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").GET().build();

			HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() == 200) {
				return ResponseEntity.ok().header("Content-Type", "video/mp4")
						.header("Content-Disposition", "attachment; filename=instagram-video.mp4")
						.body(response.body());
			} else {
				return ResponseEntity.status(response.statusCode())
						.body(new DownloadResponse(null, "Failed to download video"));
			}

		} catch (Exception e) {
			log.error(e.getLocalizedMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new DownloadResponse(null, "Error: " + e.getMessage()));
		}
	}

	// Fallback method must accept the same parameters plus a Throwable argument
	public ResponseEntity<DownloadResponse> rateLimiterFallback(Throwable t) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new DownloadResponse(null, "Too many requests! Please wait before trying again."));
	}
}
