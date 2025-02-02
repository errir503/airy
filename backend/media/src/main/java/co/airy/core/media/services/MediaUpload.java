package co.airy.core.media.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

@Service
@EnableRetry
public class MediaUpload {
    private final AmazonS3 amazonS3Client;
    private final String bucket;
    private final URL host;

    public MediaUpload(AmazonS3 amazonS3Client,
                       @Value("${storage.s3.bucket}") String bucket,
                       @Value("${storage.s3.path}") String path) throws MalformedURLException {
        this.amazonS3Client = amazonS3Client;
        this.bucket = bucket;
        URL bucketHost = new URL(String.format("https://%s.s3.amazonaws.com/", bucket));
        this.host = new URL(bucketHost, path);
    }

    public boolean isUserStorageUrl(URL dataUrl) {
        return dataUrl.getHost().equals(host.getHost());
    }

    @Retryable
    public String uploadMedia(final InputStream is, final String fileName) throws Exception {
        final String contentType = resolveContentType(fileName, is);

        final ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);

        final PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucket,
                fileName,
                is,
                objectMetadata
        ).withCannedAcl(CannedAccessControlList.PublicRead);
        amazonS3Client.putObject(putObjectRequest);

        return new URL(host, fileName).toString();
    }

    private String resolveContentType(final String fileName, final InputStream is) throws IOException {
        final String contentType = URLConnection.guessContentTypeFromStream(is);
        return contentType == null ? URLConnection.guessContentTypeFromName(fileName) : contentType;
    }
}
