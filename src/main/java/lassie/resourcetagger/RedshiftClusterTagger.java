package lassie.resourcetagger;

import com.amazonaws.services.redshift.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import lassie.awsHandlers.RedshiftHandler;
import lassie.model.Log;
import lassie.config.Account;
import lassie.model.Event;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RedshiftClusterTagger implements ResourceTagger {
    private final Logger log = Logger.getLogger(RedshiftClusterTagger.class);
    private RedshiftHandler redshiftHandler;
    private List<Event> events = new ArrayList<>();

    public RedshiftClusterTagger(RedshiftHandler redshiftHandler) {
        this.redshiftHandler = redshiftHandler;
    }

    @Override
    public void tagResources(List<Log> logs) {
        for (Log log : logs) {
            instantiateRedshiftClient(log.getAccount());
            parseJson(log.getAccount(), log.getFilePaths());
            filterTaggedResources(log.getAccount().getOwnerTag());
            tag(log.getAccount().getOwnerTag());
        }
    }

    private void instantiateRedshiftClient(Account account) {
        redshiftHandler.instantiateRedshiftClient(account.getAccessKeyId(), account.getSecretAccessKey(), account.getRegions().get(0));
    }

    private void parseJson(Account account, List<String> filePaths) {
        log.info("Parsing json");
        String jsonPath = "$..Records[?(@.eventName == 'CreateCluster' && @.responseElements != null)]";
        for (String filePath : filePaths) {
            try {
                String json = JsonPath.parse(new File(filePath)).read(jsonPath).toString();
                GsonBuilder gsonBuilder = new GsonBuilder();
                JsonDeserializer<Event> deserializer = (jsonElement, type, context) -> {
                    String clusterId = jsonElement
                            .getAsJsonObject().get("requestParameters")
                            .getAsJsonObject().get("clusterIdentifier")
                            .getAsString();
                    String arn = "arn:aws:redshift:"
                            + account.getRegions().get(0) + ":"
                            + account.getAccountId() + ":cluster:"
                            + clusterId;
                    String owner = jsonElement
                            .getAsJsonObject().get("userIdentity")
                            .getAsJsonObject().get("arn")
                            .getAsString();
                    log.info("RedShift cluster model created. ARN: " + arn + " Owner: " + owner);
                    return new Event(arn, owner);
                };
                gsonBuilder.registerTypeAdapter(Event.class, deserializer);
                Gson gson = gsonBuilder.setLenient().create();
                List<Event> createClusterEvents = gson.fromJson(
                        json, new TypeToken<List<Event>>() {
                        }.getType());
                events.addAll(createClusterEvents);
            } catch (IOException e) {
                log.error("Could not parse json: ", e);
                e.printStackTrace();
            }
        }
        log.info("Done parsing json");
    }

    private void filterTaggedResources(String ownerTag) {
        log.info("Filtering tagged RedShift clusters");
        List<Event> untaggedEvents = new ArrayList<>();
        List<Cluster> clustersWithoutTag = redshiftHandler.describeCluster(ownerTag);
        for (Cluster cluster : clustersWithoutTag) {
            for (Event event : events) {
                String clusterId = cluster.getClusterIdentifier();
                String eventId = event.getId();
                eventId = eventId.substring(eventId.lastIndexOf(':') + 1, eventId.length());
                if (clusterId.equals(eventId)) {
                    untaggedEvents.add(event);
                }
            }
        }
        log.info("Done filtering tagged RedShift clusters");
        this.events = untaggedEvents;
    }

    private void tag(String ownerTag) {
        log.info("Tagging RedShift clusters");
        for (Event event : events) {
            redshiftHandler.tagResource(event.getId(), ownerTag, event.getOwner());
            log.info("Tagged: " + event.getId() + " with key: " + ownerTag + " value: " + event.getOwner());
        }
        this.events = new ArrayList<>();
        log.info("Done tagging RedShift clusters");
    }
}
