package com.anand.instagram.downloader.app.service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReelExtractorServiceImpl implements ReelExtractorService {

	private final Browser browser;

	// Do NOT block "media" because the video itself can be a media request.
	private static final Set<String> BLOCKED_TYPES = Set.of("image", "stylesheet", "font");

	// Do NOT block instagram.com because we need Instagram's own requests.
	private static final Set<String> BLOCKED_KEYWORDS = Set.of("analytics", "telemetry", "logging", "metrics");

	public ReelExtractorServiceImpl(Browser browser) {
		this.browser = browser;
	}

	@Override
	public String extractVideoUrl(String reelUrl) {

		AtomicReference<String> foundVideoUrl = new AtomicReference<>(null);

		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
				.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " + "AppleWebKit/537.36 (KHTML, like Gecko) "
						+ "Chrome/124.0.0.0 Safari/537.36")
				.setViewportSize(1280, 720);

		try (BrowserContext context = browser.newContext(contextOptions); Page page = context.newPage()) {

			// ============================================================
			// REQUEST LOGGING
			// ============================================================

			page.onRequest(request -> {
				log.info("REQUEST [" + request.resourceType() + "] -> " + request.url());
			});

			// ============================================================
			// FAILED REQUEST LOGGING
			// ============================================================

			page.onRequestFailed(request -> {
				log.info("REQUEST FAILED -> " + request.url() + " | " + request.failure());
			});

			// ============================================================
			// RESPONSE LOGGING + VIDEO DETECTION
			// ============================================================

			page.onResponse(response -> {

				String url = response.url();

				log.info("RESPONSE [" + response.status() + "] " + response.request().resourceType() + " -> " + url);

				String lowerUrl = url.toLowerCase();

				if (lowerUrl.contains(".mp4") || lowerUrl.contains("video.xx.fbcdn.net")
						|| lowerUrl.contains("scontent")) {

					log.info("========== VIDEO FOUND ==========");

					log.info(url);

					foundVideoUrl.compareAndSet(null, url);
				}
			});

			// ============================================================
			// RESOURCE BLOCKING
			// ============================================================

			page.route("**/*", route -> {

				String resourceType = route.request().resourceType();

				String url = route.request().url().toLowerCase();

				// Block images, CSS and fonts.
				// IMPORTANT: media is NOT blocked.
				if (BLOCKED_TYPES.contains(resourceType)) {
					route.abort();
					return;
				}

				// Block tracking/analytics requests.
				for (String keyword : BLOCKED_KEYWORDS) {

					if (url.contains(keyword)) {
						route.abort();
						return;
					}
				}

				// Everything else proceeds normally.
				route.resume();
			});

			// ============================================================
			// NAVIGATION
			// ============================================================

			log.info("================================================");

			log.info("Navigating to: " + reelUrl);

			log.info("================================================");

			page.navigate(reelUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

			// ============================================================
			// PAGE INFORMATION
			// ============================================================

			log.info("FINAL URL: " + page.url());

			log.info("PAGE TITLE: " + page.title());

			// ============================================================
			// WAIT FOR VIDEO REQUEST
			// ============================================================

			for (int i = 0; i < 30; i++) {

				if (foundVideoUrl.get() != null) {
					break;
				}

				page.waitForTimeout(500);
			}

		} catch (Exception e) {

			throw new RuntimeException("Failed to scan Instagram network streams: " + e.getMessage(), e);
		}

		String videoUrl = foundVideoUrl.get();

		if (videoUrl == null) {
			throw new RuntimeException("No video stream found for URL: " + reelUrl);
		}

		return videoUrl;
	}
}