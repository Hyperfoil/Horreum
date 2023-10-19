package io.hyperfoil.tools.horreum.entity.backend;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.type.SqlTypes;

@Entity(name = "backendconfig")
public class DatastoreConfigDAO extends PanacheEntityBase {
    @Id
    @GenericGenerator(
            name = "datastoreIdGenerator",
            strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
            parameters = {
                    @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "backend_id_seq"),
                    @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
            }
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "datastoreIdGenerator")
    @Column(name="id")
    public Integer id;

    @NotNull
    @Column(name="name")
    public String name;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public ObjectNode configuration;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTEGER)
    public DatastoreType type;

    @NotNull
    public String owner;


    @NotNull
    @JdbcTypeCode(SqlTypes.INTEGER)
    public io.hyperfoil.tools.horreum.api.data.Access access = Access.PUBLIC;

}
