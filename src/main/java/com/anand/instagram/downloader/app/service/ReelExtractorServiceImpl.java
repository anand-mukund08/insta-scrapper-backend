package com.anand.instagram.downloader.app.service;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.util.Set;

@Service
public class ReelExtractorServiceImpl implements ReelExtractorService {

	private final Browser browser;

	// Define the list of resource types we want to block completely
	private static final Set<String> BLOCKED_TYPES = Set.of("image", // Pictures, icons, avatars
			"stylesheet", // CSS files
			"font", // Custom typography files
			"media" // We abort standard media tags since we intercept the raw network stream URL
					// instead
	);

	// Define keywords found in tracking, telemetry, and analytics URLs
	private static final Set<String> BLOCKED_KEYWORDS = Set.of("analytics", "telemetry", "logging", "metrics",
			"://instagram.com");

	public ReelExtractorServiceImpl(Browser browser) {
		this.browser = browser;
	}

	@Override
	public String extractVideoUrl(String reelUrl) {
		AtomicReference<String> foundVideoUrl = new AtomicReference<>(null);

		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
				.setViewportSize(1280, 720);

		try (BrowserContext context = browser.newContext(contextOptions); Page page = context.newPage()) {

			// =================================================================
			// RESOURCE BLOCKLIST ROUTING (Must be set before page.navigate)
			// =================================================================
			// "**/*" is a glob pattern that matches all outgoing URLs
			page.route("**/*", route -> {
				String resourceType = route.request().resourceType();
				String url = route.request().url().toLowerCase();

				// 1. Block by Resource Type (CSS, Images, Fonts)
				if (BLOCKED_TYPES.contains(resourceType)) {
					route.abort();
					return;
				}

				// 2. Block known tracking and analytics endpoints to save bandwidth
				for (String keyword : BLOCKED_KEYWORDS) {
					if (url.contains(keyword)) {
						route.abort();
						return;
					}
				}

				// If it passes the checks, let the request proceed normally
				route.resume();
			});

			// =================================================================
			// TRAFFIC INTERCEPTION (Looking for the MP4 URL)
			// =================================================================
			page.onResponse(response -> {
				String url = response.url();
				if (url.contains(".mp4") || url.contains("video.xx.fbcdn.net")) {
					if (foundVideoUrl.get() == null) {
						foundVideoUrl.set(url);
					}
				}
			});

			// Execute navigation
			page.navigate(reelUrl);
			page.waitForLoadState();

			// Quick polling loop to check for the URL, returning early if found
			for (int i = 0; i < 20; i++) {
				if (foundVideoUrl.get() != null) {
					break;
				}
				page.waitForTimeout(200);
			}

		} catch (Exception e) {
			throw new RuntimeException("Failed to scan Instagram network streams: " + e.getMessage(), e);
		}

		return foundVideoUrl.get();
	}
}
