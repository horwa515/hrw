package com.pptconverter;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/** Isolates plaintext intermediates and publishes an output only after all steps succeed. */
public final class ConversionService {
    private final OfficeProtection protection;
    public ConversionService(boolean enhanced) throws IOException {
        if(!enhanced){protection=null;return;}
        try { protection=(OfficeProtection)Class.forName("com.pptconverter.OfficeEncryption").newInstance(); }
        catch(ReflectiveOperationException|LinkageError e){throw new IOException("增强版加密组件缺失，请完整解压增强版 ZIP，并使用其中的 Start.cmd。",e);}
    }
    public boolean enhanced(){return protection!=null;}
    public PptxConverter.Result convert(Path input,Path output,IntConsumer progress) throws Exception {
        if(protection==null)return new PptxConverter().convert(input,output,progress);
        if(Files.exists(output))throw new IOException("输出文件已存在，拒绝覆盖。");
        Path work=Files.createTempDirectory(output.toAbsolutePath().getParent(),"office-work-");
        try {
            Path plain=input;
            if(protection.isEncrypted(input)){
                plain=work.resolve("decrypted.pptx");protection.decrypt(input,plain,"3609");
            }
            progress.accept(10);
            Path converted=work.resolve("converted.pptx");
            PptxConverter.Result result=new PptxConverter().convert(plain,converted,p->progress.accept(10+p*7/10));
            progress.accept(85);protection.encrypt(converted,output,"3609");progress.accept(100);
            return result;
        } finally {
            try(Stream<Path> paths=Files.walk(work)){
                for(Path p:paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()))Files.deleteIfExists(p);
            }
        }
    }
}
