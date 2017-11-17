package lassie.awshandlers;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static lassie.Application.DRY_RUN;

public class EMRHandlerImpl implements EMRHandler {
    private final static Logger logger = LogManager.getLogger(EMRHandlerImpl.class);
    private final static int MAX_RETRIES = 3;
    private AmazonElasticMapReduce emr;

    public void instantiateEMRClient(String accessKeyId, String secretAccessKey, String region) {
        logger.trace("Instantiating EMR client in region: {}", region);
        AWSCredentials basicCredentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
        AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(basicCredentials);
        this.emr = AmazonElasticMapReduceClientBuilder.standard()
                .withCredentials(awsCredentials)
                .withRegion(region)
                .build();
        logger.trace("EMR client instantiated");
    }

    public void tagResource(String id, String key, String value) {
        if (DRY_RUN) {
            logger.info("Dry run: {}. Did not tag: {} with {}: {}", DRY_RUN, id, key, value);
            return;
        }
        DescribeClusterRequest request = new DescribeClusterRequest().withClusterId(id);
        DescribeClusterResult result = emr.describeCluster(request);
        List<Tag> tags = result.getCluster().getTags();
        tags.add(new Tag(key, value));
        AddTagsRequest tagsRequest = new AddTagsRequest(id, tags);
        emr.addTags(tagsRequest);
        logger.info("Tagged: {} with key: {} value: {}", id, key, value);
    }

    public List<String> getIdsForClustersWithoutTag(String tag) {
        logger.trace("Describing EMR clusters");
        List<String> untaggedClusterIds = new ArrayList<>();
        ListClustersResult listClustersResult = emr.listClusters();

        for (ClusterSummary clusterSummary : listClustersResult.getClusters()) {
            DescribeClusterRequest request = new DescribeClusterRequest().withClusterId(clusterSummary.getId());
            DescribeClusterResult result = null;

            int secondsToSleep = 5;
            int retries = 0;
            while (retries < MAX_RETRIES) {
                try {
                    result = emr.describeCluster(request);
                    if (result != null) break;
                } catch (AmazonElasticMapReduceException e) {
                    logger.info("here was an issue while listing EMR clusters. The application will sleep for {} seconds\n"
                            + "and try again\nError: {}", secondsToSleep, e);
                    try {
                        Thread.sleep(1_000 * secondsToSleep);
                        secondsToSleep += secondsToSleep;
                        retries++;
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }

            if (result == null) continue;
            if (isClusterActive(result.getCluster())) {
                if (!hasTag(result.getCluster(), tag)) {
                    untaggedClusterIds.add(result.getCluster().getId());
                }
            }
        }
        logger.info("Found {} clusters without: {} on AWS", untaggedClusterIds.size(), tag);
        untaggedClusterIds.forEach(logger::info);
        return untaggedClusterIds;
    }

    private boolean isClusterActive(Cluster cluster) {
        String clusterState = cluster.getStatus().getState();
        if (clusterState.equals(ClusterState.TERMINATED.name())) {
            return false;
        } else if (clusterState.equals(ClusterState.TERMINATED_WITH_ERRORS.name())) {
            return false;
        } else if (clusterState.equals(ClusterState.TERMINATING.name())) {
            return false;
        }
        return true;
    }

    private boolean hasTag(Cluster cluster, String tag) {
        logger.debug(tag + " found: " + cluster.getTags().stream().anyMatch(t -> t.getKey().equals(tag)));
        return cluster.getTags().stream().anyMatch(t -> t.getKey().equals(tag));
    }
}
