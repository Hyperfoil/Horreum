package io.hyperfoil.tools.horreum.svc;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.RunService;
import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.server.CloseMe;
import io.hyperfoil.tools.horreum.server.RoleManager;
import io.hyperfoil.tools.horreum.test.NoGrafanaProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.quarkus.vertx.ConsumeEvent;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(NoGrafanaProfile.class)
public class RunServiceTest extends BaseServiceTest {

   @Inject
   EntityManager em;

   @Inject
   RoleManager roleManager;

   @Inject
   RunService runService;

   @Inject
   EventBus eventBus;

   @org.junit.jupiter.api.Test
   public void testDataSetCreated(TestInfo info) throws InterruptedException {
      BlockingQueue<DataSet> dataSetQueue = eventConsumerQueue(DataSet.class, DataSet.EVENT_NEW);
      Test test = createTest(createExampleTest(getTestName(info)));
      Schema schema = createExampleSchema(info);
      setTestVariables(test, "Value", "value");

      uploadRun(runWithValue(schema, 42).toString(), test.name);

      try (CloseMe ignored = roleManager.withRoles(em, Arrays.asList(TESTER_ROLES))) {
         DataSet event = dataSetQueue.poll(10, TimeUnit.SECONDS);
         assertNotNull(event);
         assertNotNull(event.id);
         Integer id = event.id;
         DataSet ds = DataSet.findById(id);
         assertNotNull(ds);
      }
   }

   private ObjectNode runWithValue(Schema schema, double value) {
      ObjectNode runJson = JsonNodeFactory.instance.objectNode();
      runJson.put("$schema", schema.uri);
      runJson.put("value", value);
      return runJson;
   }

   private <E> BlockingQueue<E> eventConsumerQueue(Class<? extends E> eventClass, String eventType) {
      BlockingQueue<E> dpe = new LinkedBlockingDeque<>();
      eventBus.consumer(eventType, msg -> {
         if (eventClass.isInstance(msg.body())) {
            dpe.add(eventClass.cast(msg.body()));
         }
      });
      return dpe;
   }


}
