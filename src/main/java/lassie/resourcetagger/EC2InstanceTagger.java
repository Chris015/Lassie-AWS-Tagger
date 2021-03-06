package lassie.resourcetagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;
import lassie.awshandlers.Ec2Handler;
import lassie.config.Account;
import lassie.model.Event;
import lassie.model.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EC2InstanceTagger implements ResourceTagger {
    private static final Logger logger = LogManager.getLogger(EC2InstanceTagger.class);
    private Ec2Handler ec2Handler;
    private List<Event> events = new ArrayList<>();

    public EC2InstanceTagger(Ec2Handler ec2Handler) {
        this.ec2Handler = ec2Handler;
    }

    @Override
    public void tagResources(Account account) {
        for (Log log : account.getLogs()) {
            ec2Handler.instantiateEC2Client(account.getAccessKeyId(), account.getSecretAccessKey(), log.getRegion());
            parseJson(log.getFilePaths());
            filterEventsWithoutTag(account.getOwnerTag());
            tag(account.getOwnerTag());
        }
    }

    private void parseJson(List<String> filePaths) {
        logger.info("Parsing json");
        String jsonPath = "$..Records[?(@.eventName == 'RunInstances' && @.responseElements != null)]";
        for (String filePath : filePaths) {
            try {
                String json = JsonPath.parse(new File(filePath)).read(jsonPath).toString();
                GsonBuilder gsonBuilder = new GsonBuilder();
                JsonDeserializer<Event> deserializer = (jsonElement, type, context) -> {
                    String id = jsonElement
                            .getAsJsonObject().get("responseElements")
                            .getAsJsonObject().get("instancesSet")
                            .getAsJsonObject().get("items")
                            .getAsJsonArray().get(0).getAsJsonObject().get("instanceId")
                            .getAsString();
                    String owner = jsonElement
                            .getAsJsonObject().get("userIdentity")
                            .getAsJsonObject().get("arn")
                            .getAsString();
                    logger.info("Event created with Id: {} Owner: {}", id, owner);
                    return new Event(id, owner);
                };
                gsonBuilder.registerTypeAdapter(Event.class, deserializer);
                Gson gson = gsonBuilder.setLenient().create();
                List<Event> runInstancesEvents = gson.fromJson(json, new TypeToken<List<Event>>() {
                }.getType());
                events.addAll(runInstancesEvents);
            } catch (IOException e) {
                logger.error("Could not parse json: ", e);
                e.printStackTrace();
            }
        }
        logger.info("Done parsing json");
    }

    private void filterEventsWithoutTag(String ownerTag) {
        logger.info("Filtering EC2 instances without: {}", ownerTag);
        List<Event> untaggedInstances = new ArrayList<>();
        List<String> untaggedInstanceIds = ec2Handler.getIdsForInstancesWithoutTag(ownerTag);
        for (Event event : events) {
            if (untaggedInstanceIds.stream().anyMatch(id -> id.equals(event.getId()))) {
                untaggedInstances.add(event);
            }
        }
        this.events = untaggedInstances;
        logger.info("Done filtering EC2 instances");

    }

    private void tag(String ownerTag) {
        logger.info("Tagging EC2 instances");
        if (events.size() == 0) {
            logger.info("No untagged EC2 instances found in log files");
        }
        for (Event event : events) {
            ec2Handler.tagResource(event.getId(), ownerTag, event.getOwner());
        }
        this.events = new ArrayList<>();
        logger.info("Done tagging EC2 instances");
    }
}
