import { Button } from "@patternfly/react-core"

import React, {useContext, useState} from "react";
import LoginModal from "../Login";
import {AuthBridgeContext} from "../context/AuthBridgeContext";
import {AuthContextType} from "../context/@types/authContextTypes";


export const LoginLogout = () => {
    const { isOidc, isAuthenticated, signIn, signOut } = useContext(AuthBridgeContext) as AuthContextType;

    const [loginModalOpen, setLoginModalOpen] = useState(false)

    if (isAuthenticated) {
        return (
            <Button onClick={() => {
                if (isOidc) {
                    void signOut()
                } else {
                    window.location.replace(window.location.origin)
                }
            }}>
                Log out
            </Button>
        )
    } else {
        return <>
            <Button onClick={() => {
                if (isOidc) {
                    void signIn()
                } else {
                    setLoginModalOpen(true)
                }
            }}>Log in</Button>
            <LoginModal
                isOpen={loginModalOpen}
                username={""}
                password={""}
                onClose={() => setLoginModalOpen(false)}
            />
        </>
    }
}
