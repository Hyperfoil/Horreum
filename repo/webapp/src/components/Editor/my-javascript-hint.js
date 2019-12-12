//https://codemirror.net/demo/complete.html

import CodeMirror from 'codemirror';

const Pos = CodeMirror.Pos;

var stringProps = ("charAt charCodeAt indexOf lastIndexOf substring substr slice trim trimLeft trimRight " +
                     "toUpperCase toLowerCase split concat match replace search").split(" ");
var arrayProps = ("length concat join splice push pop shift unshift slice reverse sort indexOf " +
                    "lastIndexOf every some filter forEach map reduce reduceRight ").split(" ");
var funcProps = "prototype apply call bind".split(" ");
var javascriptKeywords = ("break case catch class const continue debugger default delete do else export extends false finally for function " +
                  "if in import instanceof new null return super switch this throw true try typeof var void while with yield").split(" ");
var coffeescriptKeywords = ("and break catch class continue delete do else extends false finally for " +
                  "if in instanceof isnt new no not null of off on or return switch then throw true try typeof until void while with yes").split(" ");


function scriptHint(editor, keywords, getToken, options) {
    var cur = editor.getCursor(), token = getToken(editor, cur);
    const line = editor.getLine(cur.line);
    var innerMode = CodeMirror.innerMode(editor.getMode(), token.state);
    var start = cur.ch, end = cur.ch
    while (start && /\w/.test(line.charAt(start - 1))) {--start;}
    while (end < line.length && /\w/.test(line.charAt(end))) {++end;}

    
    if (innerMode.mode.helperType === "json") return;
    token.state = innerMode.state;

    return {
        list: ["foo","bar"],
        from: CodeMirror.Pos(cur.line,start),
        to: CodeMirror.Pos(cur.line,end)
    }

}

function javascriptHint(editor, options) {
    return scriptHint(editor, javascriptKeywords,
                      function (e, cur) {return e.getTokenAt(cur);},
                      options);
};


CodeMirror.registerHelper("hint", "javascript", javascriptHint);
