package com.github.yehortpk.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.microsoft.playwright.Playwright;

@SpringBootApplication
public class ParserApplication {

	public static void main(String[] args) {
		// Required for download all drivers (some issue when Playwright skips its
		// directives about drivers locations when set manually.)
		Playwright.create().close();
		SpringApplication.run(ParserApplication.class, args);
	}

}
