package com.pptconverter;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Runnable tests without external dependencies. Throws on every failed assertion. */
public final class ConverterTest {
    private static final Path SAMPLE = Path.of("examples/sample-4x3.pptx");
    private static Path temp;
    private static int tests;
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private static void test(String name, Checked action) throws Exception {
        action.run(); tests++; System.out.println("PASS " + name);
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        temp = Files.createTempDirectory("ppt-converter-tests-");
        try {
            test("4:3 resize, centering and image/table/chart/group preservation", ConverterTest::geometry);
            test("layout/master inheritance remains valid", ConverterTest::inheritance);
            test("font aliases in slides, notes, charts and themes; other fonts unchanged", ConverterTest::fonts);
            test("16:9 dimensions and coordinates unchanged", () -> unchanged(Path.of("examples/sample-16x9.pptx")));
            test("custom aspect dimensions and coordinates unchanged", ConverterTest::customAspect);
            test("corrupt input isolated, original untouched and temporary outputs removed", ConverterTest::corrupt);
            test("existing output and original overwrite refused", ConverterTest::overwrite);
            test("Strict OOXML rejected clearly", ConverterTest::strict);
            test("signed package rejected before mutation", ConverterTest::signed);
            test("XXE / DTD rejected", ConverterTest::xxe);
            test("invalid coordinates rejected without partial output", ConverterTest::invalidCoordinates);
            test("oversized XML rejected", ConverterTest::xmlLimit);
            test("alternative content shifted once; motion path preserved with warning", ConverterTest::alternatives);
            test("HTTP batch, failure isolation, downloads, logs, authorization and deletion", ConverterTest::http);
            System.out.println("RESULT: " + tests + " tests passed, 0 failed, 0 skipped");
        } finally {
            try (var paths = Files.walk(temp)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    private static Path output() { return temp.resolve(UUID.randomUUID() + ".pptx"); }
    private static PptxConverter.Result convert(Path input, Path output) throws Exception { return new PptxConverter().convert(input, output, p -> {}); }
    private static Map<String, byte[]> parts(Path file) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipFile z = new ZipFile(file.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) try (InputStream in = z.getInputStream(e)) { parts.put(e.getName(), in.readAllBytes()); }
        }
        return parts;
    }
    private static byte[] xml(Document d) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(out)); return out.toByteArray();
    }
    private static Path changed(String name, Consumer<Document> change) throws Exception {
        Map<String, byte[]> parts = parts(SAMPLE); Document d = PptxConverter.parse(parts.get(name));
        change.accept(d); parts.put(name, xml(d)); return packageFile(parts);
    }
    private static Path packageFile(Map<String, byte[]> parts) throws IOException {
        Path path = output();
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var e : parts.entrySet()) { z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue()); z.closeEntry(); }
        }
        return path;
    }
    private static Element first(Document d, String ns, String name) { return (Element)d.getElementsByTagNameNS(ns, name).item(0); }
    private static void geometry() throws Exception {
        Path out = output(); List<Integer> progress = new ArrayList<>();
        PptxConverter.Result r = new PptxConverter().convert(SAMPLE, out, progress::add);
        check(r.resized() && r.height() == 6858000 && r.newWidth() == 12192000 && r.oldWidth() == 9144000, "Incorrect dimensions");
        check(r.movedObjects() > 10, "Expected shapes/layouts/master transforms");
        check(progress.get(progress.size()-1) == 100, "No completion progress");
        for (int i=1;i<progress.size();i++)check(progress.get(i)>=progress.get(i-1),"Progress regressed");
        Map<String, byte[]> before = parts(SAMPLE), after = parts(out);
        check(before.keySet().equals(after.keySet()), "A package part was lost");
        int images = 0, charts = 0, embeddings = 0;
        for (String name : before.keySet()) {
            if (!name.startsWith("ppt/") || !name.endsWith(".xml")) {
                check(Arrays.equals(before.get(name), after.get(name)), "Binary/relationship part changed: " + name);
                if(name.startsWith("ppt/media/"))images++;
                if(name.startsWith("ppt/embeddings/"))embeddings++;
            } else {
                Document a = PptxConverter.parse(before.get(name)), b = PptxConverter.parse(after.get(name));
                compareElements(a.getDocumentElement(), b.getDocumentElement(), r.newWidth()-r.oldWidth());
                if(name.startsWith("ppt/charts/"))charts++;
            }
        }
        check(images>0 && charts>0 && embeddings>0,"Fixture did not exercise pictures, charts and embedded workbook");
        check(r.warnings().stream().anyMatch(w->w.contains("背景")),"Background warning missing");
    }
    // Compare all elements/attributes, allowing only the exact intended edits.
    private static void compareElements(Element a, Element b, long widthChange) {
        check(a.getTagName().equals(b.getTagName()), "Element changed");
        NamedNodeMap attributes = a.getAttributes();
        for (int j=0;j<attributes.getLength();j++) {
            Node attr=attributes.item(j); String expected=attr.getNodeValue();
            if(attr.getNodeName().equals("typeface") && (expected.equals("微软雅黑")||expected.equalsIgnoreCase("Microsoft YaHei")))expected="Arial";
            if(PptxConverter.P.equals(a.getNamespaceURI()) && a.getLocalName().equals("sldSz")) {
                if(attr.getNodeName().equals("cx"))expected=Long.toString(Long.parseLong(expected)+widthChange);
                if(attr.getNodeName().equals("type") && widthChange!=0)expected="screen16x9";
            }
            if(a.getLocalName().equals("off") && attr.getNodeName().equals("x") && topLevelOffset(a))
                expected=Long.toString(Long.parseLong(expected)+Math.round(widthChange/2.0));
            check(expected.equals(b.getAttribute(attr.getNodeName())), "Unexpected change to " + a.getTagName()+" @"+attr.getNodeName()+": "+expected+" != "+b.getAttribute(attr.getNodeName()));
        }
        List<Element> ac=children(a),bc=children(b);check(ac.size()==bc.size(),"Child structure changed");
        for(int i=0;i<ac.size();i++)compareElements(ac.get(i),bc.get(i),widthChange);
        if(ac.isEmpty())check(a.getTextContent().equals(b.getTextContent()),"Text content changed");
    }
    private static boolean topLevelOffset(Element e) {
        if(!Set.of("sld","sldLayout","sldMaster").contains(e.getOwnerDocument().getDocumentElement().getLocalName()))return false;
        Node n=e.getParentNode(); if(!(n instanceof Element x) || !x.getLocalName().equals("xfrm"))return false;
        n=n.getParentNode(); if(n instanceof Element p && Set.of("spPr","grpSpPr").contains(p.getLocalName()))n=n.getParentNode();
        return n!=null && n.getParentNode() instanceof Element tree && tree.getLocalName().equals("spTree");
    }
    private static List<Element> children(Element e) {
        List<Element> elements=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element el)elements.add(el);
        return elements;
    }
    private static void inheritance() throws Exception {
        Path out=output();convert(SAMPLE,out);Map<String,byte[]> before=parts(SAMPLE),after=parts(out);
        Document slide=PptxConverter.parse(before.get("ppt/slides/slide2.xml"));
        check(slide.getElementsByTagNameNS(PptxConverter.A,"xfrm").getLength()==0,"Fixture must exercise inherited placeholders");
        check(PptxConverter.parse(after.get("ppt/slides/slide2.xml")).getElementsByTagNameNS(PptxConverter.A,"xfrm").getLength()==0,"Inheritance replaced unnecessarily");
        check(!Arrays.equals(before.get("ppt/slideMasters/slideMaster1.xml"),after.get("ppt/slideMasters/slideMaster1.xml")),"Master not shifted");
        check(Arrays.equals(before.get("ppt/slideLayouts/slideLayout2.xml"),after.get("ppt/slideLayouts/slideLayout2.xml")),"Fully inherited layout should remain unchanged");
        boolean explicitLayout=false;
        for(String name:before.keySet())if(name.startsWith("ppt/slideLayouts/") && name.endsWith(".xml")){
            Document layout=PptxConverter.parse(before.get(name));
            NodeList offsets=layout.getElementsByTagNameNS(PptxConverter.A,"off");
            boolean hasPosition=false;
            for(int i=0;i<offsets.getLength();i++)hasPosition|=topLevelOffset((Element)offsets.item(i));
            if(hasPosition){
                explicitLayout=true;check(!Arrays.equals(before.get(name),after.get(name)),"Explicit layout coordinates not shifted: "+name);
            }
        }
        check(explicitLayout,"Fixture must contain explicit layout transforms");
    }
    private static void fonts() throws Exception {
        Path themed=changed("ppt/theme/theme1.xml",d->first(d,PptxConverter.A,"latin").setAttribute("typeface","Microsoft YaHei"));
        Path out=output();PptxConverter.Result r=convert(themed,out);int count=0;
        for(var e:parts(out).entrySet())if(e.getKey().startsWith("ppt/")&&e.getKey().endsWith(".xml")){
            Document d=PptxConverter.parse(e.getValue());NodeList all=d.getElementsByTagName("*");
            for(int i=0;i<all.getLength();i++){
                String font=((Element)all.item(i)).getAttribute("typeface");
                check(!font.equals("微软雅黑")&&!font.equalsIgnoreCase("Microsoft YaHei"),"Missed font alias");if(font.equals("Arial"))count++;
            }
        }
        check(count>10 && r.replacedFonts()>10,"Fonts were not exercised");
        String text=new String(parts(out).get("ppt/slides/slide1.xml"),StandardCharsets.UTF_8);
        check(text.contains("Times New Roman"),"Other font changed");
        check(r.warnings().stream().anyMatch(w->w.contains("中文")),"Fallback warning missing");
    }
    private static void unchanged(Path source) throws Exception {
        Path out=output();PptxConverter.Result r=convert(source,out);
        check(!r.resized() && r.oldWidth()==r.newWidth(),"Non-4:3 resized");
        Map<String,byte[]> a=parts(source),b=parts(out);
        for(String n:a.keySet())if(n.startsWith("ppt/")&&n.endsWith(".xml"))compareElements(PptxConverter.parse(a.get(n)).getDocumentElement(),PptxConverter.parse(b.get(n)).getDocumentElement(),0);
    }
    private static void customAspect() throws Exception {
        unchanged(changed("ppt/presentation.xml",d->first(d,PptxConverter.P,"sldSz").setAttribute("cx","6858000")));
    }
    private static void fails(Path source, Path out, String expected) throws Exception {
        byte[] original=Files.readAllBytes(source);boolean failed=false;
        try { convert(source,out); }catch(Exception e){failed=true;check(e.getMessage().contains(expected),"Wrong error: "+e.getMessage());}
        check(failed,"Expected conversion failure");check(Arrays.equals(original,Files.readAllBytes(source)),"Original was modified");
        check(!Files.exists(out),"Partial output retained");
        try(var files=Files.list(temp)){check(files.noneMatch(p->p.getFileName().toString().startsWith(".ppt-convert-")),"Temporary artifact left behind");}
    }
    private static void corrupt() throws Exception {fails(Path.of("examples/invalid.pptx"),output(),"无法打开");}
    private static void overwrite() throws Exception {
        Path out=output();Files.writeString(out,"keep me");boolean refused=false;
        try{convert(SAMPLE,out);}catch(IOException e){refused=true;}
        check(refused&&Files.readString(out).equals("keep me"),"Existing output overwritten");
        byte[] original=Files.readAllBytes(SAMPLE);refused=false;
        try{convert(SAMPLE,SAMPLE);}catch(IOException e){refused=true;}
        check(refused&&Arrays.equals(original,Files.readAllBytes(SAMPLE)),"Original overwritten");
    }
    private static void strict() throws Exception {
        Map<String,byte[]> p=parts(SAMPLE);
        p.put("ppt/presentation.xml",new String(p.get("ppt/presentation.xml"),StandardCharsets.UTF_8).replace(PptxConverter.P,"http://purl.oclc.org/ooxml/presentationml/main").getBytes(StandardCharsets.UTF_8));
        fails(packageFile(p),output(),"Strict OOXML");
    }
    private static void signed() throws Exception {
        Map<String,byte[]> p=parts(SAMPLE);p.put("_xmlsignatures/sig1.xml","<Signature/>".getBytes(StandardCharsets.UTF_8));fails(packageFile(p),output(),"数字签名");
    }
    private static void xxe() throws Exception {
        Map<String,byte[]> p=parts(SAMPLE);String s=new String(p.get("ppt/presentation.xml"),StandardCharsets.UTF_8);
        s=s.substring(s.indexOf("?>")+2);p.put("ppt/presentation.xml",("<!DOCTYPE p:presentation [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+s).getBytes(StandardCharsets.UTF_8));
        fails(packageFile(p),output(),"DOCTYPE");
    }
    private static void invalidCoordinates() throws Exception {
        fails(changed("ppt/slides/slide1.xml",d->first(d,PptxConverter.A,"off").setAttribute("x","invalid")),output(),"水平坐标无效");
    }
    private static void xmlLimit() throws Exception {
        Map<String,byte[]> p=parts(SAMPLE);byte[] huge=new byte[16*1024*1024+1];Arrays.fill(huge,(byte)' ');p.put("ppt/slides/slide1.xml",huge);
        fails(packageFile(p),output(),"安全上限");
    }
    private static void alternatives() throws Exception {
        Path source=changed("ppt/slides/slide1.xml",d->{
            Element tree=first(d,PptxConverter.P,"spTree");
            Element shape=(Element)d.getElementsByTagNameNS(PptxConverter.P,"sp").item(0);
            Element alt=d.createElementNS("http://schemas.openxmlformats.org/markup-compatibility/2006","mc:AlternateContent");
            alt.setAttributeNS("http://www.w3.org/2000/xmlns/","xmlns:mc","http://schemas.openxmlformats.org/markup-compatibility/2006");
            for(String type:List.of("Choice","Fallback")){
                Element choice=d.createElementNS(alt.getNamespaceURI(),"mc:"+type);
                if(type.equals("Choice"))choice.setAttribute("Requires","p");
                choice.appendChild(shape.cloneNode(true));alt.appendChild(choice);
            }
            tree.appendChild(alt);
            Element timing=d.createElementNS(PptxConverter.P,"p:timing"),motion=d.createElementNS(PptxConverter.P,"p:animMotion");
            motion.setAttribute("path","M 0 0 L 0.25 0 E");timing.appendChild(motion);d.getDocumentElement().appendChild(timing);
        });
        Path out=output();PptxConverter.Result r=convert(source,out);
        Document a=PptxConverter.parse(parts(source).get("ppt/slides/slide1.xml")),b=PptxConverter.parse(parts(out).get("ppt/slides/slide1.xml"));
        NodeList before=a.getElementsByTagNameNS("http://schemas.openxmlformats.org/markup-compatibility/2006","AlternateContent");
        Element aa=(Element)before.item(0),bb=(Element)b.getElementsByTagNameNS(aa.getNamespaceURI(),"AlternateContent").item(0);
        NodeList offsets=aa.getElementsByTagNameNS(PptxConverter.A,"off"),after=bb.getElementsByTagNameNS(PptxConverter.A,"off");
        for(int i=0;i<offsets.getLength();i++)check(Long.parseLong(((Element)after.item(i)).getAttribute("x"))-Long.parseLong(((Element)offsets.item(i)).getAttribute("x"))==1524000,"Alternate choice not shifted exactly once");
        check(first(b,PptxConverter.P,"animMotion").getAttribute("path").equals("M 0 0 L 0.25 0 E"),"Animation path changed");
        check(r.warnings().stream().anyMatch(w->w.contains("运动路径")),"Motion warning missing");
    }

    private static HttpResponse<byte[]> request(HttpClient client, URI base, String path, String method, String token, byte[] body) throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder(base.resolve(path)).timeout(java.time.Duration.ofSeconds(20));
        if(token!=null)b.header("X-App-Token",token);
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String body(HttpResponse<byte[]> r) { return new String(r.body(),StandardCharsets.UTF_8); }
    private static String id(String json) { Matcher m=Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);check(m.find(),"ID missing");return m.group(1); }
    private static void http() throws Exception {
        try(App app=new App(0,temp)) {
            app.start();HttpClient client=HttpClient.newHttpClient();URI base=URI.create("http://127.0.0.1:"+app.port());
            HttpResponse<byte[]> page=request(client,base,"/","GET",null,null);
            check(page.statusCode()==200 && body(page).contains("开始转换"),"Web UI not served");
            Matcher m=Pattern.compile("name=\"app-token\" content=\"([^\"]+)\"").matcher(body(page));check(m.find(),"No session token");String token=m.group(1);
            check(request(client,base,"/api/batches","POST",null,null).statusCode()==403,"Unauthenticated API accepted");
            var evil=client.send(HttpRequest.newBuilder(base.resolve("/api/batches")).header("Origin","https://evil.example").header("X-App-Token",token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(evil.statusCode()==403,"Cross-origin request accepted");
            var created=request(client,base,"/api/batches","POST",token,null);check(created.statusCode()==201,"Create failed");String batch=id(body(created));String route="/api/batches/"+batch;
            String unicode="中文样例 & \".pptx";
            String encoded=URLEncoder.encode(unicode,StandardCharsets.UTF_8);
            var file=request(client,base,route+"/files?name="+encoded,"POST",token,Files.readAllBytes(SAMPLE));check(file.statusCode()==202,"Upload failed");String fileId=id(body(file));
            check(request(client,base,route+"/files?name="+encoded,"POST",token,Files.readAllBytes(SAMPLE)).statusCode()==202,"Duplicate-name upload failed");
            check(request(client,base,route+"/files?name=invalid.pptx","POST",token,Files.readAllBytes(Path.of("examples/invalid.pptx"))).statusCode()==202,"Invalid input not queued");
            check(request(client,base,route+"/files?name=empty.pptx","POST",token,new byte[0]).statusCode()==400,"Empty input accepted");
            String status="";
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(System.nanoTime()<until){status=body(request(client,base,route,"GET",token,null));if(status.contains("\"complete\":true"))break;Thread.sleep(50);}
            check(status.contains("\"complete\":true"),"Batch did not finish");
            check(status.split("\"status\":\"done\"",-1).length-1==2 && status.split("\"status\":\"failed\"",-1).length-1==2,"Batch failure isolation broken: "+status);
            var download=request(client,base,route+"/files/"+fileId+"/download","GET",token,null);check(download.statusCode()==200,"Single download failed");
            Path downloaded=output();Files.write(downloaded,download.body());check(parts(downloaded).containsKey("ppt/charts/chart1.xml"),"Chart lost in download");
            var archive=request(client,base,route+"/download","GET",token,null);check(archive.statusCode()==200,"Batch download failed");
            Set<String> entries=new HashSet<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(archive.body()))){ZipEntry e;while((e=z.getNextEntry())!=null){check(entries.add(e.getName()),"Duplicate ZIP name");z.transferTo(OutputStream.nullOutputStream());}}
            check(entries.size()==3 && entries.contains("conversion-log.txt"),"ZIP results/log missing");
            var log=request(client,base,route+"/log","GET",token,null);check(log.statusCode()==200&&body(log).contains("转换失败")&&body(log).contains("完成："),"Conversion log incomplete");
            check(request(client,base,route,"DELETE",token,null).statusCode()==200,"Delete failed");
            check(request(client,base,route,"GET",token,null).statusCode()==404,"Deleted batch still available");
        }
    }
}
