package io.hyperfoil.tools.horreum.exp.valid;

import java.sql.*;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * WARNING :: DO NOT merge into master
 *
 * This is NOT for master. this is to test the migration on production data backups.
 * A better interface is needed before merging to master.
 * T
 */
public class RunMigrationValidator {

    public static void main(String... args) throws SQLException, ClassNotFoundException {
        System.out.println("args: " + Arrays.asList(args));
        if (args.length < 3) {
            System.out.println("required args: jdbcUrl username password");
            System.exit(1);
        }
        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];

        Connection conn = getConnection(jdbcUrl, username, password);
//        System.out.println("purging");
//        purge(conn);
//        System.out.println("purged");
//        ComposableMigration.migrate(conn);

        Long testId = null;
        Long runId = null;
        Long labelId = null;

        List<JsonNode> labelValues = getExpLabelValues(runId,labelId,conn);
        List<JsonNode> datasets = getDatasets(runId,conn);

        if(datasets.size() != labelValues.size()){
            System.err.println("Count missmatch: datasets = "+datasets.size()+" labelValues = "+labelValues.size());
        }
        int length = Math.max(datasets.size(),labelValues.size());

        for(int i=0; i<length; i++){
            JsonNode dataset = datasets.size() > i ? datasets.get(i).get(0) : JsonNodeFactory.instance.nullNode();

            JsonNode labelValue = labelValues.size() > i ? labelValues.get(i) : JsonNodeFactory.instance.nullNode();

            List<String> diffs = diff(dataset,labelValue,"$["+i+"]","datasets","composed");
            if(!diffs.isEmpty()){
                System.out.println("["+i+"] "+dataset.get("workload")+" :");
                diffs.forEach(System.out::println);

            }

        }

    }
    private static boolean isScalar(JsonNode node){
        return Arrays.asList(JsonNodeType.BOOLEAN,JsonNodeType.NUMBER,JsonNodeType.STRING,JsonNodeType.NULL).contains(node.getNodeType());
    }
    private static String oneLiner(JsonNode node){
        if(isScalar(node)){
            return node.toPrettyString();
        }else{
            String toStr = node.toString();
            if(toStr.length() > 100 ){
                toStr = toStr.substring(0,100)+"...";
            }
            return node.getNodeType().name()+" "+node.size()+" "+toStr;
        }
    }
    public static List<String> diff(JsonNode a,JsonNode b,String path){
        return diff(a,b,path,"a","b");
    }
    public static List<String> diff(JsonNode a,JsonNode b,String path,String aName,String bName){
        List<String> rtrn = new ArrayList<>();
        if(!a.getNodeType().equals(b.getNodeType())){
            rtrn.add(path+"\n  "+aName+" = "+oneLiner(a)+"\n  "+bName+" = "+oneLiner(b));
        }else{
            if(a.isArray()){
                int length = Math.max(a.size(),b.size());
                for(int i=0; i<length;i++){
                    JsonNode aEntry = a.size() > i ? a.get(i) : JsonNodeFactory.instance.nullNode();
                    JsonNode bEntry = b.size() > i ? b.get(i) : JsonNodeFactory.instance.nullNode();
                    rtrn.addAll( diff(aEntry,bEntry,path+"["+i+"]",aName,bName));
                }
            }else if (a.isObject()){
                ObjectNode aObject = (ObjectNode) a;
                ObjectNode bObject = (ObjectNode) b;
                Set<String> keys = new HashSet<>();
                for( Iterator<String> iter = aObject.fieldNames(); iter.hasNext();){
                    keys.add(iter.next());
                }
                for( Iterator<String> iter = bObject.fieldNames(); iter.hasNext();){
                    keys.add(iter.next());
                }
                keys.stream().sorted().forEach(key->{
                    JsonNode aEntry = a.has(key) ? a.get(key) : JsonNodeFactory.instance.nullNode();
                    JsonNode bEntry = b.has(key) ? b.get(key) : JsonNodeFactory.instance.nullNode();
                    rtrn.addAll(diff(aEntry,bEntry,path+"."+key,aName,bName));
                });

            }else {//comparing scalars
                if(!a.toPrettyString().equals(b.toPrettyString())){
                    rtrn.add(path+"\n  "+aName+" = "+oneLiner(a)+"\n  "+bName+" = "+oneLiner(b));
                }
                //System.out.println(path+" a="+a.getNodeType()+" b="+b.getNodeType());
            }
        }
        return rtrn;
    }

    public static List<JsonNode> getDatasets(Long runId, Connection conn){
        List<JsonNode> rtrn = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try(PreparedStatement ps = conn.prepareStatement("select data from dataset where runid = ?")){
            ps.setLong(1,runId);
            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()){
                    rtrn.add( mapper.readTree( rs.getObject(1).toString() ) );
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }
    public static List<JsonNode> getExpLabelValues(Long runId, Long labelId, Connection conn){
        List<JsonNode> rtrn = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try(PreparedStatement ps = conn.prepareStatement("select data from exp_label_values where run_id = ? and label_id = ?")){
            ps.setLong(1,runId);
            ps.setLong(2,labelId);
            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()) {
                    rtrn.add( mapper.readTree( rs.getObject(1).toString() ) );
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }


    /**
     * get a JDBC connection to the postgres database
     * @param jdbcUrl
     * @param username
     * @param password
     * @return
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public static Connection getConnection(String jdbcUrl, String username, String password)
            throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * This removes all the "exp" content so that it can be reloaded from the existing Horreum model without conflicting ids.
     * @param conn
     * @return
     */
    public static boolean purge(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "delete from exp_label_values; delete from exp_run; delete from exp_extractor; delete from exp_label; delete from exp_labelgroup; delete from exp_label_reducers; delete from exp_temp_map_schema; delete from exp_temp_map_transform; ")) {
            return ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
