import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/grid';

export class HorreumSampleData extends LitElement {

    static styles = css`
        .full-height {
          height: 100%;
        }
    `;

    jsonRpc = new JsonRpc("horreum");

    static properties = {
        _loaded: {state: true, type: String},
        _status: ""
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInfo().then(response => {
            this._loaded = response.result.isLoaded;

        });
    }

    loadSampleData() {
        this.jsonRpc
            .getSampleData()
            .then(response => {
                this._status = response
                window.alert(response.toString())
                }
            )
    }

    render() {
        if (this._loaded == "true") {
            return this.render_dataLoaded();
        } else {
            return this.render_dataLoader();
        }

/*        return html `
            <vaadin-text-area
                    label="Comment"
                    .maxlength="${this.charLimit}"
                    .value="${this.text}"
                    .helperText="${`${this.text.length}/${this.charLimit}`}"
                    @value-changed="${(event: TextAreaValueChangedEvent) => {
                        this.text = event.detail.value;
                    }}"
            ></vaadin-text-area>
        `*/
    }

    render_dataLoaded() {
        return html`
            <vaadin-vertical-layout theme="spacing padding">
                <span>Sample Data already loaded</span>
            </vaadin-vertical-layout>
        `
    }
    render_dataLoader() {
        return html`
            <vaadin-vertical-layout theme="spacing padding">
                <vaadin-button @click=${() => this.loadSampleData()}>Load Data</vaadin-button>
            </vaadin-vertical-layout>
        `
    }

}
customElements.define('horreum-sample-data', HorreumSampleData);