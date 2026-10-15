package com.pptconverter;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;
import org.apache.poi.poifs.crypt.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/** Real Office AES roundtrips and enhanced HTTP workflow on Java 8. */
public final class OfficeEncryptionTest {
    private static final Path SAMPLE=Paths.get("examples/sample-4x3.pptx");
    private static Path work;
    private static final OfficeEncryption crypto=new OfficeEncryption();
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static Path path(String name){return work.resolve(name);}
    public static void main(String[] args)throws Exception{
        work=Files.createTempDirectory("office-tests-");
        try{
            Path locked=path("locked.pptx");crypto.encrypt(SAMPLE,locked,"3609");
            check(crypto.isEncrypted(locked),"Output is not an Office encrypted container");
            Path restored=path("restored.pptx");crypto.decrypt(locked,restored,"3609");
            check(Arrays.equals(Files.readAllBytes(SAMPLE),Files.readAllBytes(restored)),"Roundtrip changed package bytes");
            System.out.println("PASS Office Agile encryption/decryption preserves exact PPTX bytes");
            boolean refused=false;try{crypto.decrypt(locked,path("wrong.pptx"),"wrong");}catch(IOException e){refused=true;}
            check(refused&&!Files.exists(path("wrong.pptx")),"Wrong password accepted or partial plaintext retained");
            System.out.println("PASS wrong Office password rejected without partial plaintext");
            ConversionService service=new ConversionService(true);
            Path out=path("converted.pptx");PptxConverter.Result r=service.convert(locked,out,p->{});
            check(r.resized()&&r.newWidth()==12192000&&r.height()==6858000,"Wrong enhanced dimensions");
            check(crypto.isEncrypted(out),"Converted output not protected");
            Path plain=path("converted-plain.pptx");crypto.decrypt(out,plain,"3609");checkConverted(plain);
            PptxConverter.Result company=service.convert(Paths.get("examples/sample-company-3609.pptx"),path("company-converted.pptx"),p->{});
            check(company.oldWidth()==9906000&&company.newWidth()==12192000&&company.height()==6858000,"Independent producer's encrypted company file not supported");
            System.out.println("PASS decrypt 3609 (including independent company fixture) → resize/fonts → re-encrypt 3609");
            Path fromPlain=path("from-plain.pptx");service.convert(SAMPLE,fromPlain,p->{});check(crypto.isEncrypted(fromPlain),"Unprotected input did not get output protection");
            System.out.println("PASS enhanced edition also protects outputs from plaintext inputs");
            Path standard=path("standard.pptx");
            try(POIFSFileSystem fs=new POIFSFileSystem()){
                EncryptionInfo info=new EncryptionInfo(EncryptionMode.standard);Encryptor enc=info.getEncryptor();enc.confirmPassword("3609");
                try(OutputStream stream=enc.getDataStream(fs)){Files.copy(SAMPLE,stream);}
                try(OutputStream stream=Files.newOutputStream(standard)){fs.writeFilesystem(stream);}
            }
            service.convert(standard,path("standard-converted.pptx"),p->{});
            System.out.println("PASS older standard Office encryption input supported");
            byte[] preserved=Files.readAllBytes(out);refused=false;
            try{service.convert(SAMPLE,out,p->{});}catch(IOException e){refused=true;}
            check(refused&&Arrays.equals(preserved,Files.readAllBytes(out)),"Existing output overwritten");
            Path wrong=path("wrong-input.pptx");crypto.encrypt(SAMPLE,wrong,"different");refused=false;
            byte[] original=Files.readAllBytes(wrong);
            try{service.convert(wrong,path("should-not-exist.pptx"),p->{});}catch(IOException e){refused=true;}
            check(refused&&!Files.exists(path("should-not-exist.pptx"))&&Arrays.equals(original,Files.readAllBytes(wrong)),"Failure damaged source/output");
            try(Stream<Path> paths=Files.list(work)){check(paths.noneMatch(p->p.getFileName().toString().startsWith("office-work-")),"Plaintext working directory left behind");}
            System.out.println("PASS output overwrite refusal, original preservation and plaintext cleanup");
            http(locked,wrong);
            // Keep one generated output for independent msoffcrypto validation, if requested.
            if(args.length==1)Files.copy(out,Paths.get(args[0]));
            System.out.println("RESULT: 7 enhanced tests passed, 0 failed, 0 skipped");
        }finally{try(Stream<Path> s=Files.walk(work)){for(Path p:s.sorted(Comparator.reverseOrder()).collect(Collectors.toList()))Files.deleteIfExists(p);}}
    }
    private static void checkConverted(Path p)throws Exception{
        try(ZipFile z=new ZipFile(p.toFile())){
            check(z.getEntry("ppt/charts/chart1.xml")!=null,"Chart lost");
            String xml=new String(read(z.getInputStream(z.getEntry("ppt/slides/slide1.xml"))),StandardCharsets.UTF_8);
            check(xml.contains("SimHei")&&xml.contains("Arial"),"Chinese/English font policy missing");
        }
    }
    private static byte[] read(InputStream in)throws IOException{try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static byte[] request(URI base,String url,String method,String token,byte[] body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)base.resolve(url).toURL().openConnection();
        try{
            c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestMethod(method);if(token!=null)c.setRequestProperty("X-App-Token",token);
            if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int code=c.getResponseCode();byte[] data=read(code>=400?c.getErrorStream():c.getInputStream());
            check(code<400,"HTTP failure: "+new String(data,StandardCharsets.UTF_8));return data;
        }finally{c.disconnect();}
    }
    private static String str(byte[] b){return new String(b,StandardCharsets.UTF_8);}
    private static String id(String json){Matcher m=Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);check(m.find(),"No ID");return m.group(1);}
    private static void http(Path locked,Path wrong)throws Exception{
        try(App app=new App(0,work,true)){
            app.start();URI base=URI.create("http://127.0.0.1:"+app.port());
            String page=str(request(base,"/","GET",null,null));check(page.contains("所有输出设置打开密码 3609"),"Enhanced policy not visible");
            Matcher m=Pattern.compile("name=\"app-token\" content=\"([^\"]+)\"").matcher(page);check(m.find(),"No token");String token=m.group(1);
            String batch=id(str(request(base,"/api/batches","POST",token,new byte[0]))),route="/api/batches/"+batch;
            String first=id(str(request(base,route+"/files?name=locked.pptx","POST",token,Files.readAllBytes(locked))));
            request(base,route+"/files?name=plain.pptx","POST",token,Files.readAllBytes(SAMPLE));
            request(base,route+"/files?name=wrong.pptx","POST",token,Files.readAllBytes(wrong));
            String status="";long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while(System.nanoTime()<deadline){status=str(request(base,route,"GET",token,null));if(status.contains("\"complete\":true"))break;Thread.sleep(50);}
            check(status.contains("\"complete\":true")&&status.split("\"status\":\"done\"",-1).length-1==2&&status.split("\"status\":\"failed\"",-1).length-1==1,"Enhanced batch isolation failed: "+status);
            check(status.contains("\"outputEncrypted\":true"),"Output protection not reported");
            Path download=path("http-download.pptx");Files.write(download,request(base,route+"/files/"+first+"/download","GET",token,null));
            check(crypto.isEncrypted(download),"HTTP download not Office-encrypted");Path plain=path("http-plain.pptx");crypto.decrypt(download,plain,"3609");checkConverted(plain);
            byte[] archive=request(base,route+"/download","GET",token,null);int files=0;
            try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;while((e=z.getNextEntry())!=null){files++;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=z.read(b))!=-1)out.write(b,0,n);if(e.getName().endsWith(".pptx"))check(out.toByteArray()[0]==(byte)0xd0,"Batch result not Office encrypted");}}
            check(files==3,"ZIP results/log missing");request(base,route,"DELETE",token,null);
            System.out.println("PASS enhanced HTTP upload, known/wrong passwords, batch isolation and protected downloads");
        }
    }
}
