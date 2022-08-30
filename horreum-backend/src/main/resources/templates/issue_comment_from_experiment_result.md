## Experiment results for test {profile.test.name}, profile {profile.name}

Dataset: {#dataset_link dataset /}
Baseline: {#each baseline}{#dataset_link it /}{#if it_hasNext}, {/if}{/each}{#if baseline.size > 16}, ... (total {baseline.size} datasets){/if}

| Variable | Experiment value | Baseline value | Result |
| -------- | ---------------- | -------------- | ------ |
{#each results}
{#let var=it.getKey().variable val=it.getValue()}
| {var.name} | {val.experimentValue} | {val.baselineValue} | {#result_emoji val.overall.toString() /} {val.result} |
{/let}
{/each}