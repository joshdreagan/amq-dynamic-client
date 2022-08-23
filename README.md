# AMQ Dynamic Client

## Broker setup

Create the broker project.

```
oc new-project broker

# Install the AMQ Broker operator in the namespace (this must be done as a cluster-admin)
```

Create a broker keystore and generate a key pair. The 'CN' will need to be equal to what the generated OpenShift route will be.

```
mkdir $PROJECT_ROOT/tmp
keytool -genkeypair -alias broker -rfc -keyalg RSA -keystore $PROJECT_ROOT/tmp/broker.ks -storepass password -dname 'CN=my-broker-amqps-0-svc-rte-broker.apps.cluster-f888r.f888r.sandbox1210.opentlc.com'
```

Create an empty truststore for the broker.

```
cp $PROJECT_ROOT/tmp/broker.ks $PROJECT_ROOT/tmp/broker.ts
keytool -delete -alias broker -storepass password -keystore $PROJECT_ROOT/tmp/broker.ts
```

Export the broker's public key from the keystore.

```
keytool -export -alias broker -rfc -keystore $PROJECT_ROOT/tmp/broker.ks -storepass password -file $PROJECT_ROOT/tmp/broker.crt
```

Create a client truststore and import the broker's public key.

```
keytool -import -noprompt -alias broker -keystore $PROJECT_ROOT/tmp/client.ts -storepass password -file $PROJECT_ROOT/tmp/broker.crt
```

Add the broker's keystore, and truststore to an OpenShift secret and link it so the AMQ Broker Operator service account can see it.

```
oc -n broker create secret generic my-broker-amqps-secret --from-file=broker.ks=$PROJECT_ROOT/tmp/broker.ks --from-file=client.ts=$PROJECT_ROOT/tmp/broker.ts --from-literal=keyStorePassword=password --from-literal=trustStorePassword=password
oc -n broker secrets link sa/amq-broker-operator secret/my-broker-amqps-secret
```

Create the broker cluster, add the security rules/users/roles, and create an address.

```
oc -n broker apply -f $PROJECT_ROOT/artemis-broker.yaml
oc -n broker apply -f $PROJECT_ROOT/artemis-address.yaml
```

## Run the client

Make sure to replace the requisite values in $PROJECT_ROOT/application.properties.

```
mvn exec:java
```