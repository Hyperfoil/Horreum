package io.hyperfoil.tools.horreum.exp.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.exp.data.Label;
import io.hyperfoil.tools.horreum.api.exp.LabelService;
import io.hyperfoil.tools.horreum.exp.data.*;
import io.hyperfoil.tools.horreum.exp.mapper.LabelMapper;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import org.hibernate.query.NativeQuery;

import java.util.*;

@ApplicationScoped
public class LabelServiceImpl implements LabelService {

    @Transactional
    @WithRoles
    @RolesAllowed(Roles.VIEWER)
    public Label get(long id){
        return LabelMapper.from(LabelDAO.findById(id));
    }

    //cannot be transactional and cannot be run in a transaction as it must write each to db as it runs
    public void calculateLabelValues(Collection<LabelDAO> labels, Long runId){
        for(LabelDAO l : labels){
            calculateLabelValue(l,runId);
        }
    }

    @Transactional
    public void calculateLabelValue(LabelDAO l, Long runId){
        ExtractedValues extractedValues = calculateExtractedValuesWithIterated(l,runId);
        //this is a separate case because the reducer receives the raw extracted value not a wrapper object
        boolean usesIterated = usesIterated(runId,l.id);
        LabelValueDao labelValue = new LabelValueDao();
        labelValue.label = l;
        labelValue.run = RunDao.findById(runId);
        labelValue.iterated = l.hasForEach() || usesIterated;
        if (l.extractors.size()==1) {
            if (extractedValues.size() == 1) {
                ExtractorDao e = l.extractors.iterator().next();
                LabelValueDao target = null;
                if(ExtractorDao.Type.VALUE.equals(e.type)){
                    target = getLabelValue(runId,e.targetLabel.id);
                }
                if(target == null){
                    //This is a problem but should not happen thanks to validation
                }
                String key = e.name;
                JsonNode data = extractedValues.get(key);
                labelValue.data = data;
                //TODO also check that data is an array?
                if (labelValue.iterated && target!=null) {
                    for(int i=0; i<data.size(); i++){
                        LabelValuePointerDao pointer = LabelValuePointerDao.create(i,labelValue,i,target);
                        labelValue.addPointer(pointer);
                    }
                }
                if (l.reducer == null) {
                    //Nothing to do
                } else {
                    //TODO reduce extractedValues before creating the labelValue
                    //labelValue.data = reduce(labelValue.data)
                    labelValue.data = l.reducer.evalJavascript(labelValue.data);
                }
                labelValue.persistAndFlush();
                em.flush();
            } else {
                //nothing was extracted for the 1 extractor...
                //how exactly would this happen?
            }
            //if we don't have to iterate over any values
        }else if ( !labelValue.iterated ){
            if(l.reducer == null){
                labelValue.data = extractedValues.asNode();
            }else{
                //TODO handle reducer
                //labelValue.data = reduce(extractedValues.asNode())
                labelValue.data = l.reducer.evalJavascript(extractedValues.asNode());
            }
            labelValue.persistAndFlush();//trying to force it to persist because of issue with LabelValueExtractor fromString creating
            //if we have to iterate over some of the extracted values
            // we might not need to separate these two if we accept the added newNode overhead?
        }else{// labelValue.iterated
            ArrayNode results = JsonNodeFactory.instance.arrayNode();

            labelValue.data = results;

            switch (l.multiType){
                case Length :

                    int maxLength = l.extractors.stream()
                            .filter(e -> ExtractorDao.Type.VALUE.equals(e.type)&& e.targetLabel.hasForEach() && extractedValues.hasNonNull(e.name))
                            .map(e->extractedValues.get(e.name).size()).max(Integer::compareTo).orElse(1);

                    for(int i=0; i < maxLength; i++){
                        ObjectNode newNode = JsonNodeFactory.instance.objectNode();
                        for(ExtractorDao e : l.extractors){
                            //TODO also check if the value is an array?
                            if(extractedValues.hasNonNull(e.name) && (e.forEach || extractedValues.isIterated(e.name))&& extractedValues.get(e.name).size() > i){
                                newNode.set(e.name,extractedValues.get(e.name).get(i));
                            }else if(!e.forEach &&  extractedValues.hasNonNull(e.name)  && (
                                    Label.ScalarVariableMethod.All.equals(l.scalarMethod) ||
                                            (i == 0 && Label.ScalarVariableMethod.First.equals(l.scalarMethod)) )
                            ){
                                newNode.set(e.name,extractedValues.get(e.name));
                            }
                            //create LvRef (if needed)
                            if(extractedValues.isIterated(e.name)){
                                //this is N db queries
                                LabelValueDao target = getLabelValue(runId,e.targetLabel.id);
                                if(target == null){
                                    //no bueno
                                }
                                LabelValuePointerDao pointer = LabelValuePointerDao.create(results.size(), labelValue,i,target);
                                labelValue.addPointer(pointer);
                            }
                        }
                        //newNode contains the parameters for this invocation
                        if(l.reducer == null){
                            results.add(newNode);
                        }else{
                            //TODO handle reducer results.add(reducedNode)
                            //results.add(reduce(newNode));
                            results.add(l.reducer.evalJavascript(newNode));
                        }
                    }
                    break;
                case NxN:
                    List<Map<String,Integer>> references = new ArrayList<>();
                    for(ExtractorDao e : l.extractors){

                        if( extractedValues.isIterated(e.name) || (e.forEach && ExtractorDao.Type.VALUE.equals(e.type) && extractedValues.hasNonNull(e.name)) ) {
                            int length = extractedValues.get(e.name).size();
                            if(references.isEmpty()){
                                for(int i=0; i<length; i++){
                                    Map<String,Integer> entry = new HashMap<>();
                                    entry.put(e.name,i);
                                    references.add(entry);
                                }
                            }else{
                                List<Map<String,Integer>> newReferencesList = new ArrayList<>();
                                for(int i=0; i<length; i++){
                                    for(int ri=0; ri < references.size(); ri++){
                                        Map<String,Integer> copy = new HashMap(references.get(ri));
                                        copy.put(e.name,i);
                                        newReferencesList.add(copy);
                                    }
                                }
                                references = newReferencesList;
                            }
                        }
                    }
                    for(int i=0; i< references.size(); i++){
                        Map<String,Integer> map = references.get(i);
                        ObjectNode newNode = JsonNodeFactory.instance.objectNode();
                        for(ExtractorDao e : l.extractors){
                            if(map.containsKey(e.name)){
                                int targetIndex = map.get(e.name);
                                LabelValueDao target = getLabelValue(runId,e.targetLabel.id);
                                if(target == null){
                                    //yikes
                                }
                                LabelValuePointerDao pointer = LabelValuePointerDao.create(i,labelValue,targetIndex,target);
                                labelValue.addPointer(pointer);
                                if(extractedValues.hasNonNull(e.name) && extractedValues.get(e.name).size() > targetIndex){
                                    newNode.set(e.name,extractedValues.get(e.name).get(targetIndex));
                                }
                            }else if (extractedValues.hasNonNull(e.name) && (
                                    Label.ScalarVariableMethod.All.equals(l.scalarMethod) ||
                                            (i == 0 && Label.ScalarVariableMethod.First.equals(l.scalarMethod)) )
                            ){
                                newNode.set(e.name,extractedValues.get(e.name));
                            }
                        }
                        if(l.reducer == null){
                            results.add(newNode);
                        }else{
                            //TODO handle reducer results.add(reducedNode)
                            //results.add(reduce(newNode));
                            results.add(l.reducer.evalJavascript(newNode));
                        }
                    }
                    break;
            }
            labelValue.persistAndFlush();
        }
        labelValue.persistAndFlush();//added to see if it fixes the rhivos test
        //this did fix it so there must be a codepath where persistAndFlush wasn't already called
        //can we simplify to just calling persist and flush here?
    }

    //ExtractedValues assumes one jsonnode for each extractor name.
    //a multilabelvalue would potentially have more than 1 and would have a UID that needs to be tracked
    //could store the multiple
    public static class ExtractedValues {
        private Map<String,Boolean> isIterated = new HashMap<>();
        private Map<String, JsonNode> data = new HashMap<>();

        public void add(String name,JsonNode data,boolean iterated){
            if(data == null || data.isNull()){
                return;
            }
            this.isIterated.put(name,iterated);
            this.data.put(name,data);
        }
        public boolean hasNonNull(String name){
            return data.containsKey(name);
        }
        public boolean isIterated(String name){
            return isIterated.getOrDefault(name,false);
        }
        public JsonNode get(String name){
            return get(name, NullNode.getInstance());
        }
        public JsonNode get(String name,JsonNode defaultValue){
            return data.getOrDefault(name, defaultValue);
        }
        public int size(){return data.size();}
        public Set<String> getNames(){return data.keySet();}
        public ObjectNode asNode(){
            ObjectNode rtrn = JsonNodeFactory.instance.objectNode();
            for(String name : getNames()){
                rtrn.set(name,get(name));
            }
            return rtrn;
        }
        public String toString(){
            StringBuilder sb = new StringBuilder();
            for(String name : getNames()){
                sb.append("\n  "+name+" "+isIterated(name)+" "+get(name));
            }
            return sb.toString();
        }
    }
    @Inject
    EntityManager em;

    @POST
    @Transactional
    public long add(LabelDAO label){
        if(label.id!=null && label.id == -1){
            label.id = null;
        }
        em.persist(label);
        em.flush();
        return label.id;
    }




    /*
     * Get all the LabelValues for the given test that target the specific schema.
     */
    public List<LabelValueDao> getBySchema(String schema,Long testId){
        return LabelValueDao.find("from LabelValueDao lv where lv.label.target_schema = ?1 and lv.label.parent.id = ?2",schema,testId).list();
    }


    public List<LabelDAO> getDescendantLabels(Long labelId){
        List<LabelDAO> rtrn = new ArrayList<>();

        //noinspection unchecked
        LabelDAO.getEntityManager().createNativeQuery(
                        """
                            with recursive bag(id) as ( values(:labelId) union select l.id from bag b,exp_extractor e left join exp_label l on e.parent_id = l.id where e.target_id =b.id) select * from bag
                            """
                ).setParameter("labelId",labelId)
                .unwrap(NativeQuery.class)
                .addScalar("id",Long.class)
                .list()
                .forEach(v->{
                    Long id = (Long)v;
                    LabelDAO found = LabelDAO.getEntityManager().getReference(LabelDAO.class,id);
                    rtrn.add(found);
                });
        return rtrn;
    }

    /*
     * Gets the LabelValues that reference the given index in parent labelValue
     */
    public List<LabelValueDao> getDerivedValues(LabelValueDao parent,int index){
        return LabelValueDao.find("from LabelValueDao lv where exists (from LabelValuePointerDao lvp where lvp.child = lv and lvp.target=?1 and lvp.targetIndex = ?2)",parent,index).list();
    }

    //get the labelValues for all instances of a target schema for a test
    //could also have a labelValues based on label name, would that be useful? label name would not be merge-able across multiple labels
    public List<LabelService.ValueMap> labelValues(String schema,long testId, List<String> include, List<String> exclude){
        List<LabelService.ValueMap> rtrn = new ArrayList<>();
        String labelNameFilter = "";
        if (include!=null && !include.isEmpty()){
            if(exclude!=null && !exclude.isEmpty()){
                include = new ArrayList<>(include);
                include.removeAll(exclude);
            }
            if(!include.isEmpty()) {
                labelNameFilter = " AND l.name in :include";
            }
        }
        //includeExcludeSql is empty if include did not contain entries after exclude removal
        if(labelNameFilter.isEmpty() && exclude!=null && !exclude.isEmpty()){
            labelNameFilter=" AND l.name NOT in :exclude";
        }
        //noinspection unchecked
        List<Object[]> found = em.createNativeQuery(
                        """
                        with bag as (
                            select
                                r.test_id, lv.run_id, lt.id as target_label_id, lvp.targetindex, l.name,
                                jsonb_agg(lv.data -> lvp.childindex::int) as data
                            from exp_label_values lv
                                right join exp_label_value_pointer lvp on lvp.child_label_id = lv.label_id and lvp.child_run_id = lv.run_id
                                left join exp_label l on l.id = lv.label_id
                                left join exp_label lt on lt.id = lvp.target_label_id
                                left join exp_run r on r.id = lv.run_id
                            where lt.target_schema = :schema and r.test_id = :testId
                            LABEL_NAME_FILTER
                            group by r.test_id,lv.run_id,lt.id,lvp.targetindex, l.name
                        )
                        select
                            targetindex,target_label_id, run_id, test_id,
                            jsonb_object_agg(name,(case when jsonb_array_length(data) > 1 then data else data->0 end)) as data
                        from bag
                        group by test_id,run_id,target_label_id,targetindex;
                        """.replace("LABEL_NAME_FILTER",labelNameFilter)
                ).setParameter("schema",schema)
                .setParameter("testId",testId)
                .unwrap(NativeQuery.class)
                .addScalar("targetindex",Long.class)
                .addScalar("target_label_id",Long.class)
                .addScalar("run_id",Long.class)
                .addScalar("test_id",Long.class)
                .addScalar("data", JsonBinaryType.INSTANCE)
                .list();
        for(Object[] object : found){
            // tuple (labelId,index) should uniquely identify which label_value entry "owns" the ValueMap for the given test and run
            // note a label_value can have multiple values that are associated with a (labelId,index) if it is NxN
            Long index = (Long)object[0];
            Long labelId = (Long)object[1];
            Long runId = (Long)object[2];
            //object[3] is testId
            ObjectNode data = (ObjectNode)object[4];

            LabelService.ValueMap vm = new LabelService.ValueMap(data,index,labelId,runId,testId);
            rtrn.add(vm);
        }
        return rtrn;
    }
    //This is the labelValues endpoint that more closely matches what currently exists in Horreum if run = dataset
    //filter,before,after,sort,direction,limit,page, and multiFilter are not yet supported
    List<LabelService.ValueMap> labelValues(
            long  testId,
            String filter,
            String before,
            String after,
            String sort,
            String direction,
            int limit,
            int page,
            List<String> include,
            List<String> exclude,
            boolean multiFilter){
        List<LabelService.ValueMap> rtrn = new ArrayList<>();
        String labelNameFilter = "";
        if (include!=null && !include.isEmpty()){
            if(exclude!=null && !exclude.isEmpty()){
                include = new ArrayList<>(include);
                include.removeAll(exclude);
            }
            if(!include.isEmpty()) {
                labelNameFilter = " AND l.name in :include";
            }
        }
        //includeExcludeSql is empty if include did not contain entries after exclude removal
        if(labelNameFilter.isEmpty() && exclude!=null && !exclude.isEmpty()){
            labelNameFilter=" AND l.name NOT in :exclude";
        }

        //noinspection rawtypes
        NativeQuery query = (NativeQuery) em.createNativeQuery(
                        """
                        with bag as (
                            select
                                r.test_id, lv.run_id, l.name,
                                jsonb_agg(lv.data) as data
                            from exp_label_values lv
                                left join exp_label l on l.id = lv.label_id
                                left join exp_run r on r.id = lv.run_id
                            where r.test_id = :testId
                            LABEL_NAME_FILTER
                            group by r.test_id,lv.run_id,l.name
                        )
                        select
                            run_id, test_id,
                            jsonb_object_agg(name,(case when jsonb_array_length(data) > 1 then data else data->0 end)) as data
                        from bag
                        group by test_id,run_id;
                        """.replace("LABEL_NAME_FILTER",labelNameFilter)
                )
                .setParameter("testId",testId);
        if(!labelNameFilter.isEmpty()){
            if(labelNameFilter.contains("include")){
                query.setParameter("include",include);
            }
            if(labelNameFilter.contains("exclude")){
                query.setParameter("exclude",exclude);
            }
        }

        //noinspection unchecked
        List<Object[]> found = query
                .unwrap(NativeQuery.class)
                .addScalar("run_id",Long.class)
                .addScalar("test_id",Long.class)
                .addScalar("data",JsonBinaryType.INSTANCE)
                .list();
        for(Object[] object : found){
            // tuple (labelId,index) should uniquely identify which label_value entry "owns" the ValueMap for the given test and run
            // note a label_value can have multiple values that are associated with a (labelId,index) if it is NxN
            Long runId = (Long)object[0];
            //object[1] is testId
            ObjectNode data = (ObjectNode)object[2];

            LabelService.ValueMap vm = new LabelService.ValueMap(data,-1,-1,runId,testId);
            rtrn.add(vm);
        }
        return rtrn;
    }

    public List<LabelService.ValueMap> labelValues(long labelId, long runId, long testId){
        return labelValues(labelId,runId,testId,Collections.emptyList(),Collections.emptyList());
    }
    //testId is only needed to create the ValueMap because labels are currently scoped to a test
    public List<LabelService.ValueMap> labelValues(long labelId, long runId, long testId, List<String> include, List<String> exclude){
        List<LabelService.ValueMap> rtrn = new ArrayList<>();
        String labelNameFilter = "";
        if (include!=null && !include.isEmpty()){
            if(exclude!=null && !exclude.isEmpty()){
                include = new ArrayList<>(include);
                include.removeAll(exclude);
            }
            if(!include.isEmpty()) {
                labelNameFilter = " AND l.name in :include";
            }
        }
        //includeExcludeSql is empty if include did not contain entries after exclude removal
        if(labelNameFilter.isEmpty() && exclude!=null && !exclude.isEmpty()){
            labelNameFilter=" AND l.name NOT in :exclude";
        }
        //could not be done in hql because of the json manipulation
        @SuppressWarnings("rawtypes")
        NativeQuery query = ((NativeQuery)em.createNativeQuery(
                        """
                           with bag as (
                                select lvp.targetindex,
                                    lvp.target_label_id,
                                    l.name,jsonb_agg(lv.data -> lvp.childindex::int) as data
                                from exp_label_values lv
                                    right join exp_label_value_pointer lvp on lvp.child_label_id = lv.label_id and lvp.child_run_id = lv.run_id
                                    left join exp_label l on l.id = lv.label_id
                                where lvp.target_label_id = :label_id
                                    and lvp.target_run_id = :run_id
                                    LABEL_NAME_FILTER
                                group by target_label_id,targetindex,name)
                           select
                                targetindex as index,
                                target_label_id as label_id,
                                jsonb_object_agg(name,(case when jsonb_array_length(data) > 1 then data else data->0 end)) as data
                           from bag
                           group by target_label_id,targetindex order by targetindex asc
                           """.replace("LABEL_NAME_FILTER",labelNameFilter)
                        //is the target_run_id = :run_id necessary? I think target_run_id === child_run_id
                )
                .setParameter("label_id",labelId)
                .setParameter("run_id",runId));

        if(labelNameFilter.contains(":include")){
            query.setParameter("include",include);
        }else if (labelNameFilter.contains(":exclude")){
            query.setParameter("exclude",exclude);
        }

        query.unwrap(NativeQuery.class)
                .addScalar("index",Long.class)
                .addScalar("label_id",Long.class)
                .addScalar("data",JsonBinaryType.INSTANCE);

        //noinspection unchecked
        List<Object[]> found = query.list();
        for(Object[] object : found){
            LabelService.ValueMap vm = new LabelService.ValueMap((ObjectNode) object[2],(Long)object[0],(Long)object[1],runId,testId);
            rtrn.add(vm);
        }
        return rtrn;
    }



    /*
     * Detects direct dependency on an iterated label_value
     * @return true iff the label depends on an iterated label_value for the specific run
     */
    public boolean usesIterated(long runId, long labelId){
        Boolean response = (Boolean) em.createNativeQuery("""
                select exists (select 1 from exp_extractor e left join exp_label_values lv on e.target_id = lv.label_id where e.parent_id = :labelId and  lv.run_id = :runId and lv.iterated)
                """).setParameter("labelId",labelId)
                .setParameter("runId",runId)
                .unwrap(NativeQuery.class)
                .getSingleResult();
        return response != null && response;
    }

    public LabelValueDao getLabelValue(long runId, long labelId){
        return LabelValueDao.find("from LabelValueDao lv where lv.run.id=?1 and lv.label.id=?2",runId,labelId).firstResult();
    }

    private void debug(String sql,Object...args){
        List<Object> found;
        NativeQuery q = LabelDAO.getEntityManager().createNativeQuery(sql).unwrap(NativeQuery.class);
        for(int i=0; i<args.length; i++){
            q.setParameter(i+1,args[i]);
        }
        found = q.getResultList();
        if(found!=null){
            found.forEach(row->{
                if(row == null){
                    //
                }else{
                    if(row instanceof Object[]){
                        System.out.printf("%s%n",Arrays.asList((Object[])row).toString());
                    }else {
                        System.out.printf("%s%n",row.toString());
                    }
                }
            });
        }
    }

    /*
        LabelValueExtractor on an iterated label_value will need to run N separate times because it will be forced to be an iterated value
     */
    public ExtractedValues calculateExtractedValuesWithIterated(LabelDAO l, long runId){
        ExtractedValues rtrn = new ExtractedValues();

        //debugging again
        //a for-each that isn't iterated...?
        //when m.dtype = 'LabelValueExtractor' and m.jsonpath is not null and m.jsonpath != '' and m.foreach and jsonb_typeof(m.lv_data) = 'array' then extract_path_array(m.lv_data,m.jsonpath::jsonpath)

        //do we need to check jsonb_typeof
        //right now this assumes we don't get garbage data... probably not a safe assumption
        //unchecked is how you know the code is great :)
        @SuppressWarnings("unchecked")
        List<Object[]> found = LabelDAO.getEntityManager().createNativeQuery("""
            with m as (select e.name, e.type, e.jsonpath, e.foreach, e.column_name, lv.data as lv_data, lv.iterated as lv_iterated, r.data as run_data, r.metadata as run_metadata from exp_extractor e 
            left join exp_label_values lv on e.target_id = lv.label_id, exp_run r  where e.parent_id = :label_id and (lv.run_id = :run_id or lv.run_id is null) and r.id = :run_id),
            n as (select m.name, m.type, m.jsonpath, m.foreach, m.lv_iterated ,m.lv_data, (case
                when m.type = 'PATH' and m.jsonpath is not null then jsonb_path_query_array(m.run_data,m.jsonpath::jsonpath)
                when m.type = 'METADATA' and m.jsonpath is not null and m.column_name = 'metadata' then jsonb_path_query_array(m.run_metadata,m.jsonpath::jsonpath)
                when m.type = 'VALUE' and m.jsonpath is not null and m.jsonpath != '' and m.lv_iterated then extract_path_array(m.lv_data,m.jsonpath::jsonpath)
                
                when m.type = 'VALUE' and m.jsonpath is not null and m.jsonpath != '' then jsonb_path_query_array(m.lv_data,m.jsonpath::jsonpath)
                when m.type = 'VALUE' and (m.jsonpath is null or m.jsonpath = '') then to_jsonb(ARRAY[m.lv_data])
                else '[]'::jsonb end) as found from m)
            select n.name as name,(case when jsonb_array_length(n.found) > 1 or strpos(n.jsonpath,'[*]') > 0 then n.found else n.found->0 end) as data, n.lv_iterated as lv_iterated from n
        """).setParameter("run_id",runId).setParameter("label_id",l.id)
                //TODO add logging in else '[]'
                .unwrap(NativeQuery.class)
                .addScalar("name",String.class)
                .addScalar("data", JsonBinaryType.INSTANCE)
                .addScalar("lv_iterated",Boolean.class)
                .getResultList();


        if(found.isEmpty()){
            //TODO alert error or assume the data missed all the labels?
        }else {
            for(int i=0; i<found.size(); i++){
                Object[] row = (Object[])found.get(i);
                String name = (String)row[0];
                JsonNode data = (JsonNode) row[1];
                Boolean iterated = (Boolean)row[2];
                rtrn.add(name,data, iterated != null && iterated);
            }
        }
        return rtrn;
    }


}
