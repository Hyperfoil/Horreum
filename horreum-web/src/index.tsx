import React from "react"
import ReactDOM from "react-dom/client"

import "./index.css"
import App from "./App"
import * as serviceWorker from "./serviceWorker"

import jsonpath from "jsonpath" 
import { BrowserRouter } from 'react-router-dom' 

const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement)
root.render(  
    <BrowserRouter>
      <App />
    </BrowserRouter> 
)

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://bit.ly/CRA-PWA
serviceWorker.unregister()

//public API for and user Js
// global.Duration = Duration;
// global.DateTime = DateTime;
globalThis.jsonpath = jsonpath
