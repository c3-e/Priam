/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package c3.ops.priam.aws;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.ICredential;
import c3.ops.priam.identity.IMembership;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Class to query amazon Ring for its members to provide - Number of valid nodes
 * in the Ring - Number of zones - Methods for adding ACLs for the nodes
 */
public class AWSMembership implements IMembership {
  private static final Logger logger = LoggerFactory.getLogger(AWSMembership.class);
  private final IConfiguration config;
  private final ICredential provider;

  @Inject
  public AWSMembership(IConfiguration config, ICredential provider) {
    this.config = config;
    this.provider = provider;
  }


  public List<String> getRunningInstancesByTags(String tagName, List<String> values) {
    AmazonEC2 client = null;

    try {

      List<String> instanceIds = Lists.newArrayList();
      List<String> states = new ArrayList<String>();

      client = getEc2Client();
      states.add("running");
      DescribeInstancesRequest req = new DescribeInstancesRequest().withFilters(new Filter("tag:" + tagName, values), new Filter("instance-state-name", states));

      for (Reservation reservation : client.describeInstances(req).getReservations()) {
        for (Instance instance : reservation.getInstances()) {
          instanceIds.add(instance.getInstanceId());
          logger.info(String.format("Querying Amazon returned following instance in the Ring: %s --> %s", config.getRac(), StringUtils.join(instanceIds, ",")));
        }
      }
      return instanceIds;
    } finally {
      if (client != null)
        client.shutdown();
    }
  }


  @Override
  public List<String> getRacMembership() {
    List<String> values = new ArrayList<String>();
    values.add(config.getRingName());
    return getRunningInstancesByTags("ring_name", values);
  }


  /**
   * Actual membership AWS source of truth...
   */
  @Override
  public int getRacMembershipSize() {
    List<String> values = new ArrayList<String>();
    values.add(config.getRingName());
    return getRunningInstancesByTags("ring_name", values).size();
  }

  @Override
  public int getRacCount() {
    return config.getRacs().size();
  }

  /**
   * Adds a iplist to the SG.
   */
  public void addACL(Collection<String> listIPs, int from, int to) {
    AmazonEC2 client = null;
    try {
      client = getEc2Client();
      List<IpPermission> ipPermissions = new ArrayList<IpPermission>();
      ipPermissions.add(new IpPermission().withFromPort(from).withIpProtocol("tcp").withIpRanges(listIPs).withToPort(to));
      client.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(config.getACLGroupName(), ipPermissions));
      logger.info("Done adding ACL to: " + StringUtils.join(listIPs, ","));
    } finally {
      if (client != null)
        client.shutdown();
    }
  }

  /**
   * removes a iplist from the SG
   */
  public void removeACL(Collection<String> listIPs, int from, int to) {
    AmazonEC2 client = null;
    try {
      client = getEc2Client();
      List<IpPermission> ipPermissions = new ArrayList<IpPermission>();
      ipPermissions.add(new IpPermission().withFromPort(from).withIpProtocol("tcp").withIpRanges(listIPs).withToPort(to));
      client.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest(config.getACLGroupName(), ipPermissions));
      logger.info("Done removing from ACL: " + StringUtils.join(listIPs, ","));
    } finally {
      if (client != null)
        client.shutdown();
    }
  }

  /**
   * List SG ACL's
   */
  public List<String> listACL(int from, int to) {
    AmazonEC2 client = null;
    try {
      client = getEc2Client();
      List<String> ipPermissions = new ArrayList<String>();
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest().withGroupNames(Arrays.asList(config.getACLGroupName()));
      DescribeSecurityGroupsResult result = client.describeSecurityGroups(req);
      for (SecurityGroup group : result.getSecurityGroups())
        for (IpPermission perm : group.getIpPermissions())
          if (perm.getFromPort() == from && perm.getToPort() == to)
            ipPermissions.addAll(perm.getIpRanges());
      return ipPermissions;
    } finally {
      if (client != null)
        client.shutdown();
    }
  }

  protected AmazonEC2 getEc2Client() {
    AmazonEC2 client = new AmazonEC2Client(provider.getAwsCredentialProvider());
    client.setEndpoint("ec2." + config.getDC() + ".amazonaws.com");
    return client;
  }
}
