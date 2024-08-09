package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class ExtractorDAO {
    @NotNull
    public String name;

    @NotNull
    public String jsonpath;

    @NotNull
    @Column(name = "isarray")
    public boolean isArray;

    public ExtractorDAO() {
    }

    public ExtractorDAO(String name, String jsonpath, boolean isArray) {
        this.name = name;
        this.jsonpath = jsonpath;
        this.isArray = isArray;
    }

    @Override
    public String toString() {
        return "ExtractorDAO{" +
                "name='" + name + '\'' +
                ", jsonpath='" + jsonpath + '\'' +
                ", isarray=" + isArray +
                '}';
    }
}
