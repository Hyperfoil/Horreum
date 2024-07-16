package io.hyperfoil.tools.horreum.exp.pasted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Proviles a fetch() method for javascript execution.
 * https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API
 */
public class ShimFetch implements ShimThenable {

    private static ObjectNode getDefaults(){
        ObjectNode rtrn = JsonNodeFactory.instance.objectNode();
        rtrn.put("method","GET");
        rtrn.put("mode","cors");
        rtrn.put("cache","no-cache");
        rtrn.put("credentials","same-origin");
        ObjectNode headers = JsonNodeFactory.instance.objectNode();
        headers.put("Content-Type","application/json");
        rtrn.set("headers",headers);
        rtrn.put("redirect","follow");
        rtrn.put("referrer","no-referrer");
        rtrn.put("body","");
        return rtrn;
    }
    private static void mergeObjectNodes(ObjectNode destination, ObjectNode source,boolean override){
        source.fields().forEachRemaining((entry)->{
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if(override || !destination.has(key)){
                destination.set(key,value);
            }else{
                if(destination.get(key).isArray()){
                    ArrayNode destinationArray = (ArrayNode) destination.get(key);
                    if(value.isArray()){
                        ((ArrayNode)value).forEach(destinationArray::add);
                    }else{
                        destinationArray.add(value);
                    }
                }else if(destination.get(key).isObject()){
                    ObjectNode destinationObject = (ObjectNode) destination.get(key);
                    if(value.isObject()){
                        mergeObjectNodes(destinationObject,(ObjectNode) value,override);
                    }else{
                        //turn destination[key] into [destination[key],value]
                        ArrayNode newArray = JsonNodeFactory.instance.arrayNode();
                        newArray.add(destinationObject);
                        newArray.add(value);
                        destination.set(key,newArray);
                    }
                }else{
                    //do nothing because not override
                }
            }
        });
    }

    private Value config;
    private Value url;

    public ShimFetch(Value url, Value config){
        this.url = url;
        this.config = config;
    }
//    @HostAccess.Export
//    @Override
//    public void onPromiseCreation(Value onResolve, Value onReject){
//
//        then(onResolve,onReject);
//    }

    @HostAccess.Export
    @Override
    public void then(Value onResolve, Value onReject){
        try{
            Object rtrn = apply(url,config);
            if(onResolve.hasMember("then")){
                onResolve.invokeMember("then",rtrn);
            }else{
                if(onResolve.canExecute()){
                    onResolve.execute(rtrn);
                }
            }
        }catch(Exception e){
            if(onReject.hasMember("then")) {
                onReject.invokeMember("then", e.getMessage());
            }else{
                if(onReject.canExecute()){
                    onReject.execute(e.getMessage());
                }
            }
        }

    }

    @HostAccess.Export
    public Object apply(Value url,Value options){
        String urlString = url.isString() ? url.asString() : url.toString();
        ObjectNode optionsNode = ExpUtil.convertMapping(options);
        mergeObjectNodes(optionsNode,getDefaults(),false);
        return apply(urlString,optionsNode,false);
    }
    public Object apply(String url,ObjectNode options,boolean redirected){
        try {
            //if the request is set to ignore tls checking
            if ("ignore".equalsIgnoreCase(options.has("tls") ? options.get("tls").textValue() : "")) {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                            public void checkClientTrusted(
                                    X509Certificate[] certs, String authType) {
                            }
                            public void checkServerTrusted(
                                    X509Certificate[] certs, String authType) {
                            }
                        }
                };
                // Install the all-trusting trust manager
                try {
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, trustAllCerts, new java.security.SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                } catch (GeneralSecurityException e) {
                    //what to do with the exception?
                }
            }
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            if (con instanceof HttpsURLConnection) {
                ((HttpsURLConnection) con).setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true; // yikes
                    }
                });
            }
            String requestMethod = options.has("method") ? options.get("method").asText("GET") : "GET";
            boolean followRedirect = options.has("follow") && "redirect".equalsIgnoreCase(options.get("follow").asText(""));
            con.setRequestMethod(requestMethod);
            con.setInstanceFollowRedirects(followRedirect);
            con.setConnectTimeout(30_000);//TODO add configuration for 30 seconds timeout
            con.setReadTimeout(30_000);//30 seconds
            ((ObjectNode)options.get("headers")).fields().forEachRemaining(entry->{
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if(value.isTextual()){
                    con.setRequestProperty(key,value.asText());
                }
            });
            //set the body if the request is post (also put?)
            if("POST".equalsIgnoreCase(requestMethod)){
                con.setDoOutput(true);
                String body = options.has("body") && options.get("body").isTextual() ? options.get("body").asText() : "";
                try(OutputStream os = con.getOutputStream()){
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input,0,input.length);
                }
            }

            int status = con.getResponseCode();
            if ((status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM) && followRedirect) {
                String location = con.getHeaderField("Location");
                return apply(location, options, true);//return the result of following the redirect
            }
            Reader streamReader = null;

            if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                streamReader = new InputStreamReader(con.getErrorStream());
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }


            String inputLine;
            StringBuffer content = new StringBuffer();
            try (BufferedReader in = new BufferedReader(streamReader)) {
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Map<String, List<String>> headerFields = con.getHeaderFields();

            ObjectNode headers = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, List<String>> entries : headerFields.entrySet()) {
                String values = "";
                for (String value : entries.getValue()) {
                    values += value + ",";
                    headers.put(entries.getKey() == null ? "" : entries.getKey(), value);
                }
            }
            String type = options.get("mode").asText("basic"); //not certain this is the correct place to read the type
            //https://developer.mozilla.org/en-US/docs/Web/API/Response/type
            return new ShimResponse(content.toString(),status,con.getResponseMessage(),headers,redirected,type,url);
        }catch (SocketTimeoutException e){
            return e;//someone else will turn that into json, right?
        } catch (MalformedURLException e) {
            return e;
        } catch (IOException e) {
            return e;
        }

    }

    @HostAccess.Export
    public static String btoa(Value input){
        String str = input == null ? "" : input.asString();
        return new String(Base64.getEncoder().encode(str.getBytes()), Charset.defaultCharset());
    }
    @HostAccess.Export
    public static String atob(Value input){
        String str = input == null ? "" : input.asString();
        return new String(Base64.getDecoder().decode(str.getBytes()), Charset.defaultCharset());
    }

}
