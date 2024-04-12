import ImportButton from "../../components/ImportButton"

import {Test, testApi, TestExport} from "../../api"

type TestImportButtonProps = {
    tests: Test[]
    onImported(): void
}

export default function TestImportButton(props: TestImportButtonProps) {
    return (
        <ImportButton
            label="Import test"
            onLoad={config => {
                const overridden = props.tests.find(t => t.id === config?.id)
                return overridden ? (
                    <>
                        This configuration is going to override test {overridden.name} ({overridden.id})
                        {config?.name !== overridden.name && ` using new name ${config?.name}`}.<br />
                        <br />
                        Do you really want to proceed?
                    </>
                ) : null
            }}
            onImport={config => testApi.importTest(config as TestExport)}
            {...props}
        />
    )
}
