package com.pptconverter;

import java.nio.file.Path;

/** Optional Office file-open encryption; implemented only in the enhanced distribution. */
public interface OfficeProtection {
    boolean isEncrypted(Path input) throws Exception;
    void decrypt(Path input, Path output, String password) throws Exception;
    void encrypt(Path input, Path output, String password) throws Exception;
}
