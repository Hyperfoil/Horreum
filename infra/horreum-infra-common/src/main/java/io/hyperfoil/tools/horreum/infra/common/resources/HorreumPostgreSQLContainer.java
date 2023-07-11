package io.hyperfoil.tools.horreum.infra.common.resources;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public class HorreumPostgreSQLContainer<SELF extends HorreumPostgreSQLContainer<SELF>> extends PostgreSQLContainer<SELF> {

    private final List<String> params = new ArrayList<>();

    public HorreumPostgreSQLContainer(String dockerImageName) {
        super(dockerImageName);
    }
    public HorreumPostgreSQLContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    public void withParameter(String param) {
        params.add(param);
    }
    protected void configure() {
        super.configure();
        StringBuilder sb = new StringBuilder();
        sb.append("postgres -c fsync=off");
        params.forEach( param -> sb.append(" -c ").append(param));

        this.setCommand(sb.toString());
    }
}
