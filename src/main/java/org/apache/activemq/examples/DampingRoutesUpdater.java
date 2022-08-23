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

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.openshift.api.model.Route;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DampingRoutesUpdater implements ResourceEventHandler<Route> {

  private static final Logger log = LoggerFactory.getLogger(DampingRoutesUpdater.class);
  
  private Producer producer;
  private Pattern routeFilter;
  private String protocol;
  private int port;
  private String username;
  private String password;
  private String clientId;
  private Path trustStore;
  private String trustStorePassword;

  private final Set<String> brokerUrls = new HashSet<>();
  private final ScheduledExecutorService damper = Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> damperHandle;

  public Producer getProducer() {
    return producer;
  }

  public void setProducer(Producer producer) {
    this.producer = producer;
  }

  public Pattern getRouteFilter() {
    return routeFilter;
  }

  public void setRouteFilter(Pattern routeFilter) {
    this.routeFilter = routeFilter;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public Path getTrustStore() {
    return trustStore;
  }

  public void setTrustStore(Path trustStore) {
    this.trustStore = trustStore;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public void setTrustStorePassword(String trustStorePassword) {
    this.trustStorePassword = trustStorePassword;
  }

  @Override
  public void onAdd(Route obj) {
    onUpdate(null, obj);
  }

  @Override
  public void onUpdate(Route oldObj, Route newObj) {
    String routeName = newObj.getMetadata().getName();
    if (routeFilter.matcher(routeName).matches()) {
      String brokerUrl = routeToBrokerUrl(newObj);
      if (brokerUrls.add(brokerUrl)) {
        log.debug("Adding/updating brokerUrl: [{}].", brokerUrl);
        markChanged();
      }
    }
  }

  @Override
  public void onDelete(Route obj, boolean deletedFinalStateUnknown) {
    String routeName = obj.getMetadata().getName();
    if (routeFilter.matcher(routeName).matches()) {
      String brokerUrl = routeToBrokerUrl(obj);
      if (brokerUrls.remove(brokerUrl)) {
        log.debug("Removing brokerUrl: [{}].", brokerUrl);
        markChanged();
      }
    }
  }

  protected String routeToBrokerUrl(Route route) {
    StringBuilder brokerUrl = new StringBuilder();
    brokerUrl.append(protocol).append("://");
    brokerUrl.append(route.getSpec().getHost());
    brokerUrl.append(":").append(port);
    brokerUrl.append("?jms.clientID=").append(clientId);
    brokerUrl.append("&jms.username=").append(username);
    brokerUrl.append("&jms.password=").append(password);
    brokerUrl.append("&transport.trustStoreLocation=").append(trustStore);
    brokerUrl.append("&transport.trustStorePassword=").append(trustStorePassword);
    return brokerUrl.toString();
  }

  private void markChanged() {
    if (damperHandle != null) {
      damperHandle.cancel(false);
    }
    damperHandle = damper.schedule(() -> {
      log.debug("Refreshing producer.");
      producer.stop();
      producer.start(brokerUrls);
    }, 1, TimeUnit.SECONDS);
  }
  
  public void close() {
    if (damperHandle != null) {
      damperHandle.cancel(true);
    }
    damper.shutdownNow();
  }
}
