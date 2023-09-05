package io.hyperfoil.tools.horreum.infra.common.resources;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class HorreumPostgreSQLContainer<SELF extends HorreumPostgreSQLContainer<SELF>> extends PostgreSQLContainer<SELF> {

    private final List<String> params = new ArrayList<>();
    private final Integer startMsgTimes;

    public HorreumPostgreSQLContainer(String dockerImageName, Integer startMsgTimes) {
        super(dockerImageName);
        this.startMsgTimes = startMsgTimes;
    }
    public HorreumPostgreSQLContainer(DockerImageName dockerImageName, Integer startMsgTimes) {
        super(dockerImageName);
        this.startMsgTimes = startMsgTimes;
    }

    public void withParameter(String param) {
        params.add(param);
    }
    protected void configure() {
        super.configure();
        if ( params.size() > 0 ) {
            StringBuilder sb = new StringBuilder();
            sb.append("postgres -c fsync=off");
            params.forEach(param -> sb.append(" -c ").append(param));
            this.setCommand(sb.toString());
        }
        super.waitStrategy =
                new LogMessageWaitStrategy()
                        .withRegEx(".*database system is ready to accept connections.*\\s")
                        .withTimes(startMsgTimes)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS));
    }
}
