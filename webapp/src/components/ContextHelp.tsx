import { useEffect, useState } from "react"

import { Button, Popover, Split, SplitItem } from "@patternfly/react-core"

const clippy =
    "XXX****///////*\nXXY,.,,*/(########((//*\nXX..,*/(XY %#(//\nXX..,/(XX %#//\nXX..,/.XX   %(//\nXY#%#%&&&&&%&XX  %(//\nX&(@@@* ..*&@@@@@&XY   ##/*/\nY@%Y   ..*(XXY #(#(,\nX..,,.,*/XXY@@@@@@@@@@&,\nXX..,XX  /,,**Y @@@@\nX&%%%&&Y .,/XY   *,,*X .@@\n.Y &##(#%&@@@@@   .,*XXY ..,Y  @\n,.Y@&%%&&&@@@@@&  .,*XX,.Y  ..*\n*.Y*@@@@@@@@@.  .,*/Y .Y  #%##%%&@@@Y.,/\nY*,....Y  ...,,*/.Y  *.Y#@&&&&@@@@@@   .,*.\nY,/*********//X   *..Y@@@@@@@@@%  .,**\nXY%%%%XX*,...X ..,,*/\nXY####XX   /**,,,,,,****/.\nXY####XXY&&&&%\nXY####   %%%XY  %%%%.Y   /((\nXY####   ####XY %%%%Y   ##%%,\nXY##((.  ##((XY %%#(Y  ((#%\nXY##((*  ((((XY %%#(Y (/(%,\nXY##(/(  ((((XY %%((Y,//#%\nXY##(/(  ((((XY %%((Y#//##\nXY##/*/  #((/XY %%(/Y#*/(.\nXY%(**  ((/*XY #%#/Y#//(\nXY%(*,  ,(/*.XY*#(/Y%/*(*\nXY%#*.*  #(**XY.((/*   /(//(\nXY(#/.,  /#/,*X   /*//.Y#//(\nXY%(,,   ##/,*X  ,,*/Y #/*(\nXY&%/.*   (%(*,*Y  ,.,*(Y  %(//.\nXX%(.,Y %%#/,,,,,*/(#X%#(/(\nXX&#*.*X /&&*XY&#(/(\nXX&(*,XXX %#(//\nXX.%/,,XXX(((/.\nXX#%*.,XXY  .*((/\nXX%%*.,XXY **//\nXXY.%(,.*XX  ,,*/.\nXXY&%/,.*XY ,..,*/\nXXX&%#/,..,,***,,..,,**/\nXXX&&%#(//////((("

export default function ContextHelp() {
    const decomp = clippy.replaceAll("X", "        ").replaceAll("Y", "    ")
    const [open, setOpen] = useState(false)
    const [yes, setYes] = useState(1)
    useEffect(() => {
        const d = new Date()
        if (d.getMonth() === 3 && Math.random() < 0.3) {
            setTimeout(() => setOpen(true), 10000)
        }
    }, [])
    return (
        <Popover
            isVisible={open}
            shouldClose={() => setOpen(false)}
            position="top-end"
            bodyContent={
                <Split>
                    <SplitItem>
                        <pre style={{ fontSize: "3px", fontWeight: "900" }}>{decomp}</pre>
                    </SplitItem>
                    <SplitItem style={{ padding: "10px" }}>
                        <br />
                        It looks like you have some trouble with performance.
                        <br />
                        <br />
                        Do you want me to fix it for you?
                        <div style={{ display: "flex", gap: "10px", margin: "5px" }}>
                            <Button
                                style={{ visibility: yes ? "visible" : "hidden" }}
                                onMouseOver={() => setYes(0)}
                                onFocus={() => setYes(0)}
                            >
                                Yes
                            </Button>
                            <Button onClick={() => setOpen(false)}>No</Button>
                            <Button
                                style={{ visibility: yes ? "hidden" : "visible" }}
                                onMouseOver={() => setYes(1)}
                                onFocus={() => setYes(1)}
                            >
                                Yes
                            </Button>
                        </div>
                    </SplitItem>
                </Split>
            }
            distance={0}
        >
            <div style={{ position: "fixed", bottom: "0px", right: "50px", opacity: 0 }}>xxx</div>
        </Popover>
    )
}
