package io.hyperfoil.tools.horreum.exp.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for all extractors to represent
 * name is intentionally not unique for a parent so that Horreum merges matched names
 * This allows a label to "mutate" with changes to data location
 */
@Entity
@Table(name = "exp_extractor")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractorDao extends PanacheEntity {
    public static final String PREFIX="$";
    public static final String FOR_EACH_SUFFIX = "[]";
    public static final String NAME_SEPARATOR=":";
    public static final String METADATA_PREFIX = "{";
    public static final String METADATA_SUFFIX = "}";
    private static final AtomicInteger counter = new AtomicInteger(0);

    @NotNull(message = "extractor must reference a label")
    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    public LabelDAO parent;

    public ExtractorDao copy(LabelDAO copyLabel) {
        ExtractorDao copy = new ExtractorDao();
        copy.parent = copyLabel;
//        copy.targetLabel = copyLabel;
        copy.name = this.name;
        copy.type = this.type;
        copy.jsonpath = this.jsonpath;
        copy.column_name = this.column_name;
        return copy;
    }

    public enum Type {PATH, VALUE, METADATA}

    @NotNull(message="extractor name cannot be null")
    public String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JsonIgnore
    public Type type;

    public String jsonpath;

    /** The id for the label that produces the value this extractor reads **/
    @ManyToOne(cascade = {CascadeType.PERSIST,CascadeType.MERGE})
    @JoinColumn(name="target_id")
    public LabelDAO targetLabel;

    public String column_name; //eventually support more than just metadata
    @JsonIgnore
    public boolean forEach=false;

    public ExtractorDao setName(String name){
        this.name = name;
        return this;
    }

    public static ExtractorDao fromString(String input){
        ExtractorDao rtrn = new ExtractorDao();
        if(input.startsWith(PREFIX) || input.startsWith(FOR_EACH_SUFFIX+NAME_SEPARATOR)){
            rtrn.type = Type.PATH;
            if(input!=null && !input.isBlank()){
                rtrn.name = generateName();
                //I think starting with a [] is not what we want.
                // we want to be able to mark the label_value as iterated not iterate applying the extractor
                if(input.startsWith(FOR_EACH_SUFFIX+NAME_SEPARATOR)){
                    rtrn.forEach=true;
                    input = input.substring(FOR_EACH_SUFFIX.length()+NAME_SEPARATOR.length());
                }
                rtrn.jsonpath=input;
            }
        }else if( input.startsWith(METADATA_PREFIX)){
            rtrn.type = Type.METADATA;
            if(input!=null && !input.isBlank() && input.startsWith(METADATA_PREFIX) && input.contains(METADATA_SUFFIX)){
                String name = input.substring(METADATA_PREFIX.length(),input.indexOf(METADATA_SUFFIX));
                rtrn.name = generateName();
                rtrn.column_name = name;
                input = input.substring(input.indexOf(METADATA_SUFFIX)+METADATA_SUFFIX.length());
                if(input.startsWith(FOR_EACH_SUFFIX)){
                    rtrn.forEach = true;
                    input = input.substring(FOR_EACH_SUFFIX.length());
                }
                if(input.startsWith(NAME_SEPARATOR)){
                    input=input.substring(NAME_SEPARATOR.length());
                }
                if(input.startsWith(PREFIX)){
                    rtrn.jsonpath = input;
                }
            }
            return rtrn;
        }else{
            rtrn.type = Type.VALUE;
            if(input!=null && !input.isBlank()){
                rtrn.name = generateName();
                String name = input;
                if(input.contains(NAME_SEPARATOR)){
                    name = input.substring(0,input.indexOf(NAME_SEPARATOR));
                    rtrn.jsonpath = input.substring(input.indexOf(NAME_SEPARATOR)+NAME_SEPARATOR.length());
                }
                if(name.endsWith(FOR_EACH_SUFFIX)){
                    rtrn.forEach=true;
                    name = name.substring(0,name.length()-FOR_EACH_SUFFIX.length());
                }
                LabelDAO found = LabelDAO.find("name",name).firstResult();
//            Label found = null;
                if(found==null){
                    found = new LabelDAO();
                    found.name = name;
                    //not sure if persisting is necessary, does this assume the new LabelValueExtractor is persisted too?
                    //Label.persist(found);//need some association so that label.name is only unique to it's context (test or schema)
                }
                rtrn.targetLabel = found;//either a new entity or null
            }
        }
        return rtrn;
    }
    public static String generateName(){
        return "label_"+String.format("%03d",counter.getAndIncrement());
    }

    @PreUpdate
    @PrePersist
    public void enforceType(){
        switch (type){
            case PATH -> {
                column_name = null;
                targetLabel = null;
            }
            case VALUE -> {
                column_name = null;
            }
            case METADATA -> {
                targetLabel = null;
            }
        }
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        switch (type){
            case VALUE -> {
                if(targetLabel != null){
                    sb.append(targetLabel.name);
                }else{
                    sb.append("<no_target>");
                }
                if(forEach){
                    sb.append(FOR_EACH_SUFFIX);
                }
            }
            case METADATA -> {
                sb.append(METADATA_PREFIX);
                sb.append(column_name);
                sb.append(METADATA_SUFFIX);
            }
            //PATH is handled after switch
        }
        if(jsonpath!=null && !jsonpath.isBlank()){
            if(!sb.isEmpty()) {
                sb.append(NAME_SEPARATOR);
            }
            sb.append(jsonpath);
        }
        return sb.toString();
    }
}
