import { RefObject } from "react"
import { useReactToPrint } from "react-to-print"
import { Button } from "@patternfly/react-core"

type PrintButtonProps = {
    printRef: RefObject<HTMLDivElement | null>
}

export default function PrintButton(props: PrintButtonProps) {
    const printHandle = useReactToPrint({
        contentRef: props.printRef,
        pageStyle: "@page { margin: 1cm; }",
    })
    return (
        <Button
            onClick={() => {
                if (printHandle) printHandle()
            }}
        >
            Export to PDF
        </Button>
    )
}
