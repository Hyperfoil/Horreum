---
title: Relative Difference of Means
description: Reference guide for Relative Difference of Means Change Detection
date: 2023-10-15
weight: 1
---
## Overview

The Relative Difference of Means Change Detection algorithm is an algorithm that compares the mean of the last M results to the mean of the previous N results. 

If the difference between a summary calculation of two is greater than a threshold, then a change is alerted.

## Configuration

The algorithm is configured with the following parameters:

{{% imgproc configuration Fit "1200x300" %}}
Configuration
{{% /imgproc %}}

* **Threshold** - The threshold of percentage difference to use to determine if a change has occurred.
* **Minimum Window** - The number of results to use in the mean calculation for current time window.
* **Minimum Number of Preeceding datapoints** - The number of preceding windows to use in the mean calculation for preceeding time window.
* **Aggregation Function** - The aggregation function to use when calculating the mean. The options are:
  * **Mean** - The average of the results in the window.
  * **Min** - The minimum of the results in the window.
  * **Max** - The maximum of the results in the window.
   
### Example

{{% imgproc timeseries Fit "1200x300" %}}
Window Configuration
{{% /imgproc %}}

> NOTE: If `Minimum Number of Preeceding datapoints` < `Minimum Window` then for the calculation `Minimum Number of Preeceding datapoints` is set equal `Minimum Window`

Once a change has been detected, the algorithm will wait until there is sufficient data to fill both windows so that neither contain a change before alerting again. This is to prevent alerting on every result.

### Insufficient Data

If there are and insufficient number of results to calculate the mean, then the change detection algorithm will not alert. The following image shows an example of insufficient data.

In this case the change detection will wait until there are sufficient results to calculate the mean of the 2 windows.

{{% imgproc insufficientData Fit "1200x300" %}}
Insufficient Data
{{% /imgproc %}}
