import Api, { Schema } from "../../api"

import ImportButton from "../../components/ImportButton"

type SchemaImportButtonProps = {
    schemas: Schema[]
    onImported(): void
}

export default function SchemaImportButton(props: SchemaImportButtonProps) {
    return (
        <ImportButton
            label="Import schema"
            onLoad={config => {
                const overridden = props.schemas.find(s => s.id === config.id)
                return overridden ? (
                    <>
                        This configuration is going to override schema {overridden.name} ({overridden.id})
                        {config?.name !== overridden.name && ` using new name ${config?.name}`}.<br />
                        <br />
                        Do you really want to proceed?
                    </>
                ) : null
            }}
            onImport={config => Api.schemaServiceImportSchema(config)}
            {...props}
        />
    )
}
