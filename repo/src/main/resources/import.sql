INSERT INTO test (id,name,description,schema,view) VALUES (1,'SPECjEnterprise2010','spec.org 2010 enterprise benchmark',null,
    '[
        {
          "Header" : "Start",
          "accessor" : "v => window.DateTime.fromMillis(v.start).toFormat(\"yyyy-LL-dd HH:mm:ss ZZZ\")"
        },
        {
          "Header" : "Stop",
          "accessor" : "v => window.DateTime.fromMillis(v.stop).toFormat(\"yyyy-LL-dd HH:mm:ss ZZZ\")"
        },
        {
          "Header" : "Scale",
          "accessor" : "scale",
          "jsonpath" : "$.faban.run.SPECjEnterprise.\"fa:runConfig\".\"fa:scale\".\"text()\""
        },
        {
          "Header" : "Ramp Up",
          "accessor" : "rampup",
          "jsonpath" : "$.faban.run.SPECjEnterprise.\"fa:runConfig\".\"fa:runControl\".\"fa:rampUp\".\"text()\""
        },
        {
          "Header" : "Steady State",
          "accessor" : "steadystate",
          "jsonpath" : "$.faban.run.SPECjEnterprise.\"fa:runConfig\".\"fa:runControl\".\"fa:steadyState\".\"text()\""
        },
        {
          "Header" : "Ramp Down",
          "accessor" : "rampdown",
          "jsonpath" : "$.faban.run.SPECjEnterprise.\"fa:runConfig\".\"fa:runControl\".\"fa:rampDown\".\"text()\""
        },
        {
          "Header" : "JFR",
          "accessor" : "(v)=>{return v && v[\"jfr\"] && v[\"jfr\"].includes(\"StartFlightRecording\") ? \"✔\" : \"✕\"}",
          "jsonpath" : "$.qdup.state.\"benchserver4.perf.lab.eng.rdu2.redhat.com\".**.JAVA_OPTS"
        },
        {
          "Header" : "LargePages",
          "accessor" : "largepages",
          "jsonpath" : "$.qdup.state.\"benchserver4.perf.lab.eng.rdu2.redhat.com\".**.JAVA_OPTS",
          "render" : "(v)=>{return v.includes(\"LargePages\") ? \"✔\" : \"✕\"}"
        },
        {
          "Header" : "GC total pause",
          "accessor" : "gc",
          "jsonpath" : "jsonb_path_query_array(data,''$.benchserver4.gclog[*] ? ( exists(@.capacity) && exists(@.seconds) )'')",
          "render" : "(v=[])=>{ if(v.length === 0){return \"--\"} const totalSeconds = v.reduce((total,entry)=>total+entry.seconds,0.0); return Number(totalSeconds).toFixed(3);}"
        },
        {
          "Header" : "Faban ID",
          "accessor" : "fabanid",
          "jsonpath" : "$.faban.summary.benchResults.benchSummary.runId.\"text()\"",
          "render" : "(value)=>`<a href=\"http://benchclient1.perf.lab.eng.rdu2.redhat.com:9980/resultframe.jsp?runId=${value}&result=summary.xml\" target=\"_blank\">${value}</a>`"
        }
    ]'
);

INSERT INTO hook (id,url,type,target,active) VALUES (  0,'http://laptop:8080/api/log','new/test',  1,true);
INSERT INTO hook (id,url,type,target,active) VALUES (  1,'http://laptop:8080/api/log','new/test', -1,false);