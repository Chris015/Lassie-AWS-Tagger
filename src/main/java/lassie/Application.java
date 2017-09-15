package lassie;

import lassie.config.Account;
import lassie.config.ConfigReader;
import lassie.resourcetagger.ResourceTagger;
import lassie.resourcetagger.ResourceTaggerFactory;
import lassie.resourcetagger.UnsupportedResourceTypeException;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Application {
    private static Logger log = Logger.getLogger(Application.class);
    private ConfigReader configReader;
    private LogHandler logHandler;
    private ResourceTaggerFactory resourceTaggerFactory;
    private String fromDate;

    public Application(String[] args) {
        this.configReader = new ConfigReader();
        this.logHandler = new LogHandler();
        this.resourceTaggerFactory = new ResourceTaggerFactory();
        this.fromDate = new DateInterpreter().interpret(args);
    }

    public void run() {
        log.info("Application started");
        List<Account> accounts = configReader.getAccounts();
        List<ResourceTagger> resourceTaggers = getResourceTaggers(accounts);
        List<Log> logs = logHandler.getLogs(fromDate, accounts);
        resourceTaggers.forEach(resourceTagger -> resourceTagger.tagResources(logs));
        logHandler.clearLogs();
        log.info("Application completed");
    }

    private List<ResourceTagger> getResourceTaggers(List<Account> accounts) {
        log.info("Creating resource taggers");
        List<ResourceTagger> resourceTaggers = new ArrayList<>();
        List<String> resourceTypes = new ArrayList<>();

        for (Account account : accounts) {
            resourceTypes.addAll(account.getResourceTypes());
        }
        try {
            for (String resourceType : resourceTypes) {
                resourceTaggers.add(resourceTaggerFactory.getResourceTagger(resourceType));
            }
        } catch (UnsupportedResourceTypeException e) {
            log.warn("Unsupported resource request.", e);
            e.printStackTrace();
        }
        log.info("Resource taggers created");
        return resourceTaggers;
    }
}
