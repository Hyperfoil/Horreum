---
title: Fixed Threshold
description: Reference guide for Fixed Threshold Change Detection
date: 2023-10-15
weight: 2
---
## Overview

The Fixed Threshold Change Detection algorithm is a simple algorithm that compares the value of the last datapoint against a predefined threshold.

If the value is greater or less than the threshold, then a change is alerted.

## Configuration

The algorithm is configured with the following parameters:

{{% imgproc configuration Fit "1200x300" %}}
Configuration
{{% /imgproc %}}

* **Minimum** - The lower bound threshold to determine if a change has occurred.
  * **Value** - Lower bound for acceptable datapoint values.
  * **Disabled** - Whether the lower bound is enabled
  * **Inclusive** - Is the lower bound value included in the evaluation, or are value less than the lower bound considered a change.
* **Maximum** - The upper bound threshold to determine if a change has occurred.
  * **Value** - Upper bound for acceptable datapoint values.
  * **Disabled** - Whether the Upper bound is enabled
  * **Inclusive** - Is the upper bound value included in the evaluation, or are value more than the upper bound considered a change.
   
### Example


{{% imgproc boundsChart Fit "1200x300" %}}
Bounds Chart
{{% /imgproc %}}

The algorithm will evaluate every datapoint against the defined bounds and raise an alert if the datapoint is outside the bounds.

## Summary Bounds

Due to the data manipulation and derived metrics capability in Horreum, the bounds can be used to validate ratios, percentages, number standard deviations etc from summary statistics derived from the raw data.

>Note: Bound checks do not need to be only used for simple static values.