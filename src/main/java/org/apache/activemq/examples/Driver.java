/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.examples;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import java.io.Console;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Pattern;

public class Driver {
  
  public static void main(String[] args) throws Throwable {
    Properties props = System.getProperties();
    Path propsFilePath = Paths.get((args.length > 0) ? args[0] : "./application.properties");
    try (InputStream propsInputStream = Files.newInputStream(propsFilePath, StandardOpenOption.READ)) {
      props.load(propsInputStream);
      System.setProperties(props);
    }
    
    String openShiftUrl = props.getProperty("openshift.url", props.getProperty("kubernetes.master"));
    String kubernetesMasterUrl = props.getProperty("kubernetes.master");
    boolean kubernetesTrustCerts = Boolean.valueOf(props.getProperty("kubernetes.trust.certificates"));
    String kubernetesUsername = props.getProperty("kubernetes.auth.basic.username");
    String kubernetesPassword = props.getProperty("kubernetes.auth.basic.password");
    
    String queue = props.getProperty("artemis.queue", "foo");
    String protocol = props.getProperty("artemis.protocol", "amqps");
    int port = Integer.valueOf(props.getProperty("artemis.port", "443"));
    String username = props.getProperty("artemis.username", "admin");
    String password = props.getProperty("artemis.password", "admin");
    Path trustStore = Paths.get(props.getProperty("artemis.trustStore", "./client.ts"));
    String trustStorePassword = props.getProperty("artemis.trustStorePassword", "password");
    String clientId = props.getProperty("artemis.clientId", "console-producer");
    String namespace = props.getProperty("artemis.namespace", "broker");
    String clusterName = props.getProperty("artemis.clusterName", "my-broker");
    String acceptorName = props.getProperty("artemis.acceptorName", "amqps");
    
    Producer producer = new Producer(queue);
    
    DampingRoutesUpdater routesUpdater = new DampingRoutesUpdater();
    routesUpdater.setProducer(producer);
    routesUpdater.setRouteFilter(Pattern.compile(String.format("\\Q%s-%s-\\E\\d+\\Q-svc-rte\\E", clusterName, acceptorName)));
    routesUpdater.setClientId(clientId);
    routesUpdater.setProtocol(protocol);
    routesUpdater.setPort(port);
    routesUpdater.setUsername(username);
    routesUpdater.setPassword(password);
    routesUpdater.setTrustStore(trustStore);
    routesUpdater.setTrustStorePassword(trustStorePassword);
    
    OpenShiftConfig config = new OpenShiftConfigBuilder()
            .withOpenShiftUrl(openShiftUrl)
            .withMasterUrl(kubernetesMasterUrl)
            .withTrustCerts(kubernetesTrustCerts)
            .withUsername(kubernetesUsername)
            .withPassword(kubernetesPassword)
            .build();
    OpenShiftClient client = new KubernetesClientBuilder().withConfig(config).build().adapt(OpenShiftClient.class);
    SharedIndexInformer<Route> watcher = client
            .routes()
            .inNamespace(namespace)
            .withLabel(String.format("ActiveMQArtemis=%s", clusterName))
            .inform(routesUpdater, 30000L);
    watcher.start();
    
    Console console = System.console();
    String line = null;
    do {
      line = console.readLine("> ");
      try {
        producer.send(line);
      } catch (Exception e) {
        console.printf("Problem sending message.\n");
      }
    } while (line != null && !line.isBlank());
    
    watcher.stop();
    watcher.close();
    client.close();
    routesUpdater.close();
    producer.stop();
    System.exit(0);
  }
}
