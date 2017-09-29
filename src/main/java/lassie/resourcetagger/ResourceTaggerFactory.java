package lassie.resourcetagger;

import lassie.awshandlers.*;

public class ResourceTaggerFactory {

    private S3Handler s3Handler = new S3Handler();
    private Ec2Handler ec2Handler = new EC2HandlerImpl();
    private EMRHandler emrHandler = new EMRHandler();
    private RedshiftHandler redshiftHandler = new RedshiftHandler();
    private ELBHandler elbHandler = new ELBHandlerImpl();
    private RDSHandler rdsHandler = new RDSHandlerImpl();

    public ResourceTagger getResourceTagger(String resourceType) throws UnsupportedResourceTypeException {
        switch (resourceType.toUpperCase()) {
            case "SECURITYGROUP":
                return new SecurityGroupTagger(ec2Handler);
            case "S3BUCKET":
                return new S3BucketTagger(s3Handler);
            case "EBSVOLUME":
                return new EBSVolumeTagger(ec2Handler);
            case "RDSDBINSTANCE":
                return new RDSDBInstanceTagger(rdsHandler);
            case "REDSHIFTCLUSTER":
                return new RedshiftClusterTagger(redshiftHandler);
            case "EC2INSTANCE":
                return new EC2InstanceTagger(ec2Handler);
            case "EMRCLUSTER":
                return new EMRClusterTagger(emrHandler);
            case "LOADBALANCER":
                return new LoadBalancerTagger(elbHandler);
        }
        throw new UnsupportedResourceTypeException(resourceType + " is not a supported resource type");
    }
}
