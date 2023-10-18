---
title: Grafana
date: 2023-10-12
description: Configure Horreum as a datasource in Grafana
categories: [Integration]
weight: 2
---

If you're familiar with Grafana you might want to use it to view the data - the button 'Open Grafana' will lead you there. Note that the dashboard is created automatically and it cannot be altered by users. The OAuth access into both Horreum and Grafana is shared and Horreum automatically creates Grafana users. In the default configuration anonymous users don't have access to Grafana, though they can see charts directly in Horreum (if the test and runs are public).

When a Change is created it is represented as a red circle in Series, or as an annotation (vertical line) in Grafana. Regrettably Grafana does not support annotation per chart, therefore the change in datapoint series for one regression variable is displayed in all charts even though the variable is unrelated.

{{% imgproc grafana Fit "1200x300" %}}
Grafana Integration
{{% /imgproc %}}