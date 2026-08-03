package com.cloudmart.admin.service;

import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class AdminMonitorService {

    private final StringRedisTemplate redisTemplate;

    public AdminMonitorService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> getServerInfo() {
        Map<String, Object> info = new HashMap<>();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> cpu = new HashMap<>();
        cpu.put("cpuNum", osBean.getAvailableProcessors());
        double systemCpuLoad = 0.0;
        double processCpuLoad = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            systemCpuLoad = sunOsBean.getCpuLoad() * 100;
            processCpuLoad = sunOsBean.getProcessCpuLoad() * 100;
        }
        double cpuUsage = processCpuLoad > 0 ? processCpuLoad : (osBean.getSystemLoadAverage() > 0 ? Math.min(osBean.getSystemLoadAverage() / osBean.getAvailableProcessors() * 100, 100) : 0);
        cpu.put("sys", Math.round(systemCpuLoad * 100.0) / 100.0);
        cpu.put("user", Math.round(processCpuLoad * 100.0) / 100.0);
        cpu.put("wait", 0.0);
        double cpuFree = Math.max(0, 100 - cpuUsage);
        cpu.put("free", Math.round(cpuFree * 100.0) / 100.0);
        cpu.put("usage", Math.round(Math.min(cpuUsage, 100) * 100.0) / 100.0);
        cpu.put("total", osBean.getAvailableProcessors());
        info.put("cpu", cpu);

        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;
        long maxMem = runtime.maxMemory();
        double memUsage = maxMem > 0 ? (double) usedMem / maxMem * 100 : 0;
        Map<String, Object> mem = new HashMap<>();
        mem.put("total", totalMem);
        mem.put("used", usedMem);
        mem.put("free", freeMem);
        mem.put("usage", Math.round(memUsage * 100.0) / 100.0);
        info.put("mem", mem);

        long jvmUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long jvmMax = memoryBean.getHeapMemoryUsage().getMax();
        long jvmFree = memoryBean.getHeapMemoryUsage().getCommitted() - jvmUsed;
        long jvmTotal = memoryBean.getHeapMemoryUsage().getCommitted();
        double jvmUsage = jvmMax > 0 ? (double) jvmUsed / jvmMax * 100 : 0;
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("name", runtimeBean.getVmName());
        jvm.put("version", runtimeBean.getSpecVersion());
        jvm.put("home", System.getProperty("java.home"));
        jvm.put("startTime", formatDateTime(runtimeBean.getStartTime()));
        long uptimeMs = runtimeBean.getUptime();
        jvm.put("runTime", formatUptime(uptimeMs));
        jvm.put("total", jvmTotal);
        jvm.put("max", jvmMax);
        jvm.put("free", jvmFree);
        jvm.put("used", jvmUsed);
        jvm.put("usage", Math.round(jvmUsage * 100.0) / 100.0);
        jvm.put("inputArgs", runtimeBean.getInputArguments());
        info.put("jvm", jvm);

        Map<String, Object> sys = new HashMap<>();
        sys.put("computerName", System.getenv().getOrDefault("COMPUTERNAME", "localhost"));
        sys.put("osName", osBean.getName());
        sys.put("osArch", osBean.getArch());
        sys.put("computerIp", getLocalIp());
        sys.put("userDir", System.getProperty("user.dir"));
        info.put("sys", sys);

        File[] roots = File.listRoots();
        ArrayList<Map<String, Object>> diskList = new ArrayList<>();
        if (roots != null) {
            for (File root : roots) {
                Map<String, Object> disk = new HashMap<>();
                long totalSpace = root.getTotalSpace();
                long freeSpace = root.getFreeSpace();
                long usedSpace = totalSpace - freeSpace;
                double usage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
                disk.put("dirName", root.getAbsolutePath());
                disk.put("sysTypeName", root.getAbsolutePath());
                disk.put("typeName", "本地磁盘");
                disk.put("total", formatFileSize(totalSpace));
                disk.put("free", formatFileSize(freeSpace));
                disk.put("used", formatFileSize(usedSpace));
                disk.put("usage", Math.round(usage * 100.0) / 100.0);
                diskList.add(disk);
            }
        }
        info.put("disk", diskList);

        return info;
    }

    public Map<String, Object> getCacheInfo() {
        Map<String, Object> info = new HashMap<>();

        Properties redisInfo = redisTemplate.execute((RedisCallback<Properties>) connection -> {
            RedisServerCommands commands = connection.serverCommands();
            return commands.info();
        });

        Properties keySpace = redisTemplate.execute((RedisCallback<Properties>) connection -> {
            RedisServerCommands commands = connection.serverCommands();
            return commands.info("keyspace");
        });

        Long dbSize = redisTemplate.execute((RedisCallback<Long>) connection -> {
            RedisServerCommands commands = connection.serverCommands();
            return commands.dbSize();
        });

        Properties commandStats = redisTemplate.execute((RedisCallback<Properties>) connection -> {
            RedisServerCommands commands = connection.serverCommands();
            return commands.info("commandstats");
        });

        info.put("redisVersion", getProperty(redisInfo, "redis_version"));
        info.put("uptimeInSeconds", parseLong(getProperty(redisInfo, "uptime_in_seconds")));
        info.put("connectedClients", parseLong(getProperty(redisInfo, "connected_clients")));
        info.put("usedMemory", getProperty(redisInfo, "used_memory"));
        info.put("usedMemoryHuman", getProperty(redisInfo, "used_memory_human"));
        info.put("totalSystemMemory", getProperty(redisInfo, "total_system_memory_human"));
        info.put("maxMemory", getProperty(redisInfo, "maxmemory"));
        info.put("maxMemoryHuman", getProperty(redisInfo, "maxmemory_human"));
        long maxMemory = parseLong(getProperty(redisInfo, "maxmemory"));
        long usedMemoryBytes = parseLong(getProperty(redisInfo, "used_memory"));
        double usedMemoryPercent = maxMemory > 0 ? (double) usedMemoryBytes / maxMemory * 100 : 0;
        info.put("usedMemoryPercent", Math.round(usedMemoryPercent * 100.0) / 100.0);
        info.put("dbSize", dbSize);
        info.put("keyspaceHits", parseLong(getProperty(redisInfo, "keyspace_hits")));
        info.put("keyspaceMisses", parseLong(getProperty(redisInfo, "keyspace_misses")));
        long hits = parseLong(getProperty(redisInfo, "keyspace_hits"));
        long misses = parseLong(getProperty(redisInfo, "keyspace_misses"));
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
        info.put("hitRate", Math.round(hitRate * 100.0) / 100.0);
        info.put("commandsProcessed", parseLong(getProperty(redisInfo, "total_commands_processed")));
        info.put("opsPerSec", parseLong(getProperty(redisInfo, "instantaneous_ops_per_sec")));
        info.put("avgTtl", parseAvgTtl(keySpace));
        info.put("dbKeys", parseDbKeys(keySpace));
        info.put("commandStats", parseCommandStats(commandStats));

        return info;
    }

    private java.util.List<Map<String, Object>> parseCommandStats(Properties commandStats) {
        java.util.List<Map<String, Object>> stats = new ArrayList<>();
        if (commandStats != null) {
            for (String key : commandStats.stringPropertyNames()) {
                if (key.startsWith("cmdstat_")) {
                    Map<String, Object> cmd = new HashMap<>();
                    String name = key.substring("cmdstat_".length());
                    String value = commandStats.getProperty(key);
                    long count = 0;
                    if (value != null && value.contains("calls=")) {
                        try {
                            String callsPart = value.split("calls=")[1].split(",")[0];
                            count = Long.parseLong(callsPart);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    cmd.put("name", name);
                    cmd.put("count", count);
                    stats.add(cmd);
                }
            }
        }
        return stats;
    }

    private String formatDateTime(long timestamp) {
        return java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatUptime(long uptimeMs) {
        long days = uptimeMs / (24 * 60 * 60 * 1000);
        long hours = (uptimeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptimeMs % (60 * 60 * 1000)) / (60 * 1000);
        return days + "天" + hours + "小时" + minutes + "分钟";
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private String getProperty(Properties props, String key) {
        if (props == null) return "";
        return props.getProperty(key, "");
    }

    private long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Map<String, Object>> parseDbKeys(Properties keySpace) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (keySpace == null) return result;
        for (String key : keySpace.stringPropertyNames()) {
            String value = keySpace.getProperty(key);
            Map<String, Object> dbInfo = new HashMap<>();
            if (value != null) {
                String[] parts = value.split(",");
                for (String part : parts) {
                    String[] kv = part.split("=");
                    if (kv.length == 2) {
                        String k = kv[0].trim();
                        Object v = kv[1].trim();
                        if ("keys".equals(k) || "expires".equals(k)) {
                            try { v = Long.parseLong(kv[1].trim()); } catch (NumberFormatException ignored) {}
                        } else if ("avg_ttl".equals(k)) {
                            try { v = Long.parseLong(kv[1].trim()); } catch (NumberFormatException ignored) {}
                        }
                        dbInfo.put(k, v);
                    }
                }
            }
            result.put(key, dbInfo);
        }
        return result;
    }

    private long parseAvgTtl(Properties keySpace) {
        if (keySpace == null) return 0;
        long totalTtl = 0;
        int count = 0;
        for (String key : keySpace.stringPropertyNames()) {
            String value = keySpace.getProperty(key);
            if (value != null && value.contains("avg_ttl=")) {
                try {
                    String[] parts = value.split(",");
                    for (String part : parts) {
                        if (part.trim().startsWith("avg_ttl=")) {
                            totalTtl += Long.parseLong(part.trim().substring("avg_ttl=".length()));
                            count++;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return count > 0 ? totalTtl / count : 0;
    }
}
