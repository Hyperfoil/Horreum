---
title: Re-transform a Run
description: 
date: 2023-10-15
weight: 8
---

Re-transforming Dataset(s) in a Run is useful after any of the following tasks are completed in Horreum

* Changed the selection of Transformers in a Test
* Changed a Transformer's definition
* Changed Schema

While re-transforming isn't necessary for existing Dataset(s) to continue operating normally an update with the latest derived values is useful to resolve any issues with incomplete derived values. These are the steps for a Run: 

1. Navigate to the Test Run List
2. Select the individual Run
3. Click the blue coloured button with the text "Re-transform datasets"

{{% imgproc retransform_dataset Fit "1200x500" %}}
Runs List
{{% /imgproc %}}


Alternatively, on the Test edit page

1. Select the Transformers tab
2. Click the Re-transform datasets button
3. Accept the prompt to re-transform datasets by clicking the button

{{% imgproc test_transformers Fit "1200x500" %}}
Runs List
{{% /imgproc %}}

{{% imgproc retransform_datasets_prompt Fit "1200x500" %}}
Retrabsform confirmation prompt
{{% /imgproc %}}