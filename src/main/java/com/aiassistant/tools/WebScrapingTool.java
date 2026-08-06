package com.aiassistant.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WebScrapingTool {

    @Tool(description = "Scrape and extract text content from a web page URL")
    public String scrapeWebPage(
            @ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            return doc.body().text();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
