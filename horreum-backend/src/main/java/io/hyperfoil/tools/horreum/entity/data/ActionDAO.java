package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.converter.ActionEventConverter;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.svc.ActionEvent;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "Action")
public class ActionDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "action_id_seq", sequenceName = "action_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "action_id_seq")
    public Integer id;

    @NotNull
    @Column(name = "event")
    @Convert(converter = ActionEventConverter.class)
    public ActionEvent event;

    /*
     * The type options were found in horreum-web/src/domain/actions/ActionComponentForm.tsx#L73
     * http, github-issue-comment, github-issue-create
     *
     */
    @NotNull
    @Column(name = "type")
    public String type;

    /*
     * Notes on where I found the config options
     * type=http: HttpActionUrlSelector.tsx : {url: string }
     * type=github-issue-comment: { issueUrl: string|undefined, owner: string, repo: string, issue: string, formatter: string }
     * type=github-issue-create: {owner: string, repo: string, title: string, formatter: string }
     */
    @NotNull
    @Column(name = "config", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public ObjectNode config;

    /*
     * horreum-web/src/domain/actions/ActionComponentForm.tsx#L278
     * { token: string, modified: boolean } are the possible values of secret but I don't think modified gets persisted
     */
    @NotNull
    @Column(name = "secrets", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public ObjectNode secrets;

    @NotNull
    @Column(name = "test_id")
    public Integer testId;

    @NotNull
    @Transient
    public boolean active = true;

    @NotNull
    @Column(name = "run_always")
    public boolean runAlways;

    public ActionDAO() {
    }

    public ActionDAO(Integer id, ActionEvent event, String type, ObjectNode config, ObjectNode secrets,
            Integer testId, boolean active, boolean runAlways) {
        this.id = id;
        this.event = event;
        this.type = type;
        this.config = config;
        this.secrets = secrets;
        this.testId = testId;
        this.active = active;
        this.runAlways = runAlways;
    }

}
