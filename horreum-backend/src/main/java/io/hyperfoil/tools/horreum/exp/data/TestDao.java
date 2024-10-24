package io.hyperfoil.tools.horreum.exp.data;

import java.util.*;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("test")
public class TestDao extends LabelGroupDao {

    public TestDao() {
    }

    public TestDao(String name) {
        super(name);
    }

    public TestDao setLabels(LabelDao... labels) {
        if (this.labels == null || this.labels.isEmpty()) {
            this.labels = new ArrayList<>(Arrays.asList(labels));
            checkLabels();
        }
        return this;
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof TestDao)) {
            return false;
        }
        TestDao t = (TestDao) o;
        return ((this.id == null || t.id == null) && this.name.equals(t.name)) || (this.id != null && this.id.equals(t.id));
    }
}
