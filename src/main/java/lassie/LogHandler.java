package lassie;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import lassie.config.Account;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class LogHandler {

    private AmazonS3 s3;
    private Path tmpFolderZipped;
    private Path tmpFolderUnzipped;

    public LogHandler() {
        createTmpFolders();
    }

    public List<Log> getLogs(String fromDate, List<Account> accounts) {
        List<Log> logs = new ArrayList<>();
        // For each account
        for (Account account : accounts) {
            BasicAWSCredentials awsCredentials = new BasicAWSCredentials(account.getAccessKeyId(),
                    account.getSecretAccessKey());

            this.s3 = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .withRegion(Regions.fromName(account.getBucketRegion()))
                    .build();
            // For each region, gets3ObjectSummaries
            for (String region : account.getRegions()) {
                List<S3ObjectSummary> summaries = getObjectSummaries(fromDate, account, region);


                // Download and unzip CloudTrail logs
                List<String> filePaths = downloadLogs(account, summaries);
                // Create new logs and add them to list
                logs.addAll(createLogs(account,region , filePaths));
            }
        }
        // Return a list of Logs containing the account with Region
        return logs;
    }

    private List<S3ObjectSummary> getObjectSummaries(String fromDate, Account account, String region) {
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(account.getS3Url().getBucket())
                .withPrefix(account.getS3Url().getKey() +
                        "/AWSLogs/" +
                        account.getAccountId() + "/" +
                        "CloudTrail/" +
                        region + "/" +
                        fromDate + "/"
                );
        return s3.listObjectsV2(request).getObjectSummaries();
    }

    private List<String> downloadLogs(Account account, List<S3ObjectSummary> summaries) {
        List<String> fileNames = downloadZip(account, summaries);
        return unzipObject(fileNames);
    }

    private List<String> downloadZip(Account account, List<S3ObjectSummary> summaries) {
        List<String> fileNames = new ArrayList<>();
        for (S3ObjectSummary objectSummary : summaries) {
            String key = objectSummary.getKey();
            try (S3Object s3Object = s3.getObject(new GetObjectRequest(account.getS3Url().getBucket(), key));
                 S3ObjectInputStream objectContent = s3Object.getObjectContent()) {

                String filename = s3Object.getKey().substring(key.lastIndexOf('/') + 1, key.length());
                Files.copy(objectContent, Paths.get(tmpFolderZipped + "/" + filename), StandardCopyOption.REPLACE_EXISTING);
                fileNames.add(filename);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fileNames;
    }

    private List<String> unzipObject(List<String> filenames) {
        List<String> filePaths = new ArrayList<>();
        for (String filename : filenames) {
            String fileInputPath = tmpFolderZipped + "/" + filename;
            String fileOutputPath = tmpFolderUnzipped + "/" + filename.substring(0, filename.length() - 3);
            try (FileInputStream fileInputStream = new FileInputStream(fileInputPath);
                 GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                 FileOutputStream fileOutputStream = new FileOutputStream(fileOutputPath)) {

                byte[] buffer = new byte[1024];
                int len;

                while ((len = gzipInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, len);
                }
                filePaths.add(fileOutputPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filePaths;
    }

    private List<Log> createLogs(Account account, String region, List<String> filePaths) {
        List<Log> logs = new ArrayList<>();

        for (String filePath : filePaths) {
            List<String> regions = new ArrayList<>();
            regions.add(region);
            logs.add(new Log(
                    new Account(account.getOwnerTag(),
                            account.getAccessKeyId(),
                            account.getSecretAccessKey(),
                            account.getAccountId(),
                            account.getS3Url(),
                            account.getBucketRegion(),
                            account.getResourceTypes(),
                            regions),
                    filePath));
        }
        return logs;
    }

    private void createTmpFolders() {
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
}
