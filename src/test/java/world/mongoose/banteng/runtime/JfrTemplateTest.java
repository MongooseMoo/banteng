package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import jdk.jfr.Configuration;
import org.junit.jupiter.api.Test;

final class JfrTemplateTest {
  @Test
  void productionTemplateLoadsThroughTheJdkConfigurationApi() throws Exception {
    Path template = resourcePath("/jfr/banteng-production.jfc");
    Configuration configuration = Configuration.create(template);

    assertEquals("Banteng Production", configuration.getLabel());
    for (String event :
        new String[] {
          "world.mongoose.banteng.TaskSegment",
          "world.mongoose.banteng.WorldCommit",
          "world.mongoose.banteng.WorldConflict",
          "world.mongoose.banteng.TaskRetry",
          "world.mongoose.banteng.TaskFallback",
          "world.mongoose.banteng.Checkpoint",
          "world.mongoose.banteng.VersionRetention"
        }) {
      assertEquals("true", configuration.getSettings().get(event + "#enabled"), event);
    }
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    var resource = JfrTemplateTest.class.getResource(name);
    assertNotNull(resource, name);
    return Path.of(resource.toURI());
  }
}
