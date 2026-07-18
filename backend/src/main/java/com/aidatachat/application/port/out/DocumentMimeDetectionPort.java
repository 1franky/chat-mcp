package com.aidatachat.application.port.out;

public interface DocumentMimeDetectionPort {

    String detect(byte[] content, String declaredFilename);
}
