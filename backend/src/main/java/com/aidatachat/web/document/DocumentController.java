package com.aidatachat.web.document;

import com.aidatachat.adapters.out.security.AuthenticatedUser;
import com.aidatachat.application.exception.DocumentStorageException;
import com.aidatachat.application.port.in.DocumentManagementUseCase;
import com.aidatachat.application.port.in.DocumentManagementUseCase.DocumentListView;
import com.aidatachat.application.port.in.DocumentManagementUseCase.DocumentView;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentCommand;
import com.aidatachat.application.port.in.DocumentManagementUseCase.UploadDocumentResult;
import com.aidatachat.domain.model.DocumentStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentManagementUseCase documents;

    public DocumentController(DocumentManagementUseCase documents) {
        this.documents = documents;
    }

    @GetMapping
    DocumentListView list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documents.listDocuments(actor.id(), status, page, size);
    }

    @GetMapping("/{documentId}")
    DocumentView get(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID documentId) {
        return documents.getDocument(actor.id(), documentId);
    }

    @PostMapping
    ResponseEntity<DocumentView> upload(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        UploadDocumentResult result =
                documents.uploadDocument(
                        actor.id(),
                        new UploadDocumentCommand(
                                file.getOriginalFilename(),
                                file.getSize(),
                                inputStream(file),
                                request.getRemoteAddr()));
        DocumentView view = result.document();
        if (!result.created()) {
            return ResponseEntity.ok(view);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/documents/" + view.id()))
                .body(view);
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID documentId,
            HttpServletRequest request) {
        documents.deleteDocument(actor.id(), documentId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private InputStream inputStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to read uploaded file", e);
        }
    }
}
