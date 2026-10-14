package com.pptconverter;

import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Loopback-only, dependency-free local application. */
public final class App implements AutoCloseable {
    private final HttpServer server;
    private final Path root;
    private final String token = UUID.randomUUID().toString();
    private final ConcurrentMap<String, Batch> batches = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ExecutorService httpThreads = Executors.newFixedThreadPool(8);
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    private static final long MAX_BATCH = 256L * 1024 * 1024;
    private volatile boolean closed;

    static final class Item {
        final String id = UUID.randomUUID().toString(), name;
        final Path input, output;
        volatile String status = "queued", error = "";
        volatile int progress;
        volatile PptxConverter.Result result;
        Item(String name, Path directory) {
            this.name = name; input = directory.resolve(id + ".pptx");
            output = directory.resolve(id + "-16x9.pptx");
        }
        String outputName() { return name.substring(0, name.length() - 5) + "_converted.pptx"; }
    }
    static final class Batch {
        final String id = UUID.randomUUID().toString();
        final List<Item> items = new CopyOnWriteArrayList<>();
        final List<String> logs = new CopyOnWriteArrayList<>();
        final Path directory;
        final AtomicInteger uploading = new AtomicInteger();
        volatile long lastAccess = System.currentTimeMillis();
        long bytes;
        Batch(Path root) throws IOException {
            directory = Files.createDirectory(root.resolve(id)); log("创建批次");
        }
        void log(String message) { logs.add(Instant.now() + " " + message); }
        boolean complete() { return uploading.get() == 0 && items.stream().allMatch(i -> i.status.equals("done") || i.status.equals("failed")); }
    }

    public App(int port, Path storage) throws IOException {
        root = Files.createTempDirectory(storage, "ppt-format-converter-");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/", this::handle);
        server.setExecutor(httpThreads);
    }

    public int port() { return server.getAddress().getPort(); }
    public void start() {
        server.start();
        cleanup.scheduleAtFixedRate(() -> {
            synchronized (batches) {
                batches.values().removeIf(b -> {
                    if (b.complete() && System.currentTimeMillis() - b.lastAccess > 24L * 3600 * 1000) {
                        deleteTree(b.directory); return true;
                    }
                    return false;
                });
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    private void handle(HttpExchange x) throws IOException {
        try {
            x.getResponseHeaders().set("Cache-Control", "no-store");
            x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            x.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            x.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'");
            String host = x.getRequestHeaders().getFirst("Host");
            String expected = "127.0.0.1:" + port();
            if (!expected.equals(host)) { error(x, 403, "只允许从本机应用页面访问。"); return; }
            String origin = x.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.equals("http://" + expected)) { error(x, 403, "不允许跨站访问。"); return; }
            String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
            if (path.equals("/favicon.ico") && method.equals("GET")) { x.sendResponseHeaders(204, -1); return; }
            if (path.equals("/") && method.equals("GET")) {
                byte[] page = resource("index.html");
                send(x, 200, "text/html; charset=utf-8", new String(page, StandardCharsets.UTF_8).replace("{{TOKEN}}", token).getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (Set.of("/app.js", "/style.css").contains(path) && method.equals("GET")) {
                send(x, 200, path.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8", resource(path.substring(1))); return;
            }
            if (!path.startsWith("/api/") || !token.equals(x.getRequestHeaders().getFirst("X-App-Token"))) {
                error(x, 403, "访问令牌无效，请从启动窗口重新打开页面。"); return;
            }
            if (path.equals("/api/batches") && method.equals("POST")) {
                synchronized (batches) {
                    if (batches.size() >= 8) { error(x, 429, "最多保留 8 个批次，请删除旧批次后再试。"); return; }
                    Batch b = new Batch(root); batches.put(b.id, b);
                    json(x, 201, "{\"id\":" + quote(b.id) + "}"); return;
                }
            }
            String[] segments = path.split("/");
            if (segments.length < 4 || !segments[2].equals("batches")) { error(x, 404, "接口不存在。"); return; }
            Batch b = batches.get(segments[3]);
            if (b == null) { error(x, 404, "批次不存在或已清理，请重新上传。"); return; }
            b.lastAccess = System.currentTimeMillis();
            if (segments.length == 4 && method.equals("GET")) { json(x, 200, status(b)); return; }
            if (segments.length == 4 && method.equals("DELETE")) {
                synchronized (b) {
                    if (!b.complete()) { error(x, 409, "转换进行中，暂不能删除。"); return; }
                    batches.remove(b.id); deleteTree(b.directory);
                }
                json(x, 200, "{\"deleted\":true}"); return;
            }
            if (segments.length == 5 && segments[4].equals("files") && method.equals("POST")) {
                upload(x, b); return;
            }
            if (segments.length == 5 && segments[4].equals("log") && method.equals("GET")) {
                attachment(x, "conversion-log.txt");
                send(x, 200, "text/plain; charset=utf-8", (String.join("\n", b.logs) + "\n").getBytes(StandardCharsets.UTF_8)); return;
            }
            if (segments.length == 5 && segments[4].equals("download") && method.equals("GET")) {
                if (!b.complete()) { error(x, 409, "请等待所有文件转换完成。"); return; }
                List<Item> successful = b.items.stream().filter(i -> i.status.equals("done")).toList();
                if (successful.isEmpty()) { error(x, 404, "此批次没有成功转换的文件。"); return; }
                attachment(x, "converted-pptx.zip");
                x.getResponseHeaders().set("Content-Type", "application/zip"); x.sendResponseHeaders(200, 0);
                try (ZipOutputStream zip = new ZipOutputStream(x.getResponseBody())) {
                    Set<String> used = new HashSet<>();
                    for (Item item : successful) {
                        String name = item.outputName(), unique = name; int index = 2;
                        while (!used.add(unique.toLowerCase(Locale.ROOT))) unique = name.substring(0, name.length() - 5) + "_" + index++ + ".pptx";
                        zip.putNextEntry(new ZipEntry(unique)); Files.copy(item.output, zip); zip.closeEntry();
                    }
                    zip.putNextEntry(new ZipEntry("conversion-log.txt"));
                    zip.write(String.join("\n", b.logs).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                }
                return;
            }
            if (segments.length == 7 && segments[4].equals("files") && segments[6].equals("download") && method.equals("GET")) {
                Item item = b.items.stream().filter(i -> i.id.equals(segments[5])).findFirst().orElse(null);
                if (item == null || !item.status.equals("done")) { error(x, 404, "文件未完成或不存在。"); return; }
                attachment(x, item.outputName());
                x.getResponseHeaders().set("Content-Type", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                x.sendResponseHeaders(200, Files.size(item.output));
                try (OutputStream out = x.getResponseBody()) { Files.copy(item.output, out); }
                return;
            }
            error(x, 404, "接口不存在。");
        } catch (Exception e) {
            if (x.getResponseCode() == -1) error(x, 500, "请求处理失败：" + safeError(e));
        } finally { x.close(); }
    }

    private void upload(HttpExchange x, Batch b) throws IOException {
        String name = "";
        String query = x.getRequestURI().getRawQuery();
        if (query != null) for (String field : query.split("&"))
            if (field.startsWith("name=")) name = URLDecoder.decode(field.substring(5), StandardCharsets.UTF_8);
        name = name.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_");
        if (name.length() > 180 || name.length() <= 5 || !name.toLowerCase(Locale.ROOT).endsWith(".pptx")) {
            error(x, 400, "文件名无效，请上传 .pptx 文件（名称最长 180 字符）。"); return;
        }
        Item item;
        synchronized (b) {
            if (!batches.containsKey(b.id)) { error(x, 404, "批次已被删除。"); return; }
            if (b.items.size() >= 20) { error(x, 400, "每批最多 20 个文件。"); return; }
            item = new Item(name, b.directory); item.status = "uploading";
            b.items.add(item); b.uploading.incrementAndGet();
        }
        try {
            long received = 0;
            try (InputStream in = x.getRequestBody(); OutputStream out = Files.newOutputStream(item.input, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) {
                    received += n;
                    synchronized (b) {
                        if (received > PptxConverter.MAX_INPUT || b.bytes + n > MAX_BATCH)
                            throw new IOException("超过单文件 128 MB 或批次 256 MB 的限制。");
                        b.bytes += n;
                    }
                    out.write(buffer, 0, n);
                }
            }
            if (received == 0) throw new IOException("上传的文件为空。");
            item.status = "queued"; b.log("已上传：" + item.name + "（" + received + " 字节）");
            workers.submit(() -> convert(b, item));
            json(x, 202, "{\"id\":" + quote(item.id) + "}");
        } catch (IOException | RejectedExecutionException e) {
            item.error = safeError(e); item.status = "failed";
            b.log("上传失败：" + item.name + "：" + item.error);
            Files.deleteIfExists(item.input); error(x, 400, item.error);
        } finally { b.uploading.decrementAndGet(); }
    }

    private void convert(Batch b, Item item) {
        item.status = "converting"; b.log("开始转换：" + item.name);
        try {
            item.result = new PptxConverter().convert(item.input, item.output, p -> item.progress = p);
            PptxConverter.Result r = item.result;
            b.log("完成：" + item.name + "；尺寸 " + r.oldWidth() + " × " + r.height() + " → " + r.newWidth() + " × " + r.height() +
                " EMU；平移对象 " + r.movedObjects() + "；字体替换 " + r.replacedFonts());
            r.warnings().forEach(w -> b.log("警告：" + item.name + "：" + w));
            item.status = "done";
        } catch (Exception e) {
            item.error = safeError(e); b.log("转换失败：" + item.name + "：" + item.error); item.status = "failed";
        } finally {
            try { Files.deleteIfExists(item.input); } catch (IOException ignored) { b.log("临时输入将在退出时清理。"); }
            b.lastAccess = System.currentTimeMillis();
        }
    }

    private static String status(Batch b) {
        StringBuilder s = new StringBuilder("{\"id\":" + quote(b.id) + ",\"complete\":" + b.complete() + ",\"files\":[");
        boolean first = true;
        for (Item i : b.items) {
            if (!first) s.append(','); first = false;
            String state = i.status; // Volatile publication: result is set before done.
            s.append("{\"id\":").append(quote(i.id)).append(",\"name\":").append(quote(i.name))
             .append(",\"outputName\":").append(quote(i.outputName())).append(",\"status\":").append(quote(state))
             .append(",\"progress\":").append(i.progress).append(",\"error\":").append(quote(i.error));
            if (i.result != null) {
                PptxConverter.Result r = i.result;
                s.append(",\"resized\":").append(r.resized()).append(",\"width\":").append(r.oldWidth())
                 .append(",\"height\":").append(r.height()).append(",\"newWidth\":").append(r.newWidth())
                 .append(",\"fontReplacements\":").append(r.replacedFonts()).append(",\"warnings\":[");
                for (int j = 0; j < r.warnings().size(); j++) { if (j > 0) s.append(','); s.append(quote(r.warnings().get(j))); }
                s.append(']');
            }
            s.append('}');
        }
        return s.append("]}").toString();
    }

    static String quote(String value) {
        StringBuilder s = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\""); case '\\' -> s.append("\\\\");
                case '\n' -> s.append("\\n"); case '\r' -> s.append("\\r"); case '\t' -> s.append("\\t");
                default -> { if (c < 32) s.append(String.format("\\u%04x", (int)c)); else s.append(c); }
            }
        }
        return s.append('"').toString();
    }
    private static String safeError(Exception e) {
        if (e instanceof org.xml.sax.SAXException) return "PPTX 内部 XML 无效或包含不允许的外部实体。";
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
    private static byte[] resource(String name) throws IOException {
        try (InputStream in = App.class.getResourceAsStream("/web/" + name)) {
            if (in == null) throw new IOException("缺少网页资源。"); return in.readAllBytes();
        }
    }
    private static void attachment(HttpExchange x, String name) {
        x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"download\"; filename*=UTF-8''" +
            URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }
    private static void send(HttpExchange x, int status, String type, byte[] data) throws IOException {
        x.getResponseHeaders().set("Content-Type", type); x.sendResponseHeaders(status, data.length);
        try (OutputStream out = x.getResponseBody()) { out.write(data); }
    }
    private static void json(HttpExchange x, int status, String text) throws IOException { send(x, status, "application/json; charset=utf-8", text.getBytes(StandardCharsets.UTF_8)); }
    private static void error(HttpExchange x, int status, String message) throws IOException { json(x, status, "{\"error\":" + quote(message) + "}"); }
    private static void deleteTree(Path path) {
        try (var paths = Files.walk(path)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
        catch (IOException ignored) {}
    }

    @Override public synchronized void close() {
        if (closed) return; closed = true;
        server.stop(1); httpThreads.shutdownNow(); cleanup.shutdownNow(); workers.shutdown();
        try { if (!workers.awaitTermination(15, TimeUnit.SECONDS)) { workers.shutdownNow(); return; } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        deleteTree(root);
    }

    public static void main(String[] args) throws Exception {
        int port = 0; boolean browser = true;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--no-browser")) browser = false;
            else if (args[i].equals("--port") && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else throw new IllegalArgumentException("用法：java -jar ppt-format-converter.jar [--port 端口] [--no-browser]");
        }
        App app = new App(port, Path.of(System.getProperty("java.io.tmpdir")));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close)); app.start();
        URI url = URI.create("http://127.0.0.1:" + app.port() + "/");
        System.out.println("PPT Format Converter V1.0\n请在本机浏览器打开：" + url + "\n关闭此窗口或按 Ctrl+C 退出；退出前请下载结果。");
        if (browser) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(url);
                else if (System.getProperty("os.name").startsWith("Windows")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url.toString()).start();
            } catch (Exception e) { System.out.println("无法自动打开浏览器，请复制上述地址到浏览器。"); }
        }
    }
}
