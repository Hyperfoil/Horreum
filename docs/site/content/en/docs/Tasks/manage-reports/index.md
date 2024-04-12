---
title: Manage Reports
date: 2024-02-21
description: How to manage Reports and Report configurations in Horreum
categories: [Tutorial]
weight: 3
---

## Background

Creation of `Report Configurations` in `Horreum` is straightforward enough but deletion can be not obvious. A `Report Configuration` can be updated or saved as a individual `Report`. A useful procedure when modifying an existing `Report` that functions correctly.

## Report Deletion

{{% imgproc total-reports-on-report-configuration-list-page Fit "460x528" %}}
Select Report configurations
{{% /imgproc %}}

To delete an existing `Report` select the folder icon in the `Total reports` column on the `Report Configuations` list view. Each instance of a `Report` will have a red coloured button named Delete.

{{% imgproc report-configuration-list-view Fit "752x681" %}}
Available Report configurations
{{% /imgproc %}}

The same task can be repeated using the web API to delete a `Report`. Copy and paste this into your shell. Note, modify the REPORT_ID parameter. The response to expect is a 204 HTTP response code for a successful deletion.

```bash
TOKEN=$(curl -s http://localhost:8180/realms/horreum/protocol/openid-connect/token \
    -d 'username=user' -d 'password=secret' \
    -d 'grant_type=password' -d 'client_id=horreum-ui' \
    | jq -r .access_token)
REPORT_ID=<your_report_id_here>
curl   'http://localhost:8080/api/report/table/'$REPORT_ID   -H 'content-type: application/json' -H 'Authorization: Bearer '$TOKEN --request DELETE -v
```
