package com.qcerebrum.dialerbackend;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class FirebaseStorageService {

    private static final Logger log = Logger.getLogger(FirebaseStorageService.class.getName());

    private static final String STORAGE_BUCKET = "poc-kodichits.appspot.com";

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = getClass().getResourceAsStream("/firebase-service-account.json");
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setStorageBucket(STORAGE_BUCKET)
                    .build();
                FirebaseApp.initializeApp(options);
                log.info("FirebaseStorageService: initialized FirebaseApp with storageBucket=" + STORAGE_BUCKET);
            } else {
                log.info("FirebaseStorageService: FirebaseApp already initialized elsewhere — reusing existing app.");
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "FirebaseStorageService: FirebaseApp initialization failed: " + e.getMessage(), e);
        }
    }

    public String uploadRecording(Long callLogId, File localFile) {
        try {
            String blobPath = "call-recordings/telecmi_" + callLogId + ".mp3";

            // Explicit bucket name (rather than the no-arg StorageClient.getInstance().bucket())
            // because FirebaseConfig.init() initializes the shared FirebaseApp without a
            // storageBucket set — the no-arg overload would fail with "Bucket name not specified"
            // whenever that PostConstruct runs first.
            Bucket bucket = StorageClient.getInstance().bucket(STORAGE_BUCKET);

            Blob blob;
            try (FileInputStream fileInputStream = new FileInputStream(localFile)) {
                blob = bucket.create(blobPath, fileInputStream, "audio/mpeg");
            }
            blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));

            String publicUrl = "https://storage.googleapis.com/" + bucket.getName() + "/" + blobPath;
            log.info("FirebaseStorageService: uploaded recording for callLogId=" + callLogId + " -> " + publicUrl);
            return publicUrl;
        } catch (Exception e) {
            log.log(Level.WARNING, "FirebaseStorageService: upload failed for callLogId=" + callLogId
                + ": " + e.getMessage(), e);
            return null;
        }
    }
}
