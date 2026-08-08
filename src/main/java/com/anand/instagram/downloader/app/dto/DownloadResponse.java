package com.anand.instagram.downloader.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DownloadResponse {
	private String videoUrl;
	private String status;
}
