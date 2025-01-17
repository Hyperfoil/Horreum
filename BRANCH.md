This Branch is / was the work to correct design limiations in the Horreum data model.

The basic plan was to allow Extractors to target the output of other labels, thus removing the need for Datasets. 
The branch also introduced LabelGroups as a replacement for Schemas. Tests are a special case of LabelGroups and 
referencing a LabelGroup copies the associated Labels (copy on reference).
The copy on reference would prevent accidental mutation when shared schemas are changed by their owner.

The work stopped while tesing the migration code in ComposableMigration.
* Tests preserve their ID
* Runs preserve their ID
* Schema IDs change (schema's are gone) and are tracked in `exp_temp_map_schema` as well as `exp_temp_map_transforms`

We were using MigrateService and RunMigrationValidator as temporary assistants to 
validate migrated production data. 

We identified an issue where Extractors (either validator or label) that relied on `isArray`
to wrap the results in a json array would need to add `[*]` to the jsonpath. That, or we need to change how
extractor values are processed by default. I favor changing how the extractors are written instead of perpetuating the 
anomalous behavior that occurred due to how Extractors were initially implemented.

If the work is resumed the next step is to iterate through each test and calculate the label values for each run, comparing them to the old model.
Once all deltas are identified then the migration can take place.

