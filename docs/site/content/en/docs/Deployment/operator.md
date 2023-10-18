---
title: OpenShift/Kubernetes
description: Install Horreum on a OpenShift/Kubernetes cluster
date: 2023-10-15
weight: 2
---

Deploying Horreum for production in OpenShift/Kubernetes is easy using [Horreum operator](https://github.com/Hyperfoil/horreum-operator); you can install it through the Operator Marketplace/Operator Hub. This operator installs [Horreum](https://github.com/Hyperfoil/Horreum) and the services it depends on - PostgreSQL database and Keycloak SSO.

## Installation steps

1. From OperatorHub install `horreum` in your namespace.
2. Create a pvc in the namespace where horreum operator was installed (`oc apply -f <your_pvc_filename>.yaml -n <your_horreum_namespace>`). The pvc is required by the postgres database and must be in place before the database pod is created by the operator. PVC should have the same name as specified in the CR definition below. See example pvc definition:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: horreum-postgres
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  volumeMode: Filesystem
```

3. Create horreum CR, so that all pods wil lbe created by the horreum operator (`oc apply -f <your_cr_filename>.yaml -n <your_horreum_namespace>`).
   See an [example](https://github.com/Hyperfoil/horreum-operator/tree/master/deploy/crds/hyperfoil.io_v1alpha1_horreum_cr.yaml) of the `horreum` resource:

```yaml
apiVersion: hyperfoil.io/v1alpha1
kind: Horreum
metadata:
  name: example-horreum
spec:
  route:
    host: horreum.apps.mycloud.example.com
  keycloak:
    route:
      host: keycloak.apps.mycloud.example.com
  grafana:
    route:
      host: grafana.apps.mycloud.example.com
  postgres:
    persistentVolumeClaim: horreum-postgres
  report:
    route:
      host: hyperfoil-report.apps.mycloud.example.com
    persistentVolumeClaim: hyperfoil-report
```

For detailed description of all properties [refer to the CRD](https://github.com/Hyperfoil/horreum-operator/tree/master/deploy/olm-catalog/horreum-operator/0.1.0/hyperfoil.io_horreums_crd.yaml).

When using persistent volumes make sure that the access rights are set correctly and the pods have write access; in particular the PostgreSQL database requires that the mapped directory is owned by user with id `999`.

If you're planning to use secured routes (edge termination) it is recommended to set the `tls: my-tls-secret` at the first deploy; otherwise it is necessary to update URLs for clients `horreum` and `horreum-ui` in Keycloak manually. Also the Horreum pod needs to be restarted after Keycloak route update.

Currently you must set both Horreum and Keycloak route host explicitly, otherwise you could not log in (TODO).

When the `horreum` resource gets ready, login into Keycloak using administrator credentials (these are automatically created if you don't specify existing secret) and create a new user in the `horreum` realm, a new team role (with `-team` suffix) and assign it to the user along with [other appropriate predefined roles](/docs/about/users.html). Administrator credentials can be found using this:

```sh
NAME=$(oc get horreum -o jsonpath='{$.items[0].metadata.name}')
oc get secret $NAME-keycloak-admin -o json | \
    jq '{ user: .data.username | @base64d, password: .data.password | @base64d }'
```

For details of roles in Horreum please refer to [user management](/docs/about/users.html).

## Hyperfoil integration

For your convenience this operator creates also a config map (`*-hyperfoil-upload`) that can be used in [Hyperfoil resource](https://github.com/Hyperfoil/hyperfoil-operator) to upload Hyperfoil results to this instance - you can use it directly or merge that into another config map you use for post-hooks. However, it is necessary to define & mount a secret with these keys:

```sh
# Credentials of the user you've created in Keycloak
HORREUM_USER=user
HORREUM_PASSWORD=password
# Role for the team the user belongs to (something you've created)
HORREUM_GROUP=engineers-team

oc create secret generic hyperfoil-horreum \
    --from-literal=HORREUM_USER=$HORREUM_USER \
    --from-literal=HORREUM_PASSWORD=$HORREUM_PASSWORD \
    --from-literal=HORREUM_GROUP=$HORREUM_GROUP \
```

Then set it up in the `hyperfoil` resource:

```yaml
apiVersion: hyperfoil.io/v1alpha1
kind: Hyperfoil
metadata:
  name: example-hyperfoil
  namespace: hyperfoil
spec:
  # ...
  postHooks: example-horreum-hyperfoil-upload
  secretEnvVars:
    - hyperfoil-horreum
```

This operator automatically inserts a webhook to convert test results into Hyperfoil report; In order to link from test to report you have to add a schema (matching the URI used in your Hyperfoil version, usually something like `http://hyperfoil.io/run-schema/0.8` and add it an extractor `info` with JSON path `.info`. Subsequently go to the test and add a view component with header 'Report', accessor you've created in the previous step and this rendering script (replacing the hostname):

```js
(value, all) => {
  let info = JSON.parse(value);
  return (
    '<a href="http://example-horreum-report-hyperfoil.apps.mycloud.example.com/' +
    all.id +
    "-" +
    info.id +
    "-" +
    info.benchmark +
    '.html" target=_blank>Show</a>'
  );
};
```
