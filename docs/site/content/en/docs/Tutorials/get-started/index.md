---
title: Get Started
date: 2023-10-12
description: >
  Start Horreum on your local machine in 3 easy steps
categories: [Tutorial]
weight: 1
---

## Running Horreum locally

In this tutorial we'll show you how to start Horreum and its infrastructure using container technologies such as `podman` or `docker`.

### 1: install required software

Make sure that you have either `podman` and `podman-compose` or `docker` and `docker-compose` installed. Some scripting we have prepared to simplify the startup also requires `curl` and `jq`. On Fedora you would run

```
sudo dnf install -y curl jq podman podman-plugins podman-compose
```

This setup is going to use ports `8080` (Horreum application), `8180` (Keycloak), and `5432` (PostgreSQL) on `localhost`. Please make sure that these are not used by any other application.

### 2: start Horreum

We have prepared a simple script that downloads all the necessary files and starts all containers in host-network mode:

```bash
curl -s https://raw.githubusercontent.com/Hyperfoil/Horreum/0.6/infra/start.sh | bash
```

After a few moments everything should be up and ready, and a browser pointed to [http://localhost:8080](http://localhost:8080) will open.

### 3: log in

In the upper right corner you should see the **Log in** button. Press that and fill in username `user` and password `secret`. When you sign in the upper right corner you should see that you're authenticated as 'Dummy User'.


{{% imgproc logged_in Fit "1200x300" %}}
User logged in
{{% /imgproc %}}


You can explore the application but at this moment it is empty. Let's continue with [creating a test and uploading a run](/docs/tutorials/create-test-run).

### stop Horreum

You can stop and remove all the containers using the command below:

```bash
podman kill $(podman ps -q --filter 'label=io.podman.compose.project=horreum')
podman rm $(podman ps -q -a --filter 'label=io.podman.compose.project=horreum')
```

