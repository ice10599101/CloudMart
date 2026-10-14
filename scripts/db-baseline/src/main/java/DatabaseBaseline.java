import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** C26: operate only on explicitly selected schemas; never repair or clean. */
public class DatabaseBaseline {
    static final String USER = System.getenv().getOrDefault("MYSQL_USERNAME", "root");
    static final String PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "123456");
    static final String HOST = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
    static final String PORT = System.getenv().getOrDefault("MYSQL_PORT", "8306");
    static final String SUFFIX = System.getenv().getOrDefault("MYSQL_SCHEMA_SUFFIX", "");
    static final Map<String, Set<String>> SEEDS = Map.of(
        "mall-admin", Set.of("admin_menu", "admin_role", "admin_role_menu", "admin_config", "admin_dict_type", "admin_dict_data", "admin_dept", "admin_post"),
        "mall-community", Set.of("sensitive_words", "exp_rules", "level_configs", "ranking_configs", "avatar_frames"),
        "mall-wish", Set.of("wish_category", "wish_badge", "wish_env_config", "wish_ai_config", "wish_leaderboard_config", "wish_match_config", "wish_gift"),
        "mall-pet", Set.of("pet_job_config", "pet_study_config", "pet_achievement", "pet_equipment_config", "pet_skin_config", "pet_skill_config", "pet_evolution_config", "pet_event_config", "pet_career_config", "pet_furniture_config", "pet_daily_quest_config", "pet_content_sensitive_word", "pet_food_config", "pet_config", "pet_persona_phrase", "pet_season_guard")
    );
    static String url(String schema) { return "jdbc:mysql://" + HOST + ":" + PORT + "/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"; }
    static String q(String name) { if (!name.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException(name); return "`" + name + "`"; }
    static Connection connect(String schema) throws SQLException { return DriverManager.getConnection(url(schema), USER, PASSWORD); }
    static Flyway flyway(Path dir, String schema, boolean legacyJava) throws Exception {
        FluentConfiguration c = Flyway.configure().dataSource(url(schema), USER, PASSWORD)
            .locations("filesystem:" + dir.toAbsolutePath().toString().replace('\\', '/'))
            .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true).ignoreMigrationPatterns(new String[0]);
        if (legacyJava) c.javaMigrations((JavaMigration) Class.forName("com.cloudmart.community.db.migration.V3__add_review_and_blocks").getDeclaredConstructor().newInstance());
        return c.load();
    }
    static List<Path> modules(Path root) throws Exception {
        try (var paths = Files.list(root)) {
            return paths.filter(p -> Files.isDirectory(p.resolve("src/main/resources/db/migration"))).sorted().toList();
        }
    }
    static List<String> tables(Connection c) throws SQLException {
        var result = new ArrayList<String>();
        try (var s = c.createStatement(); var r = s.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
            while (r.next()) if (!r.getString(1).equals("flyway_schema_history")) result.add(r.getString(1));
        }
        Collections.sort(result); return result;
    }
    static String ddl(Connection c, String table) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery("SHOW CREATE TABLE " + q(table))) {
            r.next(); return r.getString(2).replaceAll(" AUTO_INCREMENT=\\d+", "");
        }
    }
    static String literal(ResultSet r, int i) throws SQLException {
        Object value = r.getObject(i);
        if (value == null) return "NULL";
        if (value instanceof byte[] bytes) return "X'" + HexFormat.of().formatHex(bytes) + "'";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean b) return b ? "1" : "0";
        return "'" + r.getString(i).replace("\\", "\\\\").replace("'", "''").replace("\0", "\\0").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }
    static List<String> rows(Connection c, String table) throws SQLException { return rows(c, table, false); }
    static List<String> rows(Connection c, String table, boolean semantic) throws SQLException {
        var columns = new ArrayList<String>();
        String auditColumns = semantic ? " AND COLUMN_NAME NOT IN ('created_at','updated_at','gmt_create','gmt_modified','update_time')" : "";
        try (var s = c.prepareStatement("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND extra NOT LIKE '%VIRTUAL GENERATED%' AND extra NOT LIKE '%STORED GENERATED%'" + auditColumns + " ORDER BY ordinal_position")) {
            s.setString(1, table); try (var r = s.executeQuery()) { while (r.next()) columns.add(q(r.getString(1))); }
        }
        var rows = new ArrayList<String>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT " + String.join(", ", columns) + " FROM " + q(table))) {
            while (r.next()) {
                var values = new ArrayList<String>();
                for (int i = 1; i <= columns.size(); i++) values.add(literal(r, i));
                rows.add("INSERT INTO " + q(table) + " (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", values) + ");");
            }
        }
        Collections.sort(rows); return rows;
    }
    static String sha(String text) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    static Map<String, Object> snapshot(Connection c, String module, boolean enforceSeeds) throws Exception {
        var result = new TreeMap<String, Object>();
        for (String t : tables(c)) {
            // Ignore only audit timestamps in content hashes; retain business schedules and all constraints.
            var rows = rows(c, t, true);
            if (enforceSeeds && !rows.isEmpty() && !SEEDS.getOrDefault(module, Set.of()).contains(t)) throw new IllegalStateException("Unreviewed seed table: " + module + "." + t + " (" + rows.size() + ")");
            result.put(t, Map.of("ddlSha256", sha(ddl(c, t)), "rows", rows.size(), "seedSha256", sha(String.join("\n", rows))));
        }
        return result;
    }
    static void writeJson(Path file, Object value) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value) + "\n", StandardCharsets.UTF_8);
    }
    static void expectFailure(String name, Runnable operation) {
        try { operation.run(); }
        catch (org.flywaydb.core.api.FlywayException expected) {
            System.out.println("C26 guard PASS " + name); return;
        }
        throw new IllegalStateException("Flyway protection did not reject: " + name);
    }
    static void verifyAdminSeeds(Connection c, Path root) throws Exception {
        for (String sql : List.of(
            "SELECT COUNT(*) FROM admin_menu m LEFT JOIN admin_menu p ON p.id=m.parent_id WHERE m.parent_id<>0 AND p.id IS NULL",
            "SELECT COUNT(*) FROM admin_role_menu rm LEFT JOIN admin_role r ON r.id=rm.role_id LEFT JOIN admin_menu m ON m.id=rm.menu_id WHERE r.id IS NULL OR m.id IS NULL")) {
            try (var s = c.createStatement(); var r = s.executeQuery(sql)) {
                r.next(); if (r.getLong(1) != 0) throw new IllegalStateException("Orphan menu/role seed: " + sql);
            }
        }
        var permissions = new HashSet<String>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT DISTINCT perms FROM admin_menu WHERE deleted_at IS NULL")) {
            while (r.next()) permissions.add(r.getString(1));
        }
        var missing = new TreeSet<String>();
        var annotation = java.util.regex.Pattern.compile("@RequiresPermission\\(([^)]*)\\)", java.util.regex.Pattern.DOTALL);
        var string = java.util.regex.Pattern.compile("\"([^\"]+)\"");
        List<Path> appModules;
        try (var dirs = Files.list(root)) { appModules = dirs.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("mall-")).toList(); }
        for (Path module : appModules) {
            Path java = module.resolve("src/main/java");
            if (!Files.isDirectory(java)) continue;
            try (var sources = Files.walk(java)) {
                for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var annotations = annotation.matcher(Files.readString(file));
                    while (annotations.find()) {
                        var values = string.matcher(annotations.group(1));
                        while (values.find()) {
                            String permission = values.group(1);
                            if (!permission.contains("$") && !permission.equals("*:*:*") && !permissions.contains(permission)) missing.add(permission);
                        }
                    }
                }
            }
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Permissions missing from baseline menu seeds: " + missing);
        System.out.println("C26 admin menu hierarchy, role bindings and controller permissions: PASS");
    }
    static void verifyProtections(Path migrations, String schema, Path root) throws Exception {
        Path temp = Files.createTempDirectory(Files.createDirectories(root.resolve(".tmp-c26")), "validation-");
        // Isolate checksum and missing-file probes from the tracked migration sources.
        List<Path> originals;
        try (var files = Files.list(migrations)) { originals = files.filter(p -> p.toString().endsWith(".sql")).toList(); }
        for (Path file : originals) Files.copy(file, temp.resolve(file.getFileName()));
        Path changed = temp.resolve(originals.get(0).getFileName());
        Files.writeString(changed, "\n-- checksum drift probe\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Flyway drift = flyway(temp, schema, false);
        expectFailure("checksum drift", drift::validate);
        Files.delete(changed);
        Flyway missing = flyway(temp, schema, false);
        expectFailure("missing historical migration", missing::validate);
        expectFailure("automatic clean prohibited", drift::clean);
        for (Path file : originals) Files.deleteIfExists(temp.resolve(file.getFileName()));
        Files.delete(temp);
        String occupied = schema + "_occupied";
        try (var c = connect(""); var s = c.createStatement()) {
            s.execute("CREATE DATABASE " + q(occupied));
            try {
                s.execute("CREATE TABLE " + q(occupied) + ".occupied (id INT PRIMARY KEY)");
                Flyway nonEmpty = flyway(migrations, occupied, false);
                expectFailure("non-empty database without history", nonEmpty::migrate);
            } finally { s.execute("DROP DATABASE " + q(occupied)); }
        }
    }
    static void verifyOrderShards(Connection c) throws Exception {
        for (String logical : List.of("orders", "order_items")) {
            String expected = ddl(c, logical);
            for (int i = 0; i < 4; i++) {
                String shard = logical + "_" + i;
                String actual = ddl(c, shard).replace("CREATE TABLE " + q(shard), "CREATE TABLE " + q(logical));
                if (!expected.equals(actual)) throw new IllegalStateException("Order shard schema drift: " + shard);
            }
        }
        System.out.println("C26 all eight order shards match current logical tables: PASS");
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("replay|export|verify|record|migrate ROOT PREFIX [REPORT]");
        String mode = args[0], prefix = args[2]; Path root = Path.of(args[1]).toAbsolutePath();
        if (!prefix.matches("c26_[a-z0-9_]+_") && !(mode.equals("migrate") && prefix.equals("mall_"))) throw new IllegalArgumentException("Use c26_<run>_ prefix; migrate mall_ only targets business schemas");
        var report = new TreeMap<String, Object>();
        boolean guardsVerified = false;
        for (Path module : modules(root)) {
            String name = module.getFileName().toString(), schema = prefix + name.substring(5).replace('-', '_') + SUFFIX;
            Path migrations = module.resolve("src/main/resources/db/migration");
            if (mode.equals("replay") || mode.equals("verify") || mode.equals("record") || mode.equals("migrate")) {
                try (var c = connect(""); var s = c.createStatement()) {
                    // No DROP: verification prefixes must be new; normal migrate can create a missing schema.
                    s.execute("CREATE DATABASE " + (mode.equals("migrate") ? "IF NOT EXISTS " : "") + q(schema) + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                }
            }
            if (!mode.equals("export")) {
                Flyway f = flyway(migrations, schema, mode.equals("replay") && name.equals("mall-community"));
                f.migrate(); f.validate();
                int repeated = f.migrate().migrationsExecuted;
                if (repeated != 0) throw new IllegalStateException("Second startup executed migrations: " + name);
                f.validate();
                if ((mode.equals("verify") || mode.equals("record")) && !guardsVerified) {
                    verifyProtections(migrations, schema, root);
                    guardsVerified = true;
                }
            }
            try (var c = connect(schema)) {
                if ((mode.equals("verify") || mode.equals("record")) && name.equals("mall-admin")) verifyAdminSeeds(c, root);
                if ((mode.equals("verify") || mode.equals("record")) && name.equals("mall-order")) verifyOrderShards(c);
                var snap = snapshot(c, name, !mode.equals("migrate") && !mode.equals("replay"));
                if (mode.equals("export")) {
                    var sql = new StringBuilder("-- C26 clean development/test baseline. Recreated EMPTY databases only.\n-- Materialized from verified MySQL schema; never repair an old history to adopt this file.\nSET NAMES utf8mb4;\nSET time_zone = '+00:00';\n\n");
                    for (String t : tables(c)) sql.append(ddl(c, t)).append(";\n\n");
                    for (String t : tables(c)) { var data = rows(c, t); if (!data.isEmpty()) sql.append("-- Required catalog/configuration: ").append(t).append('\n').append(String.join("\n", data)).append("\n\n"); }
                    // Export to a staging directory. Retiring historical migrations is a separate explicit step.
                    Path dest = root.resolve(".tmp-c26/baselines/" + name + "/V1__baseline.sql");
                    Files.createDirectories(dest.getParent()); Files.writeString(dest, sql, StandardCharsets.UTF_8);
                }
                report.put(name, snap);
                System.out.println("C26 " + mode + " OK " + name + " tables=" + snap.size());
            }
        }
        if (args.length > 3) writeJson(Path.of(args[3]), report);
        if (mode.equals("verify")) {
            var expected = JsonParser.parseString(Files.readString(root.resolve("scripts/db-baseline/schema-manifest.json")));
            var actual = new GsonBuilder().create().toJsonTree(report);
            if (!expected.equals(actual)) throw new IllegalStateException("Baseline schema/seed manifest mismatch (see report)");
            System.out.println("C26 manifest matches: every table, index, constraint and seed row");
        }
    }
}
