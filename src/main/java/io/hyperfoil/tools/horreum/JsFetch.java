package io.hyperfoil.tools.horreum;

import io.hyperfoil.tools.yaup.json.Json;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Map;


public class JsFetch {

   public static final Json DEFAULT_OPTIONS = Json.fromString("{" +
      "\"method\":\"GET\"," + // GET, POST, PUT, DELETE
      "\"mode\":\"cors\","+ //no-cors, cors, same-origin
      "\"cache\":\"no-cache\","+ //default, no-cache, reload, force-cahe, only-if-cached
      "\"credentials\":\"same-origin\","+ //include, same-origin, omit
      "\"headers\":{"+
      "    \"Content-Type\":\"application/json\""+ // 'Content-Type': 'application/x-www-form-urlencoded',
      "},"+
      "\"redirect\":\"follow\"," + // manual, *follow, error
      "\"referrer\":\"no-referrer\"," + // no-referrer, *client
      "\"body\":\"\"" + //
      "}",null,true);

   public class MapProxyObject implements ProxyObject {
      private final Map<String, Object> map;

      public MapProxyObject(Map<String, Object> map) {
         this.map = map;
      }

      public void putMember(String key, Value value) {
         map.put(key, value.isHostObject() ? value.asHostObject() : value);
      }

      public boolean hasMember(String key) {
         return map.containsKey(key);
      }

      public Object getMemberKeys() {
         return map.keySet().toArray();
      }

      public Object getMember(String key) {
         Object v = map.get(key);
         if (v instanceof Map) {
            return new MapProxyObject((Map<String, Object>)v);
         } else {
            return v;
         }
      }

      public Map<String, Object> getMap() {
         return map;
      }
   }


   public int count = 10;

   @HostAccess.Export
   public Object jsApply(Value url,Value options){
      if (url.isString()){
         String urlString = url.asString();
         Json optionsJson = options == null ? new Json(false) : Json.fromGraalvm(options);
         Object v  = apply(urlString,optionsJson);
         if(v instanceof Json){
            v = new JsProxyObject((Json)v);
         }
         return v;
      }
      return "ERROR url="+url+" options="+options;
   }
   public Object apply(String url, Json options) {
      System.out.println("Apply("+url+", "+options+" )");

      if(options == null){
         options = DEFAULT_OPTIONS;
      }else {
         options.merge(DEFAULT_OPTIONS, false);
      }
      try {
         HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
         con.setRequestMethod(options.getString("method","GET"));
         con.setInstanceFollowRedirects(true);
         con.setConnectTimeout(5000);
         con.setReadTimeout(5000);

         options.getJson("headers",new Json()).forEach((key,value)->{
            con.setRequestProperty(key.toString(),value.toString());
         });

         if("POST".equals(options.getString("method","GET"))){
            con.setDoOutput(true);
            String body = options.get("body") == null ? "" : options.get("body").toString();

            try(OutputStream os = con.getOutputStream()) {
               byte[] input = body.getBytes("utf-8");
               os.write(input, 0, input.length);
            }
         }

         int status = con.getResponseCode();

         if (status == HttpURLConnection.HTTP_MOVED_TEMP
            || status == HttpURLConnection.HTTP_MOVED_PERM) {
            String location = con.getHeaderField("Location");
            URL newUrl = new URL(location);
            return apply(newUrl.toString(),options);//return the result of following the redirect
         }

         Reader streamReader = null;

         if (status > 299) {
            streamReader = new InputStreamReader(con.getErrorStream());
         } else {
            streamReader = new InputStreamReader(con.getInputStream());
         }

         BufferedReader in = new BufferedReader(streamReader);
         String inputLine;
         StringBuffer content = new StringBuffer();
         while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
         }
         in.close();

         Map<String,List<String>> headerFields = con.getHeaderFields();

         Json result = new Json();
         result.set("status",status);
         result.set("statusText",con.getResponseMessage());
         result.set("headers",new Json());
         for (Map.Entry<String, List<String>> entries : con.getHeaderFields().entrySet()) {
            String values = "";
            for (String value : entries.getValue()) {
               values += value + ",";
               result.getJson("headers").add(entries.getKey()==null ? "" : entries.getKey(),value);
            }
            System.out.println("header: "+entries.getKey()+" : "+values);
         }

         if( (headerFields.containsKey("Content-Type") && headerFields.get("Content-Type").contains("application/json") ) || (headerFields.containsKey("content-type") && headerFields.get("content-type").contains("application/json"))){
            result.set("body",Json.fromString(content.toString(),new Json(false)));
         }else{
            result.set("body",content.toString());
         }

         try {
            System.out.println("URL " + url + "\n" + result.toString(2));

         }catch(Exception e){
            e.printStackTrace();
         }

         return new JsProxyObject(result);
      } catch (ProtocolException e) {
         e.printStackTrace();
      } catch (MalformedURLException e) {
         e.printStackTrace();
      } catch (IOException e) {
         e.printStackTrace();
      }

      return null;
   }

   public static String btoa(Value input){
      String str = input == null ? "" : input.asString();
      return new String(Base64.getEncoder().encode(str.getBytes()), Charset.defaultCharset());
   }
   public static String atob(Value input){
      String str = input == null ? "" : input.asString();
      return new String(Base64.getDecoder().decode(str.getBytes()), Charset.defaultCharset());
   }

}

