package com.github.yehortpk.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.microsoft.playwright.Playwright;

@SpringBootApplication
public class CrawlerApplication {

	public static void main(String[] args) {
		// Required for download all drivers (some issue when Playwright skips its
		// directives about drivers locations when set manually.)
		Playwright.create().close();
		SpringApplication.run(CrawlerApplication.class, args);
	}

}
