package io.hyperfoil.tools.horreum.exp.data;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

@Entity
@Table(
        name = "exp_run"
)
public class RunDao extends PanacheEntity {

    @ManyToOne
    @JoinColumn(name="test_id")
    public TestDao test;
    @Type(JsonBinaryType.class)
    public JsonNode data;
    @Type(JsonBinaryType.class)
    public JsonNode metadata;

    public RunDao(){}

    public RunDao(long testId, JsonNode data, JsonNode metadata){
        this.test = TestDao.findById(testId);
        this.data = data;
        this.metadata = metadata;
    }
}
