package com.anand.instagram.downloader.app.config;


import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaywrightConfig {

    private Playwright playwright;
    private Browser browser;

    @Bean
    public Browser playwrightBrowser() {
        // Runs exactly once at application startup
        this.playwright = Playwright.create();
        
        // Launch a single, reusable heavy browser instance
        this.browser = this.playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true)
        );
        
        return this.browser;
    }

    @PreDestroy
    public void shutdown() {
        // Ensures smooth OS process cleanup when Spring Boot shuts down
        if (this.browser != null) {
            this.browser.close();
        }
        if (this.playwright != null) {
            this.playwright.close();
        }
    }
}
