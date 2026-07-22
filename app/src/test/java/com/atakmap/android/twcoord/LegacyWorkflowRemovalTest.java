package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.coord.input.CoordinateParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class LegacyWorkflowRemovalTest {

  @Test
  public void retiredUiClassesAreAbsentButNeutralContractsRemain() {
    assertMissing("com.atakmap.android.twcoord.gotopage.TwCoordGotoReceiver");
    assertMissing("com.atakmap.android.twcoord.gotopage.TwCoordGotoIntents");
    assertMissing("com.atakmap.android.twcoord.address.ForwardSearchReceiver");
    assertMissing("com.atakmap.android.twcoord.address.ForwardSearchIntents");
    assertMissing("com.atakmap.android.twcoord.plugin.TwCoordGotoTool");
    assertMissing("com.atakmap.android.twcoord.plugin.ForwardSearchTool");

    assertThat(CoordinateParser.class.getPackage().getName())
        .isEqualTo("com.atakmap.android.twcoord.coord.input");
    assertThat(ResultOrdering.class.getPackage().getName())
        .isEqualTo("com.atakmap.android.twcoord.address.lookup");
  }

  @Test
  public void componentDoesNotRegisterStaleActions() throws Exception {
    String bytecode = classBytes(TwCoordMapComponent.class);

    assertThat(bytecode)
        .doesNotContain("com.atakmap.android.twcoord.SHOW_GOTO")
        .doesNotContain("com.atakmap.android.twcoord.SHOW_FORWARD_SEARCH");
  }

  private static void assertMissing(String className) {
    assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
  }

  private static String classBytes(Class<?> type) throws Exception {
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    try (InputStream stream = type.getResourceAsStream(resource)) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
    }
  }
}
