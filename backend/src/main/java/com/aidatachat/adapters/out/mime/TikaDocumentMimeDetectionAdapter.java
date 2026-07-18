package com.aidatachat.adapters.out.mime;

import com.aidatachat.application.port.out.DocumentMimeDetectionPort;
import org.apache.tika.Tika;

public final class TikaDocumentMimeDetectionAdapter implements DocumentMimeDetectionPort {

    private final Tika tika = new Tika();

    @Override
    public String detect(byte[] content, String declaredFilename) {
        return tika.detect(content, declaredFilename);
    }
}
