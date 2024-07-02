package io.hyperfoil.tools.horreum.entity.backend;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.entity.CustomSequenceGenerator;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

@Entity(name = "backendconfig")
public class DatastoreConfigDAO extends PanacheEntityBase {
    @Id
    @CustomSequenceGenerator(
          name = "backend_id_seq",
          allocationSize = 1,
          initialValue = 10
    )
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
