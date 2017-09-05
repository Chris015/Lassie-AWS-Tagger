import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import config.S3Url;
import event.Event;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class LogPersister {

    private Path tmpFolderZipped;
    private Path tmpFolderUnzipped;
    private S3Object s3Object;
    private S3ObjectInputStream objectContent;
    private AmazonS3 s3;
    private S3Url s3Url;
    private DateFormatter dateFormatter;

    public LogPersister(AmazonS3 s3, S3Url s3Url, DateFormatter dateFormatter) {
        this.s3 = s3;
        this.s3Url = s3Url;
        this.dateFormatter = dateFormatter;
    }

    //"cloudtrail/AWSLogs/343211807682/CloudTrail/"

    public List<S3ObjectSummary> listObjectsWithDate(List<Event> events) {
        for (Event event : events) {
            ListObjectsV2Request req = new ListObjectsV2Request()
                    .withBucketName(s3Url.getBucket())
                    .withPrefix(s3Url.getKey()
                            + "/AWSLogs/"
                            + event.getOwnerId() + "/"
                            + "CloudTrail/"
                            + s3.getRegionName() + "/"
                            + dateFormatter.format(event.getLaunchTime()) + "/");
            ListObjectsV2Result listing = s3.listObjectsV2(req);

            return listing.getObjectSummaries();
        }

        return null;
    }

    public void downloadObject(List<S3ObjectSummary> objectSummaries) {
        createTmpFolders();
        for (S3ObjectSummary objectSummary : objectSummaries) {
            String key = objectSummary.getKey();
            try {
                s3Object = s3.getObject(new GetObjectRequest(s3Url.getBucket(), key));
                objectContent = s3Object.getObjectContent();

                String filename = s3Object.getKey().substring(key.lastIndexOf('/') + 1, key.length());


                Files.copy(objectContent, Paths.get(tmpFolderZipped + "/" + filename), StandardCopyOption.REPLACE_EXISTING);
                unzipObject(filename);
                objectContent.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void createTmpFolders() {
        try {
            if (!Files.isDirectory(Paths.get("tmp"))) {
                Files.createDirectory(Paths.get("tmp"));
            }

            tmpFolderZipped = Files.createTempDirectory(Paths.get("tmp/"), null);
            tmpFolderUnzipped = Files.createTempDirectory(Paths.get("tmp/"), null);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void unzipObject(String filename) {
        try (FileInputStream fileInputStream = new FileInputStream(tmpFolderZipped + "/" + filename);
             GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
             FileOutputStream fileOutputStream = new FileOutputStream(
                     tmpFolderUnzipped + "/" + filename.substring(0, filename.length() - 3))) {

            byte[] buffer = new byte[1024];
            int len;

            while ((len = gzipInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, len);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteFolders() {
        try {
            if (Files.isDirectory(Paths.get("tmp"))) {
                FileUtils.cleanDirectory(new File("tmp"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
