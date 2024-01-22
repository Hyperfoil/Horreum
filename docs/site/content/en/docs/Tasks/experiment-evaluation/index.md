---
title: Dataset Experiment Evaluation
description: Document explaining the use of Experiment Evaluation function in Horreum UI
date: 2024-01-18
weight: 5
---

## Using the Dataset Experiment Evaluation View

Using the Experiment evaluation window you can quickly see the values and the relative difference of a Run.
Start by initially loading the Test. Then click the `Dataset list` button.

{{% imgproc dataset_list Fit "1200x900" %}} Dataset List {{% /imgproc %}}

Then select the individual Test Run you want to compare with it's Baseline.

By navigating to the uploaded run Dataset view page you will see a button  “Evaluate experiment”. Clicking this button opens a window revealing the comparison result for the current Run and the Baseline Run..

{{% imgproc individual_evaluation Fit "1200x900" %}} Individual Evaluation {{% /imgproc %}}

Results show the values then the percentage difference.


## Datasets Comparison View

Horreum provides multiple Run comparisons in the Dataset Comparison View. We can filter based on the Filter labels defined in the Schema.

Start by initially loading the Dataset list. Then click the button “Select for comparison”.

{{% imgproc select_for_comparison Fit "1200x900" %}} Select for comparison {{% /imgproc %}}

Next the Comparison view is displayed. This is where filters are set.

{{% imgproc dataset_selection Fit "1200x900" %}} Dataset selection {{% /imgproc %}}

Select a number of Runs to be compared using the “Add to comparison” button. Then click the “Compare labels”. Displayed next is the Labels Comparison view. 

{{% imgproc multiple_dataset_comparison Fit "1200x900" %}} Multiple Dataset comparison {{% /imgproc %}}

 Displayed here are multiple Datasets. Schema Labels can be expanded to display a bar graph representing each Run.
 
 In this guide two things were shown. How to compare an individual Dataset. Followed by comparing multiple Datasets.