package com.pptconverter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Edits only OOXML metadata and coordinates; leaves binary objects byte-for-byte intact. */
public final class PptxConverter {
    public static final long MAX_INPUT = 128L * 1024 * 1024;
    private static final long MAX_EXPANDED = 512L * 1024 * 1024;
    private static final int MAX_XML = 16 * 1024 * 1024, MAX_ENTRIES = 20000;
    static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String STRICT_P = "http://purl.oclc.org/ooxml/presentationml/main";

    public static final class Result {
        private final long oldWidth, height, newWidth;
        private final boolean resized;
        private final int movedObjects, replacedFonts;
        private final List<String> warnings;
        Result(long oldWidth, long height, long newWidth, boolean resized,
               int movedObjects, int replacedFonts, List<String> warnings) {
            this.oldWidth = oldWidth; this.height = height; this.newWidth = newWidth;
            this.resized = resized; this.movedObjects = movedObjects; this.replacedFonts = replacedFonts;
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }
        public long oldWidth() { return oldWidth; }
        public long height() { return height; }
        public long newWidth() { return newWidth; }
        public boolean resized() { return resized; }
        public int movedObjects() { return movedObjects; }
        public int replacedFonts() { return replacedFonts; }
        public List<String> warnings() { return warnings; }
    }

    public Result convert(Path input, Path output, IntConsumer progress) throws Exception {
        if (!input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pptx"))
            throw new IOException("仅支持 .pptx；旧版 .ppt 请先在 PowerPoint 中另存为 .pptx。");
        if (Files.size(input) > MAX_INPUT) throw new IOException("单个文件不能超过 128 MB。");
        if (Files.exists(output)) throw new IOException("输出文件已存在，拒绝覆盖。");
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IOException("输出不能与原文件相同。");
        Path parent = output.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".ppt-convert-", ".tmp");
        List<String> warnings = new ArrayList<>();
        int moved = 0, fonts = 0;
        long width, height, newWidth;
        boolean resized;
        try (ZipFile zip = new ZipFile(input.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            if (entries.size() > MAX_ENTRIES) throw new IOException("PPTX 内部文件数量超出安全上限。");
            Set<String> names = new HashSet<>();
            for (ZipEntry e : entries) {
                String name = e.getName();
                if (!names.add(name) || name.startsWith("/") || name.contains("\\") ||
                    Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("PPTX 包含重复或非法内部路径。");
                if (name.startsWith("_xmlsignatures/"))
                    throw new IOException("文件带有数字签名，修改将使签名失效；请先另存为无签名副本。");
            }
            ZipEntry presentation = zip.getEntry("ppt/presentation.xml");
            if (presentation == null || zip.getEntry("[Content_Types].xml") == null)
                throw new IOException("不是有效的 PPTX 文件（缺少演示文稿结构）。");
            Document doc = parse(readXml(zip, presentation));
            if (STRICT_P.equals(doc.getDocumentElement().getNamespaceURI()))
                throw new IOException("暂不支持 Strict OOXML，请用 PowerPoint 另存为标准 .pptx。");
            NodeList sizes = doc.getElementsByTagNameNS(P, "sldSz");
            if (sizes.getLength() != 1) throw new IOException("无法检测页面尺寸。");
            Element size = (Element)sizes.item(0);
            width = positive(size.getAttribute("cx")); height = positive(size.getAttribute("cy"));
            resized = Math.abs((double)width / height / (4.0 / 3.0) - 1) <= 0.005;
            newWidth = resized ? Math.round(height * 16.0 / 9.0) : width;
            long delta = Math.round((newWidth - width) / 2.0);
            if (resized) {
                size.setAttribute("cx", Long.toString(newWidth));
                size.setAttribute("type", "screen16x9");
            } else {
                warnings.add("输入比例为 " + String.format(Locale.ROOT, "%.4f", (double)width / height) +
                    "，不是 4:3，保留页面尺寸；仍执行字体替换。");
            }
            boolean advanced = false, backgrounds = false, customCoordinates = false, smartArt = false, legacy = false, embeddedFonts = false, externalLinks = false;
            long[] expanded = {0};
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
                int processed = 0;
                for (ZipEntry entry : entries) {
                    String name = entry.getName();
                    legacy |= name.toLowerCase(Locale.ROOT).endsWith(".vml");
                    ZipEntry target = new ZipEntry(name);
                    if (entry.getTime() >= 0) target.setTime(entry.getTime());
                    out.putNextEntry(target);
                    if (!entry.isDirectory()) {
                        boolean xml = name.startsWith("ppt/") && name.endsWith(".xml");
                        if (xml) {
                            byte[] original = readBounded(zip.getInputStream(entry), MAX_XML, expanded);
                            Document part = name.equals("ppt/presentation.xml") ? doc : parse(original);
                            int count = replaceFonts(part);
                            fonts += count;
                            boolean changed = count > 0 || (resized && name.equals("ppt/presentation.xml"));
                            if (resized && isSlidePart(part)) {
                                int shifted = shiftTopLevel(part, delta, warnings, name);
                                moved += shifted;
                                changed |= shifted > 0;
                                backgrounds |= part.getElementsByTagNameNS(P, "bg").getLength() > 0;
                                customCoordinates |= part.getElementsByTagNameNS(P, "animMotion").getLength() > 0;
                            }
                            advanced |= part.getElementsByTagNameNS(P, "oleObj").getLength() > 0 ||
                                part.getElementsByTagNameNS(P, "contentPart").getLength() > 0;
                            smartArt |= part.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/diagram", "relIds").getLength() > 0;
                            embeddedFonts |= part.getElementsByTagNameNS(P, "embeddedFontLst").getLength() > 0;
                            out.write(changed ? serialize(part) : original);
                        } else if (name.endsWith(".rels")) {
                            byte[] original = readBounded(zip.getInputStream(entry), MAX_XML, expanded);
                            NodeList rels = parse(original).getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship");
                            for (int j = 0; j < rels.getLength(); j++)
                                externalLinks |= "External".equals(((Element)rels.item(j)).getAttribute("TargetMode"));
                            out.write(original);
                        } else {
                            try (InputStream in = zip.getInputStream(entry)) {
                                byte[] buffer = new byte[65536]; int n;
                                while ((n = in.read(buffer)) != -1) {
                                    expanded[0] += n;
                                    if (expanded[0] > MAX_EXPANDED) throw new IOException("解压后大小超过 512 MB 安全上限。");
                                    out.write(buffer, 0, n);
                                }
                            }
                        }
                    }
                    out.closeEntry();
                    progress.accept(++processed * 99 / entries.size());
                }
            }
            if (backgrounds) warnings.add("页面背景仍按新页面宽度铺设，背景填充可能改变显示范围；普通图片对象未缩放。");
            if (advanced) warnings.add("OLE / 扩展内容已保留；其显示与编辑依赖 PowerPoint 及相应组件，请人工检查。");
            if (smartArt) warnings.add("SmartArt 的原始数据及外层坐标已保留；自动布局和字体回退效果请在 PowerPoint 中复查。");
            if (legacy) warnings.add("检测到旧式 VML 内容：原样保留，内部坐标及样式字体不处理，请人工检查。");
            if (embeddedFonts) warnings.add("嵌入字体资源及其名称绑定保留；替换为 Arial 的文本不再使用原微软雅黑嵌入资源，显示依赖目标系统字体。");
            if (externalLinks) warnings.add("检测到外部链接：链接原样保留，不下载外部内容；链接媒体或数据源在离线电脑上可能不可用。");
            if (customCoordinates) warnings.add("检测到动画运动路径：动画 XML 保留，按页面比例定义的运动路径可能改变轨迹，请人工检查。");
            if (fonts > 0) warnings.add("Arial 不包含完整中文字形；中文将由系统回退字体显示，换行、字宽可能变化，请在目标 Windows 上检查。");
            // No REPLACE_EXISTING: an existing output is never overwritten.
            Files.move(temporary, output);
            progress.accept(100);
            return new Result(width, height, newWidth, resized, moved, fonts, warnings);
        } catch (ZipException ex) {
            throw new IOException("无法打开 PPTX：文件损坏、已加密，或不是标准 ZIP 格式。", ex);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isSlidePart(Document doc) {
        Element root = doc.getDocumentElement();
        return P.equals(root.getNamespaceURI()) && Arrays.asList("sld", "sldLayout", "sldMaster").contains(root.getLocalName());
    }

    private static int shiftTopLevel(Document doc, long delta, List<String> warnings, String part) throws IOException {
        int moved = 0;
        NodeList trees = doc.getElementsByTagNameNS(P, "spTree");
        for (int i = 0; i < trees.getLength(); i++) {
            Element tree = (Element)trees.item(i);
            for (Node node = tree.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element)) continue;
                Element shape = (Element)node;
                String kind = shape.getLocalName();
                if (kind.equals("nvGrpSpPr") || kind.equals("grpSpPr") || kind.equals("extLst")) continue;
                if (!P.equals(shape.getNamespaceURI())) {
                    // e.g. mc:AlternateContent: each alternative is an independent rendering choice.
                    moved += shiftAlternatives(shape, delta, warnings, part);
                    continue;
                }
                if (!Arrays.asList("sp", "pic", "cxnSp", "grpSp", "graphicFrame", "contentPart").contains(kind)) {
                    warnings.add(part + "：保留未识别对象 " + kind + "，无法保证居中。");
                    continue;
                }
                Element xfrm = findTransform(shape);
                if (xfrm == null) {
                    if (shape.getElementsByTagNameNS(P, "ph").getLength() == 0)
                        warnings.add(part + "：" + kind + " 没有显式坐标，原样保留。");
                    continue; // Placeholder coordinates inherit the shifted layout/master.
                }
                if (shiftOffset(xfrm, delta)) moved++;
                else if (shape.getElementsByTagNameNS(P, "ph").getLength() == 0)
                    warnings.add(part + "：" + kind + " 缺少位置坐标，原样保留。");
            }
        }
        return moved;
    }

    private static int shiftAlternatives(Element wrapper, long delta, List<String> warnings, String part) throws IOException {
        int moved = 0;
        for (Node n = wrapper.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element)) continue;
            Element e = (Element)n;
            if (P.equals(e.getNamespaceURI()) && Arrays.asList("sp", "pic", "cxnSp", "grpSp", "graphicFrame").contains(e.getLocalName())) {
                Element t = findTransform(e);
                if (t != null && shiftOffset(t, delta)) moved++;
                else warnings.add(part + "：替代内容对象无法确定坐标，原样保留。");
            } else if ("AlternateContent".equals(e.getLocalName()) || "Choice".equals(e.getLocalName()) || "Fallback".equals(e.getLocalName())) {
                moved += shiftAlternatives(e, delta, warnings, part);
            } else warnings.add(part + "：未识别替代内容 " + e.getLocalName() + "，原样保留，无法保证居中。");
        }
        return moved;
    }

    private static Element findTransform(Element shape) {
        for (Node n = shape.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element)) continue;
            Element e = (Element)n;
            if (e.getLocalName().equals("xfrm")) return e;
            if (e.getLocalName().equals("spPr") || e.getLocalName().equals("grpSpPr")) {
                for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling())
                    if (c instanceof Element && ((Element)c).getLocalName().equals("xfrm")) return (Element)c;
            }
        }
        return null;
    }

    private static boolean shiftOffset(Element transform, long delta) throws IOException {
        for (Node n = transform.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element)n).getLocalName().equals("off")) {
                Element e = (Element)n;
                try { e.setAttribute("x", Long.toString(Math.addExact(Long.parseLong(e.getAttribute("x")), delta))); }
                catch (NumberFormatException | ArithmeticException ex) { throw new IOException("对象水平坐标无效。", ex); }
                return true;
            }
        }
        return false;
    }

    private static int replaceFonts(Document doc) {
        int count = 0;
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element)all.item(i);
            // Resource names must continue to match the retained embedded font binaries.
            if (P.equals(e.getNamespaceURI()) && e.getLocalName().equals("font") &&
                e.getParentNode() instanceof Element && ((Element)e.getParentNode()).getLocalName().equals("embeddedFont")) continue;
            if (e.hasAttribute("typeface")) {
                String font = e.getAttribute("typeface");
                if (font.equalsIgnoreCase("Microsoft YaHei") || font.equals("微软雅黑")) {
                    e.setAttribute("typeface", "Arial"); count++;
                }
            }
        }
        return count;
    }

    static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false); f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        return b.parse(new ByteArrayInputStream(xml));
    }

    private static byte[] serialize(Document doc) throws Exception {
        TransformerFactory f = TransformerFactory.newInstance();
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer t = f.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        t.transform(new DOMSource(doc), new StreamResult(bytes));
        return bytes.toByteArray();
    }

    private static long positive(String value) throws IOException {
        try { long n = Long.parseLong(value); if (n <= 0 || n > 100_000_000) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IOException("页面尺寸无效或超出支持范围。", e); }
    }
    private static byte[] readXml(ZipFile zip, ZipEntry entry) throws IOException {
        return readBounded(zip.getInputStream(entry), MAX_XML, new long[]{0});
    }
    private static byte[] readBounded(InputStream stream, int limit, long[] expanded) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) {
                expanded[0] += n;
                if (out.size() + n > limit || expanded[0] > MAX_EXPANDED)
                    throw new IOException("PPTX 解压后大小超过安全上限。");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
