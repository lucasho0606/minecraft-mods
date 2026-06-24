package com.InstallYuanShen.GenshinImpactDownload;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;



public class Handle {
    private static final String DOWNLOAD_URL = "https://ys-api.mihoyo.com/event/download_porter/link/ys_cn/official/pc_backup319"; // 修改为你的下载地址


    /**
     * 从 Windows 注册表读取原神安装路径
     */
    private static String getGenshinPathFromRegistry() {
        try {
            main.LOGGER.info("正在从注册表读取原神路径...");
            String command = "reg query \"HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Genshin Impact\" /v DisplayIcon";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                main.LOGGER.debug("注册表输出: " + line);
                if (line.contains("REG_SZ") || line.contains("REG_EXPAND_SZ")) {
                    // ... 提取路径
                }
            }
            main.LOGGER.warn("注册表中未找到原神路径");
        } catch (Exception e) {
            main.LOGGER.error("读取注册表失败: ", e);
        }
        return null;
    }

    public static void handleLauncher() {
        try {
            main.LOGGER.info("========== 开始查找原神启动器 ==========");

            // 1. 首先查找 YuanShen.exe（全盘搜索）
            main.LOGGER.info("正在查找 YuanShen.exe...");
            String yuanShenPath = findYuanShen();
            if (yuanShenPath != null) {
                main.LOGGER.info("✅ 找到 YuanShen.exe: " + yuanShenPath);
                runProgram(yuanShenPath);
                return;
            } else {
                main.LOGGER.warn("❌ 未找到 YuanShen.exe");
            }

            // 2. 如果找不到原神本体，查找 miHoYo Launcher
            main.LOGGER.info("正在查找 miHoYo Launcher 启动器...");
            String launcherPath = findMiHoYoLauncher();
            if (launcherPath != null) {
                main.LOGGER.info("✅ 找到 launcher.exe: " + launcherPath);
                runProgram(launcherPath);
                return;
            } else {
                main.LOGGER.warn("❌ 未找到 miHoYo Launcher");
            }

            // 3. 如果都找不到，下载文件
            main.LOGGER.info("❌ 未找到任何启动器，开始下载...");
            downloadAndRun();

        } catch (Exception e) {
            main.LOGGER.error("处理启动器时发生错误: ", e);
        }
    }

    /**
     * 多线程全盘搜索（更快）
     */
    private static String findYuanShen() {
        main.LOGGER.info("开始多线程全盘搜索 YuanShen.exe...");

        File[] roots = File.listRoots();
        if (roots == null) {
            return null;
        }

        // 使用线程池并发搜索
        ExecutorService executor = Executors.newFixedThreadPool(roots.length);
        List<Future<String>> futures = new ArrayList<>();

        for (File root : roots) {
            futures.add(executor.submit(() -> searchDrive(root)));
        }

        try {
            // 等待任何一个任务完成
            for (Future<String> future : futures) {
                try {
                    String result = future.get();
                    if (result != null) {
                        executor.shutdownNow();
                        return result;
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        } finally {
            executor.shutdownNow();
        }

        return null;
    }

    private static String searchDrive(File root) {
        main.LOGGER.info("线程 " + Thread.currentThread().getId() + " 正在搜索 " + root.getPath());

        Queue<File> queue = new LinkedList<>();
        queue.add(root);
        int totalDirs = 0;

        while (!queue.isEmpty()) {
            File dir = queue.poll();

            try {
                if (!dir.exists() || !dir.isDirectory()) {
                    continue;
                }

                if (dir.getName().equalsIgnoreCase("System Volume Information") ||
                        dir.getName().equalsIgnoreCase("$Recycle.Bin") ||
                        dir.getName().equalsIgnoreCase("Windows") ||
                        dir.getName().equalsIgnoreCase("Winnt") ||
                        dir.getName().equalsIgnoreCase("ProgramData") ||
                        dir.getName().startsWith("$")) {
                    continue;
                }

                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (file.isDirectory()) {
                        queue.add(file);
                        totalDirs++;
                    } else if (file.isFile() && file.getName().equalsIgnoreCase("YuanShen.exe")) {
                        main.LOGGER.info("✅✅✅ 在 " + root.getPath() + " 找到 YuanShen.exe！");
                        main.LOGGER.info("路径: " + file.getAbsolutePath());
                        return file.getAbsolutePath();
                    }
                }
            } catch (Exception e) {
                // 跳过
            }
        }

        return null;
    }


    /**
     * 全盘搜索 miHoYo Launcher 文件夹，找到 launcher.exe
     * 搜索所有盘符的所有文件夹，不遗漏任何一个角落
     */
    private static String findMiHoYoLauncher() {
        main.LOGGER.info("开始全盘搜索 miHoYo Launcher 文件夹...");

        File[] roots = File.listRoots();
        if (roots == null) {
            main.LOGGER.warn("无法获取盘符列表");
            return null;
        }

        int totalDirs = 0;
        int totalFiles = 0;

        for (File root : roots) {
            main.LOGGER.info("正在搜索 " + root.getPath() + " ...");

            // 使用队列进行广度优先搜索
            Queue<File> queue = new LinkedList<>();
            queue.add(root);

            while (!queue.isEmpty()) {
                File dir = queue.poll();

                try {
                    if (!dir.exists() || !dir.isDirectory()) {
                        continue;
                    }

                    // 跳过系统目录（提高搜索效率）
                    String dirName = dir.getName();
                    if (dirName.equalsIgnoreCase("System Volume Information") ||
                            dirName.equalsIgnoreCase("$Recycle.Bin") ||
                            dirName.equalsIgnoreCase("Windows") ||
                            dirName.equalsIgnoreCase("Winnt") ||
                            dirName.equalsIgnoreCase("ProgramData") ||
                            dirName.equalsIgnoreCase("AppData") ||
                            dirName.startsWith("$") ||
                            dirName.equalsIgnoreCase("temp") ||
                            dirName.equalsIgnoreCase("tmp")) {
                        continue;
                    }

                    File[] files = dir.listFiles();
                    if (files == null) {
                        continue;
                    }

                    for (File file : files) {
                        if (file.isDirectory()) {
                            // 检查是否找到了 miHoYo Launcher 文件夹（精确匹配，不区分大小写）
                            if (file.getName().equalsIgnoreCase("miHoYo Launcher")) {
                                main.LOGGER.info("✅ 找到 miHoYo Launcher 文件夹: " + file.getAbsolutePath());

                                // 在 miHoYo Launcher 文件夹中精确查找 launcher.exe
                                String launcherPath = findLauncherExe(file);
                                if (launcherPath != null) {
                                    main.LOGGER.info("✅ 找到 launcher.exe: " + launcherPath);
                                    main.LOGGER.info("总共扫描了 " + totalDirs + " 个目录，" + totalFiles + " 个文件");
                                    return launcherPath;
                                }
                            }
                            // 将子目录加入队列继续搜索
                            queue.add(file);
                            totalDirs++;
                        } else if (file.isFile()) {
                            totalFiles++;
                        }
                    }
                } catch (Exception e) {
                    // 权限不足或其他错误，跳过继续
                    main.LOGGER.debug("无法访问目录: " + dir.getPath() + " - " + e.getMessage());
                }
            }
        }

        main.LOGGER.warn("❌ 全盘搜索结束，未找到 miHoYo Launcher 文件夹");
        main.LOGGER.info("总共扫描了 " + totalDirs + " 个目录，" + totalFiles + " 个文件");
        return null;
    }

    /**
     * 在 miHoYo Launcher 文件夹中精确查找 launcher.exe
     * 检查所有子目录，名字必须完全匹配 launcher.exe（不区分大小写）
     */
    private static String findLauncherExe(File launcherFolder) {
        main.LOGGER.info("在 " + launcherFolder.getPath() + " 中精确查找 launcher.exe...");

        // 使用队列搜索 launcher 文件夹下的所有子目录
        Queue<File> queue = new LinkedList<>();
        queue.add(launcherFolder);

        int checkedDirs = 0;

        while (!queue.isEmpty()) {
            File dir = queue.poll();

            try {
                if (!dir.exists() || !dir.isDirectory()) {
                    continue;
                }

                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (file.isDirectory()) {
                        // 将子目录加入队列继续搜索
                        queue.add(file);
                        checkedDirs++;
                    } else if (file.isFile()) {
                        // 精确匹配文件名（不区分大小写）
                        if (file.getName().equalsIgnoreCase("launcher.exe")) {
                            main.LOGGER.info("✅ 在 " + dir.getPath() + " 找到 launcher.exe");
                            return file.getAbsolutePath();
                        }
                    }
                }
            } catch (Exception e) {
                main.LOGGER.debug("无法访问目录: " + dir.getPath() + " - " + e.getMessage());
            }
        }

        main.LOGGER.warn("在 " + launcherFolder.getPath() + " 中未找到 launcher.exe");
        return null;
    }

    private static String searchInDirectory(File directory, String fileName) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 限制搜索深度，避免性能问题
                if (file.getName().equals("Genshin Impact") || file.getName().equals("miHoYo Launcher")) {
                    String result = searchInDirectory(file, fileName);
                    if (result != null) {
                        return result;
                    }
                }
            } else if (file.getName().equalsIgnoreCase(fileName)) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static void runProgram(String path) {
        try {
            main.LOGGER.info("正在启动程序: " + path);

            File file = new File(path);
            if (!file.exists()) {
                main.LOGGER.error("❌ 文件不存在: " + path);
                return;
            }

            // 使用 cmd /c start 启动（会弹窗请求管理员权限）
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", "\"" + path + "\""});
            main.LOGGER.info("✅ 程序已启动");

            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    main.LOGGER.info("程序已退出，退出代码: " + exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (IOException e) {
            main.LOGGER.error("❌ 无法运行程序: " + path, e);
        }
    }

    private static String getDownloadPath() {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        return "./Temporary/launcher_" + timestamp + ".exe";
    }

    private static void downloadAndRun() {
        try {
            // 每次生成带时间戳的新路径
            String downloadPathStr = getDownloadPath();
            Path downloadPath = Paths.get(downloadPathStr);
            Path parentDir = downloadPath.getParent();

            // 创建目录
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            main.LOGGER.info("开始下载文件到: " + downloadPathStr);

            // 下载文件
            URL url = new URL(DOWNLOAD_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, downloadPath, StandardCopyOption.REPLACE_EXISTING);
                }
                main.LOGGER.info("✅ 下载完成: " + downloadPathStr);
                main.LOGGER.info("正在运行...");
                runProgram(downloadPathStr);
            } else {
                main.LOGGER.error("❌ 下载失败，HTTP响应码: " + responseCode);
            }

        } catch (Exception e) {
            main.LOGGER.error("下载或运行程序失败: ", e);
        }
    }
}
