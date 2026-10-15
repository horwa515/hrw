package com.pptconverter;

import java.io.*;
import java.nio.file.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.crypt.*;

/** Standard Office password-to-open encryption (Agile AES), not ZIP encryption. */
public final class OfficeEncryption implements OfficeProtection {
    static {
        if(System.getProperty("log4j2.loggerContextFactory")==null)
            System.setProperty("log4j2.loggerContextFactory","org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
    }
    @Override public boolean isEncrypted(Path input) throws IOException {
        try(InputStream in=Files.newInputStream(input)){
            byte[] signature=new byte[8];int n=0,k;
            while(n<8&&(k=in.read(signature,n,8-n))>0)n+=k;
            return n==8 && signature[0]==(byte)0xd0 && signature[1]==(byte)0xcf && signature[2]==0x11 && signature[3]==(byte)0xe0 &&
                signature[4]==(byte)0xa1 && signature[5]==(byte)0xb1 && signature[6]==0x1a && signature[7]==(byte)0xe1;
        }
    }
    @Override public void decrypt(Path input,Path output,String password) throws Exception {
        if(Files.exists(output))throw new IOException("解密输出已存在，拒绝覆盖。");
        boolean success=false;
        try(POIFSFileSystem fs=new POIFSFileSystem(input.toFile(),true)){
            if(!fs.getRoot().hasEntry("EncryptedPackage") || !fs.getRoot().hasEntry("EncryptionInfo"))
                throw new IOException("此 OLE 文件不是支持的加密 PPTX，请勿将旧 .ppt 直接改扩展名。");
            EncryptionInfo info=new EncryptionInfo(fs);
            Decryptor decryptor=Decryptor.getInstance(info);
            if(!decryptor.verifyPassword(password))throw new IOException("PPT 打开密码不是预设的 3609，无法自动解密。");
            try(InputStream in=decryptor.getDataStream(fs);OutputStream out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){
                copy(in,out,PptxConverter.MAX_INPUT);
            }
            success=true;
        } finally {if(!success)Files.deleteIfExists(output);}
    }
    @Override public void encrypt(Path input,Path output,String password) throws Exception {
        if(Files.exists(output))throw new IOException("输出文件已存在，拒绝覆盖。");
        Path temp=Files.createTempFile(output.toAbsolutePath().getParent(),".office-encrypt-",".tmp");
        try {
            try(POIFSFileSystem fs=new POIFSFileSystem()){
                EncryptionInfo info=new EncryptionInfo(EncryptionMode.agile,CipherAlgorithm.aes128,HashAlgorithm.sha512,-1,-1,ChainingMode.cbc);
                Encryptor encryptor=info.getEncryptor();encryptor.confirmPassword(password);
                try(InputStream in=Files.newInputStream(input);OutputStream out=encryptor.getDataStream(fs)){
                    copy(in,out,512L*1024*1024);
                }
                try(OutputStream out=Files.newOutputStream(temp)){fs.writeFilesystem(out);}
            }
            Files.move(temp,output);
        } finally {Files.deleteIfExists(temp);}
    }
    private static void copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buffer=new byte[65536];long total=0;int n;
        while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("加密/解密后的文件超出安全大小上限。");out.write(buffer,0,n);}
    }
}
