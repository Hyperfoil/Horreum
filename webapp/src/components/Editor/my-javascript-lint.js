// modified version of https://codemirror.net/addon/lint/javascript-lint.js that only reports Errors
// CodeMirror, copyright (c) by Marijn Haverbeke and others
// Distributed under an MIT license: https://codemirror.net/LICENSE
"use strict";
import CodeMirror from 'codemirror';
// declare global: JSHINT

function validator(text, options) {
    if (!window.JSHINT) {
        if (window.console) {
            window.console.error("Error: window.JSHINT not defined, CodeMirror JavaScript linting cannot run.");
        }
        return [];
    }
    if (!options.indent) // JSHint error.character actually is a column index, this fixes underlining on lines using tabs for indentation
        options.indent = 1; // JSHint default value is 4
    window.JSHINT("const _tovalidate_ = "+text+";", options, options.globals);
    var errors = window.JSHINT.data().errors, result = [];
    if (errors) parseErrors(errors, result);
    return result;
}

CodeMirror.registerHelper("lint", "javascript", validator);

function parseErrors(errors, output) {
    for (var i = 0; i < errors.length; i++) {
        var error = errors[i];
        if (error) {
            if (error.line <= 0) {
                if (window.console) {
                    window.console.warn("Cannot display JSHint error (invalid line " + error.line + ")", error);
                }
                continue;
            }

            var start = error.character - 1, end = start + 1;
            if (error.evidence) {
                var index = error.evidence.substring(start).search(/.\b/);
                if (index > -1) {
                    end += index;
                }
            }

            // Convert to format expected by validation service
            if (!error.code.startsWith('W')) {
                var hint = {
                    message: error.reason,
                    severity: error.code ? (error.code.startsWith('W') ? "warning" : "error") : "error",
                    from: CodeMirror.Pos(error.line - 1, start),// -1 because we are pre-pending the line in Editor
                    to: CodeMirror.Pos(error.line - 1, end)
                };

                output.push(hint);
            }
        }
    }
}

