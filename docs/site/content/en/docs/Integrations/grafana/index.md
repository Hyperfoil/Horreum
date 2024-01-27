---
title: Grafana
date: 2023-10-12
description: Configure Horreum as a datasource in Grafana
categories: [Integration]
weight: 2
---

Grafana is a popular tool for visualizing time series data. Horreum can be configured as a datasource in Grafana to allow you to view the data processed by Horreum.

> **Prerequisites**:

> 1. Horreum is running, and you are logged in

> 2. You have access to a running Grafana instance. If you do not have an environment available, please follow the [official documentation](https://grafana.com/docs/grafana/latest/installation/) for your platform.


## Configure Horruem as a Datasource

### 1. Install the JSON Datasource plugin for Grafana

{{% imgproc grafana_install_json_plugin Fit "1200x300" %}}
Install JSON Grafana Plugin
{{% /imgproc %}}

### 2. Add new Datasource

2.1 Click "Add new data source" button

{{% imgproc plugin_add_new_datasource Fit "1200x300" %}}
Click "Add new data source"
{{% /imgproc %}}


2.2 Configure Horreum as a Datasource

Configure the datasource as follows:
- **Name**: Horreum
- **URL**: https://<horreum_host>/api/changes
- **Access**: Server (Default)
- **Forward OAuth Identity**: checked

Click "Save & Test" button

{{% imgproc setup-horreum-datasource Fit "1200x500" %}}
Configure Horreum JSON Datasource
{{% /imgproc %}}

If successful, you should see the following message:

{{% imgproc datasource_successful Fit "1200x300" %}}
Datasource Successful
{{% /imgproc %}}


## Querying Horreum data

Now that you have Horreum configured as a datasource, you can query the data in Horreum from Grafana.

### 1.1 Create a new Dashboard

{{% imgproc create-dashboard Fit "1200x300" %}}
Create new Dashboard
{{% /imgproc %}}

### 2.2 Add Horreum as Datasource 

{{% imgproc horreum_datasource Fit "1200x300" %}}
Select Horreum Datasource created in previous step
{{% /imgproc %}}

### 2.3 Provide a name for the new Panel

{{% imgproc panel_name Fit "1200x300" %}}
Set panel name
{{% /imgproc %}}

### 2.4 Define query

Horreum provides an API that Grafana can natively query.

In order for Grafana to query Horreum, you must provide a Metric payload that defines the query.

The Metric is a String that consists of the `Variable ID` and `Fingerprint` seperated by a semi-colon `;`.

e.g. **Metric** : `23219;{"buildType":"SNAPSHOT"}`


{{% imgproc define_metric Fit "1200x300" %}}
Define query metric
{{% /imgproc %}}

### 2.5 Save panel

{{% imgproc panel_save Fit "1200x300" %}}
Save panel
{{% /imgproc %}}

### 2.5 View Dashboard

{{% imgproc grafana_dashboard Fit "1200x300" %}}
View Dashboard
{{% /imgproc %}}
